package pt.vcc.smartshopcart.feature.reports

import androidx.annotation.StringRes
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.domain.reports.PeriodComparison
import pt.vcc.smartshopcart.domain.reports.ReportMode
import pt.vcc.smartshopcart.domain.reports.ReportPeriodPreset
import pt.vcc.smartshopcart.domain.reports.TimeGranularity
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ecrãs de detalhe acessíveis a partir do resumo de relatórios. */
enum class ReportDetailKind(@StringRes val title: Int) {
    TIMELINE(R.string.reports_section_timeline),
    CATEGORIES(R.string.reports_section_categories),
    ESTABLISHMENTS(R.string.reports_section_establishments),
    PRODUCTS(R.string.reports_section_products),
    BUDGET(R.string.reports_section_budget),
    TRANSACTIONS(R.string.reports_section_transactions),
}

internal object ReportsText {
    private val locale: Locale = Locale.forLanguageTag("pt-PT")
    private val shortDate = DateTimeFormatter.ofPattern("d MMM", locale)
    private val fullDate = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val month = DateTimeFormatter.ofPattern("MMM yyyy", locale)

    @StringRes
    fun preset(preset: ReportPeriodPreset): Int = when (preset) {
        ReportPeriodPreset.TODAY -> R.string.reports_period_today
        ReportPeriodPreset.LAST_7_DAYS -> R.string.reports_period_last_7_days
        ReportPeriodPreset.THIS_MONTH -> R.string.reports_period_this_month
        ReportPeriodPreset.PREVIOUS_MONTH -> R.string.reports_period_previous_month
        ReportPeriodPreset.THIS_YEAR -> R.string.reports_period_this_year
        ReportPeriodPreset.CUSTOM -> R.string.reports_period_custom
    }

    @StringRes
    fun mode(mode: ReportMode?): Int = when (mode) {
        null -> R.string.reports_mode_all
        ReportMode.CALCULATOR -> R.string.reports_mode_calculator
        ReportMode.PLANNED_LIST -> R.string.reports_mode_planned
        ReportMode.EXPENSE -> R.string.reports_mode_expense
    }

    @StringRes
    fun granularity(granularity: TimeGranularity): Int = when (granularity) {
        TimeGranularity.DAY -> R.string.reports_granularity_day
        TimeGranularity.WEEK -> R.string.reports_granularity_week
        TimeGranularity.MONTH -> R.string.reports_granularity_month
    }

    fun date(value: LocalDate): String = fullDate.format(value)

    fun range(start: LocalDate, endInclusive: LocalDate): String = when {
        start == endInclusive -> fullDate.format(start)
        start.year == endInclusive.year -> "${shortDate.format(start)} – ${fullDate.format(endInclusive)}"
        else -> "${fullDate.format(start)} – ${fullDate.format(endInclusive)}"
    }

    /** Etiqueta curta de um ponto da linha temporal (eixo do gráfico). */
    fun bucket(start: LocalDate, granularity: TimeGranularity): String = when (granularity) {
        TimeGranularity.DAY, TimeGranularity.WEEK -> shortDate.format(start)
        TimeGranularity.MONTH -> month.format(start)
    }

    fun percent(fraction: Double): String = NumberFormat.getPercentInstance(locale).apply {
        maximumFractionDigits = if (fraction != 0.0 && kotlin.math.abs(fraction) < 0.1) 1 else 0
    }.format(fraction)

    fun signedPercent(comparison: PeriodComparison): String? = comparison.differencePercent?.let {
        (if (it > 0) "+" else "") + percent(it / 100)
    }
}
