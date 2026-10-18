package pt.vcc.smartshopcart.domain.reports

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Origem de um movimento real. Listas planeadas só contam depois de concluídas (`PLANNED_LIST`). */
enum class ReportMode { CALCULATOR, PLANNED_LIST, EXPENSE }

/** Atalhos de período; `CUSTOM` usa as datas indicadas. */
enum class ReportPeriodPreset { TODAY, LAST_7_DAYS, THIS_MONTH, PREVIOUS_MONTH, THIS_YEAR, CUSTOM }

/**
 * Filtro de relatório. As datas são locais e inclusivas; os filtros de dimensão vazios significam
 * "todos". Usar [NO_CATEGORY_ID]/[NO_ESTABLISHMENT_ID] para filtrar movimentos sem essa dimensão.
 */
data class ReportFilter(
    val startDate: LocalDate,
    val endDateInclusive: LocalDate,
    val modes: Set<ReportMode> = ReportMode.entries.toSet(),
    val categoryIds: Set<String> = emptySet(),
    val establishmentIds: Set<String> = emptySet(),
    val productIds: Set<String> = emptySet(),
    val preset: ReportPeriodPreset = ReportPeriodPreset.CUSTOM,
) {
    init {
        require(!endDateInclusive.isBefore(startDate)) { "A data final não pode ser anterior à inicial" }
    }

    val hasDimensionFilters: Boolean
        get() = categoryIds.isNotEmpty() || establishmentIds.isNotEmpty() || productIds.isNotEmpty()

    fun withoutDimensionFilters() = copy(categoryIds = emptySet(), establishmentIds = emptySet(), productIds = emptySet())

    fun matches(row: ReportRow): Boolean =
        row.mode in modes &&
            (categoryIds.isEmpty() || row.categoryKey in categoryIds) &&
            (establishmentIds.isEmpty() || row.establishmentKey in establishmentIds) &&
            (productIds.isEmpty() || row.productKey in productIds)

    companion object {
        const val NO_CATEGORY_ID = "none:category"
        const val NO_ESTABLISHMENT_ID = "none:establishment"
    }
}

/** Intervalo epoch `[início, fim)` correspondente às datas locais do filtro, correto em mudanças de hora. */
fun ReportFilter.toEpochRange(zone: ZoneId): Pair<Long, Long> {
    val from = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    val until = endDateInclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return from to until
}

/** Uma linha por artigo de uma transação real (compra concluída ou despesa). */
data class ReportRow(
    val transactionId: String,
    val itemId: String,
    val occurredAt: Instant,
    val mode: ReportMode,
    val establishmentId: String?,
    val establishmentName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val productId: String?,
    val productName: String,
    val quantity: BigDecimal,
    val unit: String,
    val unitPriceCents: Long,
    val discountCents: Long,
    val totalCents: Long,
) {
    val categoryKey: String get() = categoryId ?: ReportFilter.NO_CATEGORY_ID
    val establishmentKey: String get() = establishmentId ?: ReportFilter.NO_ESTABLISHMENT_ID

    /** Produtos sem ID agrupam-se pelo nome normalizado. */
    val productKey: String get() = productId ?: "name:${productName.trim().lowercase()}"
}

data class ReportSummary(
    val totalCents: Long,
    val transactionCount: Int,
    val averageTransactionCents: Long,
    val itemCount: Int,
    val largestTransactionCents: Long = 0,
)

/** Compra concluída com orçamento: o realizado é o total de todos os seus artigos. */
data class ReportBudgetRow(
    val transactionId: String,
    val mode: ReportMode,
    val occurredAt: Instant,
    val label: String,
    val establishmentId: String?,
    val budgetCents: Long,
    val spentCents: Long,
) {
    val differenceCents: Long get() = budgetCents - spentCents
    val overBudget: Boolean get() = spentCents > budgetCents
}

/** Transações cujos totais podem não refletir os artigos (avisos no ecrã e nas exportações). */
data class ReportDataQuality(
    val transactionsWithoutItems: Int = 0,
    val totalMismatches: Int = 0,
) {
    val hasIssues: Boolean get() = transactionsWithoutItems > 0 || totalMismatches > 0
}

data class PeriodComparison(
    val previousStartDate: LocalDate,
    val previousEndDateInclusive: LocalDate,
    val previousTotalCents: Long,
    val currentTotalCents: Long,
    /** O período atual inclui hoje ou dias futuros e por isso ainda não terminou. */
    val currentPeriodIncomplete: Boolean,
) {
    val differenceCents: Long get() = currentTotalCents - previousTotalCents
    val differencePercent: Double?
        get() = if (previousTotalCents == 0L) null else differenceCents * 100.0 / previousTotalCents
}

/** Valor possível para os filtros de dimensão, com o total no período para ordenar. */
data class ReportOption(val id: String, val label: String, val amountCents: Long)

data class ReportFilterOptions(
    val categories: List<ReportOption> = emptyList(),
    val establishments: List<ReportOption> = emptyList(),
    val products: List<ReportOption> = emptyList(),
)

data class ReportData(
    val schemaVersion: Int = SCHEMA_VERSION,
    val filter: ReportFilter,
    val rows: List<ReportRow>,
    val summary: ReportSummary,
    val generatedAt: Instant,
    val zoneId: ZoneId,
    val budgets: List<ReportBudgetRow> = emptyList(),
    val dataQuality: ReportDataQuality = ReportDataQuality(),
    val comparison: PeriodComparison? = null,
    val options: ReportFilterOptions = ReportFilterOptions(),
) {
    val isEmpty: Boolean get() = rows.isEmpty()

    companion object {
        const val SCHEMA_VERSION = 1
    }
}

object ReportLabels {
    const val NO_CATEGORY = "Sem categoria"
    const val NO_ESTABLISHMENT = "Sem estabelecimento"
    const val UNNAMED_PRODUCT = "Artigo sem nome"
    const val OTHERS = "Outros"

    fun mode(mode: ReportMode) = when (mode) {
        ReportMode.CALCULATOR -> "Calculadora"
        ReportMode.PLANNED_LIST -> "Lista planeada"
        ReportMode.EXPENSE -> "Despesa rápida"
    }
}
