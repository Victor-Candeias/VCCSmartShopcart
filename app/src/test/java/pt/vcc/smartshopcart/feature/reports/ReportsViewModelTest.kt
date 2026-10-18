package pt.vcc.smartshopcart.feature.reports

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.onEach
import org.junit.Assert.assertFalse
import pt.vcc.smartshopcart.data.reports.export.ExportCoordinator
import pt.vcc.smartshopcart.data.reports.export.ExportFormat
import pt.vcc.smartshopcart.data.reports.export.ExportOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.smartshopcart.domain.reports.GenerateReportUseCase
import pt.vcc.smartshopcart.domain.reports.ReportBudgetRow
import pt.vcc.smartshopcart.domain.reports.ReportDataQuality
import pt.vcc.smartshopcart.domain.reports.ReportFilter
import pt.vcc.smartshopcart.domain.reports.ReportMode
import pt.vcc.smartshopcart.domain.reports.ReportPeriodPreset
import pt.vcc.smartshopcart.domain.reports.ReportRow
import pt.vcc.smartshopcart.domain.reports.ReportsRepository
import pt.vcc.smartshopcart.domain.reports.TimeGranularity
import pt.vcc.smartshopcart.domain.reports.toEpochRange
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = LocalDateTime.of(2026, 4, 15, 12, 0).atZone(zone).toInstant().toEpochMilli()
    private val repository = FakeReportsRepository()
    private val savedState = SavedStateHandle()
    private val exports = mockk<ExportCoordinator>(relaxed = true)
    private lateinit var viewModel: ReportsViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository.rows.value = listOf(
            row("e1", "2026-04-02", ReportMode.EXPENSE, "cat-cafe", "Café", 300),
            row("s1", "2026-04-10", ReportMode.CALCULATOR, "cat-mercearia", "Mercearia", 1_000),
            row("s2", "2026-03-10", ReportMode.PLANNED_LIST, "cat-mercearia", "Mercearia", 700),
        )
        viewModel = createViewModel()
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun createViewModel() = ReportsViewModel(
        savedState,
        GenerateReportUseCase(repository, { now }, { zone }),
        repository,
        { now },
        { zone },
        exports,
    )

    @Test
    fun `exportar para partilha emite o ficheiro e erros sao reportados`() = runTest(dispatcher) {
        viewModel.state.launchIn(backgroundScope)
        val events = mutableListOf<ExportEvent>()
        viewModel.exportEvents.onEach { events += it }.launchIn(backgroundScope)
        val file = java.io.File("relatorio.csv")
        val uri = mockk<Uri>()
        coEvery { exports.writeShareFile(any(), ExportFormat.CSV, any(), "relatorio") } returns file
        every { exports.shareUri(file) } returns uri

        viewModel.exportForShare(ExportFormat.CSV, ExportOptions(), "relatorio")

        assertEquals(listOf<ExportEvent>(ExportEvent.Share(uri, "text/csv")), events)
        coVerify { exports.writeShareFile(match { it.summary.totalCents == 1_300L }, ExportFormat.CSV, ExportOptions(), "relatorio") }

        coEvery { exports.writeToUri(uri, any(), ExportFormat.JSON, any()) } throws java.io.IOException("disco cheio")
        viewModel.exportToUri(uri, ExportFormat.JSON, ExportOptions())

        assertEquals(ExportEvent.Failed("disco cheio"), events.last())
        assertFalse(viewModel.exporting.value)
    }

    @Test
    fun `abre no mes atual com todos os modos`() = runTest(dispatcher) {
        viewModel.state.launchIn(backgroundScope)

        val view = ready()
        assertEquals(LocalDate.of(2026, 4, 1), view.report.filter.startDate)
        assertEquals(LocalDate.of(2026, 4, 15), view.report.filter.endDateInclusive)
        assertEquals(1_300L, view.report.summary.totalCents)
        assertEquals(700L, view.report.comparison?.previousTotalCents)
        assertEquals(listOf("Mercearia", "Café"), view.categories.map { it.label })
    }

    @Test
    fun `filtros de periodo modo e categoria recalculam o relatorio`() = runTest(dispatcher) {
        viewModel.state.launchIn(backgroundScope)
        viewModel.filter.launchIn(backgroundScope)

        viewModel.selectPreset(ReportPeriodPreset.THIS_YEAR)
        assertEquals(2_000L, ready().report.summary.totalCents)

        viewModel.selectMode(ReportMode.EXPENSE)
        assertEquals(300L, ready().report.summary.totalCents)

        viewModel.selectMode(null)
        viewModel.toggleCategory("cat-mercearia")
        assertEquals(1_700L, ready().report.summary.totalCents)
        assertEquals(setOf("cat-mercearia"), viewModel.filter.value?.categoryIds)

        viewModel.clearDimensionFilters()
        assertEquals(2_000L, ready().report.summary.totalCents)
    }

    @Test
    fun `periodo personalizado e granularidade sobrevivem a recriacao`() = runTest(dispatcher) {
        viewModel.selectCustomRange(LocalDate.of(2026, 4, 10), LocalDate.of(2026, 3, 1))
        viewModel.selectGranularity(TimeGranularity.WEEK)

        val recreated = createViewModel()
        recreated.state.launchIn(backgroundScope)

        val view = (recreated.state.value as ReportsUiState.Ready).view
        assertEquals(ReportPeriodPreset.CUSTOM, view.report.filter.preset)
        assertEquals(LocalDate.of(2026, 3, 1), view.report.filter.startDate)
        assertEquals(LocalDate.of(2026, 4, 10), view.report.filter.endDateInclusive)
        assertEquals(TimeGranularity.WEEK, view.granularity)
        assertEquals(2_000L, view.report.summary.totalCents)
    }

    @Test
    fun `erro e mostrado e tentar novamente recupera`() = runTest(dispatcher) {
        repository.fail = true
        viewModel.state.launchIn(backgroundScope)
        assertTrue(viewModel.state.value is ReportsUiState.Error)

        repository.fail = false
        viewModel.retry()
        assertEquals(1_300L, ready().report.summary.totalCents)
    }

    @Test
    fun `alteracoes nos dados atualizam o relatorio`() = runTest(dispatcher) {
        viewModel.state.launchIn(backgroundScope)
        assertEquals(1_300L, ready().report.summary.totalCents)

        repository.rows.value = repository.rows.value + row("e2", "2026-04-12", ReportMode.EXPENSE, null, null, 50)

        assertEquals(1_350L, ready().report.summary.totalCents)
    }

    private fun ready(): ReportView = (viewModel.state.value as ReportsUiState.Ready).view

    private fun row(id: String, date: String, mode: ReportMode, categoryId: String?, category: String?, cents: Long) = ReportRow(
        transactionId = id,
        itemId = "$id-1",
        occurredAt = LocalDate.parse(date).atTime(10, 0).atZone(zone).toInstant(),
        mode = mode,
        establishmentId = null,
        establishmentName = null,
        categoryId = categoryId,
        categoryName = category,
        productId = null,
        productName = "Artigo $id",
        quantity = BigDecimal.ONE,
        unit = "un",
        unitPriceCents = cents,
        discountCents = 0,
        totalCents = cents,
    )

    private class FakeReportsRepository : ReportsRepository {
        val rows = MutableStateFlow<List<ReportRow>>(emptyList())
        var fail = false

        override suspend fun loadRows(filter: ReportFilter, zone: ZoneId): List<ReportRow> {
            check(!fail) { "falha simulada" }
            val (from, until) = filter.toEpochRange(zone)
            return rows.value.filter { it.occurredAt.toEpochMilli() in from until until && it.mode in filter.modes }
        }

        override suspend fun loadBudgets(filter: ReportFilter, zone: ZoneId): List<ReportBudgetRow> = emptyList()

        override suspend fun loadDataQuality(filter: ReportFilter, zone: ZoneId) = ReportDataQuality(0, 0)

        override fun observeChanges(): Flow<Unit> = rows.map { }
    }
}
