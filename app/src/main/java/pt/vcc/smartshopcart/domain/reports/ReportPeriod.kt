package pt.vcc.smartshopcart.domain.reports

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** Datas (inclusivas) de um atalho de período relativamente a [today]. */
fun ReportPeriodPreset.range(today: LocalDate): Pair<LocalDate, LocalDate> = when (this) {
    ReportPeriodPreset.TODAY -> today to today
    ReportPeriodPreset.LAST_7_DAYS -> today.minusDays(6) to today
    ReportPeriodPreset.THIS_MONTH -> today.withDayOfMonth(1) to today
    ReportPeriodPreset.PREVIOUS_MONTH -> today.minusMonths(1).let {
        it.withDayOfMonth(1) to it.with(TemporalAdjusters.lastDayOfMonth())
    }
    ReportPeriodPreset.THIS_YEAR -> today.withDayOfYear(1) to today
    ReportPeriodPreset.CUSTOM -> today to today
}

/** Filtro com as datas do atalho, mantendo modos e filtros de dimensão. */
fun ReportFilter.withPreset(preset: ReportPeriodPreset, today: LocalDate): ReportFilter {
    val (start, end) = preset.range(today)
    return copy(startDate = start, endDateInclusive = end, preset = preset)
}

fun ReportFilter.Companion.forPreset(preset: ReportPeriodPreset, today: LocalDate): ReportFilter {
    val (start, end) = preset.range(today)
    return ReportFilter(startDate = start, endDateInclusive = end, preset = preset)
}

private fun LocalDate.isFirstDayOfMonth() = dayOfMonth == 1
private fun LocalDate.isLastDayOfMonth() = this == with(TemporalAdjusters.lastDayOfMonth())

/**
 * Período equivalente anterior, para comparação:
 * - meses completos → o mesmo número de meses imediatamente antes;
 * - "este mês"/"este ano" (parciais) → os mesmos dias do mês/ano anterior;
 * - restantes → a mesma duração imediatamente antes.
 */
fun ReportFilter.previousPeriod(): Pair<LocalDate, LocalDate> {
    val start = startDate
    val end = endDateInclusive
    return when {
        preset == ReportPeriodPreset.THIS_YEAR -> start.minusYears(1) to end.minusYears(1)
        preset == ReportPeriodPreset.THIS_MONTH -> start.minusMonths(1) to end.minusMonths(1)
        start.isFirstDayOfMonth() && end.isLastDayOfMonth() -> {
            val months = ChronoUnit.MONTHS.between(start, end.plusDays(1))
            val previousStart = start.minusMonths(months)
            previousStart to start.minusDays(1)
        }
        else -> {
            val days = ChronoUnit.DAYS.between(start, end) + 1
            start.minusDays(days) to start.minusDays(1)
        }
    }
}

/** Segunda-feira da semana de [date] (semanas ISO, como em Portugal). */
fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
