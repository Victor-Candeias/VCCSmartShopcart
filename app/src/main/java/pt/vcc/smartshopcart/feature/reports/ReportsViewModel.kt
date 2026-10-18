package pt.vcc.smartshopcart.feature.reports

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.common.ZoneProvider
import pt.vcc.smartshopcart.domain.reports.GenerateReportUseCase
import pt.vcc.smartshopcart.domain.reports.ProductBreakdown
import pt.vcc.smartshopcart.domain.reports.ReportBreakdown
import pt.vcc.smartshopcart.domain.reports.ReportData
import pt.vcc.smartshopcart.domain.reports.ReportFilter
import pt.vcc.smartshopcart.domain.reports.ReportMode
import pt.vcc.smartshopcart.domain.reports.ReportPeriodPreset
import pt.vcc.smartshopcart.domain.reports.ReportSeries
import pt.vcc.smartshopcart.domain.reports.ReportTransaction
import pt.vcc.smartshopcart.domain.reports.ReportsRepository
import pt.vcc.smartshopcart.domain.reports.TimeGranularity
import pt.vcc.smartshopcart.domain.reports.TimelinePoint
import pt.vcc.smartshopcart.domain.reports.range
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Relatório pronto a mostrar, com as séries já calculadas para os gráficos e tabelas. */
data class ReportView(
    val report: ReportData,
    val granularity: TimeGranularity,
    val timeline: List<TimelinePoint>,
    val categories: List<ReportBreakdown>,
    val establishments: List<ReportBreakdown>,
    val products: List<ProductBreakdown>,
    val transactions: List<ReportTransaction>,
)

sealed interface ReportsUiState {
    data object Loading : ReportsUiState
    data class Ready(val view: ReportView) : ReportsUiState
    data class Error(val message: String) : ReportsUiState
}

