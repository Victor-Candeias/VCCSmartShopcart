package pt.vcc.smartshopcart.domain.reports

import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.common.ZoneProvider
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/**
 * Gera o relatório: carrega as linhas do período/modos, calcula as opções de filtro, aplica os
 * filtros de dimensão e produz KPIs, orçamento vs. realizado, avisos e comparação com o período anterior.
 * Transações contam-se por `(modo, id)` para não colidirem entre tabelas.
 */
class GenerateReportUseCase @Inject constructor(
    private val repository: ReportsRepository,
    private val time: TimeProvider,
    private val zones: ZoneProvider,
) {
    suspend operator fun invoke(filter: ReportFilter, compareWithPrevious: Boolean = true): ReportData {
        val zone = zones.zone()
        val now = Instant.ofEpochMilli(time.now())
        val today = now.atZone(zone).toLocalDate()

        val periodRows = repository.loadRows(filter, zone).filter { it.mode in filter.modes }
        val rows = periodRows.filter(filter::matches)
        val budgets = loadBudgets(filter, zone)
        val comparison = if (compareWithPrevious) compare(filter, rows, today, zone) else null

        return ReportData(
            filter = filter,
            rows = rows,
            summary = summarize(rows),
            generatedAt = now,
            zoneId = zone,
            budgets = budgets,
            dataQuality = repository.loadDataQuality(filter, zone),
            comparison = comparison,
            options = options(periodRows),
        )
    }

    private suspend fun loadBudgets(filter: ReportFilter, zone: java.time.ZoneId): List<ReportBudgetRow> {
        // Orçamentos referem-se à compra inteira: não fazem sentido filtrados por categoria/produto.
        if (filter.categoryIds.isNotEmpty() || filter.productIds.isNotEmpty()) return emptyList()
        return repository.loadBudgets(filter, zone).filter { budget ->
            budget.mode in filter.modes &&
                (filter.establishmentIds.isEmpty() ||
                    (budget.establishmentId ?: ReportFilter.NO_ESTABLISHMENT_ID) in filter.establishmentIds)
        }
    }

    private suspend fun compare(
        filter: ReportFilter,
        rows: List<ReportRow>,
        today: LocalDate,
        zone: java.time.ZoneId,
    ): PeriodComparison {
        val (start, end) = filter.previousPeriod()
        val previousFilter = filter.copy(startDate = start, endDateInclusive = end, preset = ReportPeriodPreset.CUSTOM)
        val previousTotal = repository.loadRows(previousFilter, zone)
            .filter(previousFilter::matches)
            .sumOf { it.totalCents }
        return PeriodComparison(
            previousStartDate = start,
            previousEndDateInclusive = end,
            previousTotalCents = previousTotal,
            currentTotalCents = rows.sumOf { it.totalCents },
            currentPeriodIncomplete = !filter.endDateInclusive.isBefore(today),
        )
    }

    companion object {
        const val MAX_PRODUCT_OPTIONS = 50

        fun summarize(rows: List<ReportRow>): ReportSummary {
            val total = rows.sumOf { it.totalCents }
            val perTransaction = rows.groupBy { it.mode to it.transactionId }.mapValues { (_, items) -> items.sumOf { it.totalCents } }
            val count = perTransaction.size
            val average = if (count == 0) 0L else BigDecimal.valueOf(total)
                .divide(BigDecimal.valueOf(count.toLong()), 0, RoundingMode.HALF_UP)
                .toLong()
            return ReportSummary(
                totalCents = total,
                transactionCount = count,
                averageTransactionCents = average,
                itemCount = rows.size,
                largestTransactionCents = perTransaction.values.maxOrNull() ?: 0L,
            )
        }

        fun options(rows: List<ReportRow>): ReportFilterOptions = ReportFilterOptions(
            categories = ReportSeries.byCategory(rows).map { ReportOption(it.id, it.label, it.amountCents) },
            establishments = ReportSeries.byEstablishment(rows).map { ReportOption(it.id, it.label, it.amountCents) },
            products = ReportSeries.byProduct(rows).take(MAX_PRODUCT_OPTIONS).map { ReportOption(it.id, it.label, it.amountCents) },
        )
    }
}