/**
 * Estado dos relatórios, partilhado pelo ecrã principal e pelos detalhes. Os filtros vivem no
 * [SavedStateHandle]; o relatório é recalculado quando mudam os filtros ou os dados na base de dados.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val generateReport: GenerateReportUseCase,
    repository: ReportsRepository,
    private val time: TimeProvider,
    private val zones: ZoneProvider,
) : ViewModel() {

    private val retry = MutableStateFlow(0)

    private val storedFilter = combine(
        savedStateHandle.getStateFlow(KEY_PRESET, ReportPeriodPreset.THIS_MONTH.name),
        savedStateHandle.getStateFlow<String?>(KEY_START, null),
        savedStateHandle.getStateFlow<String?>(KEY_END, null),
        savedStateHandle.getStateFlow(KEY_MODES, ArrayList(ReportMode.entries.map { it.name })),
        combine(
            savedStateHandle.getStateFlow(KEY_CATEGORIES, ArrayList<String>()),
            savedStateHandle.getStateFlow(KEY_ESTABLISHMENTS, ArrayList<String>()),
            savedStateHandle.getStateFlow(KEY_PRODUCTS, ArrayList<String>()),
            savedStateHandle.getStateFlow<String?>(KEY_GRANULARITY, null),
        ) { categories, establishments, products, granularity -> Dimensions(categories, establishments, products, granularity) },
    ) { preset, start, end, modes, dimensions -> StoredFilter(preset, start, end, modes, dimensions) }

    /** Filtro efetivo (datas resolvidas), disponível antes de o relatório estar pronto. */
    val filter: StateFlow<ReportFilter?> = storedFilter
        .map { it.toFilter(today()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val state: StateFlow<ReportsUiState> = combine(storedFilter, repository.observeChanges(), retry) { stored, _, _ -> stored }
        .mapLatest { stored ->
            runCatching { buildView(stored) }
                .fold(
                    onSuccess = { ReportsUiState.Ready(it) },
                    onFailure = { ReportsUiState.Error(it.message ?: it.javaClass.simpleName) },
                )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ReportsUiState.Loading)

    fun today(): LocalDate = Instant.ofEpochMilli(time.now()).atZone(zones.zone()).toLocalDate()

    fun selectPreset(preset: ReportPeriodPreset) {
        savedStateHandle[KEY_PRESET] = preset.name
    }

    fun selectCustomRange(start: LocalDate, endInclusive: LocalDate) {
        val (from, to) = if (endInclusive.isBefore(start)) endInclusive to start else start to endInclusive
        savedStateHandle[KEY_START] = from.toString()
        savedStateHandle[KEY_END] = to.toString()
        savedStateHandle[KEY_PRESET] = ReportPeriodPreset.CUSTOM.name
    }

    /** `null` = todos os modos (visão global). */
    fun selectMode(mode: ReportMode?) {
        savedStateHandle[KEY_MODES] = ArrayList((mode?.let(::listOf) ?: ReportMode.entries).map { it.name })
    }

    fun toggleCategory(id: String) = toggle(KEY_CATEGORIES, id)

    fun toggleEstablishment(id: String) = toggle(KEY_ESTABLISHMENTS, id)

    fun toggleProduct(id: String) = toggle(KEY_PRODUCTS, id)

    fun clearDimensionFilters() {
        savedStateHandle[KEY_CATEGORIES] = ArrayList<String>()
        savedStateHandle[KEY_ESTABLISHMENTS] = ArrayList<String>()
        savedStateHandle[KEY_PRODUCTS] = ArrayList<String>()
    }

    fun selectGranularity(granularity: TimeGranularity) {
        savedStateHandle[KEY_GRANULARITY] = granularity.name
    }

    fun retry() {
        retry.value++
    }

    private fun toggle(key: String, id: String) {
        val current = savedStateHandle.get<ArrayList<String>>(key).orEmpty()
        savedStateHandle[key] = ArrayList(if (id in current) current - id else current + id)
    }

    private suspend fun buildView(stored: StoredFilter): ReportView {
        val filter = stored.toFilter(today())
        val report = generateReport(filter)
        val granularity = stored.dimensions.granularity?.let(TimeGranularity::valueOf)
            ?: ReportSeries.defaultGranularity(filter.startDate, filter.endDateInclusive)
        return ReportView(
            report = report,
            granularity = granularity,
            timeline = ReportSeries.timeline(report.rows, filter.startDate, filter.endDateInclusive, report.zoneId, granularity),
            categories = ReportSeries.byCategory(report.rows),
            establishments = ReportSeries.byEstablishment(report.rows),
            products = ReportSeries.byProduct(report.rows),
            transactions = ReportSeries.transactions(report.rows),
        )
    }

    private data class Dimensions(
        val categories: List<String>,
        val establishments: List<String>,
        val products: List<String>,
        val granularity: String?,
    )

    private data class StoredFilter(
        val preset: String,
        val start: String?,
        val end: String?,
        val modes: List<String>,
        val dimensions: Dimensions,
    ) {
        fun toFilter(today: LocalDate): ReportFilter {
            val period = ReportPeriodPreset.valueOf(preset)
            val (from, to) = if (period == ReportPeriodPreset.CUSTOM && start != null && end != null) {
                LocalDate.parse(start) to LocalDate.parse(end)
            } else {
                period.range(today)
            }
            return ReportFilter(
                startDate = from,
                endDateInclusive = to,
                modes = modes.map(ReportMode::valueOf).toSet(),
                categoryIds = dimensions.categories.toSet(),
                establishmentIds = dimensions.establishments.toSet(),
                productIds = dimensions.products.toSet(),
                preset = if (period == ReportPeriodPreset.CUSTOM && (start == null || end == null)) ReportPeriodPreset.TODAY else period,
            )
        }
    }

    private companion object {
        const val KEY_PRESET = "preset"
        const val KEY_START = "start"
        const val KEY_END = "end"
        const val KEY_MODES = "modes"
        const val KEY_CATEGORIES = "categories"
        const val KEY_ESTABLISHMENTS = "establishments"
        const val KEY_PRODUCTS = "products"
        const val KEY_GRANULARITY = "granularity"
    }
}
