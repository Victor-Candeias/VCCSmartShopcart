package pt.vcc.smartshopcart.data.reports.export.pdf

import pt.vcc.smartshopcart.data.reports.export.centsPt
import pt.vcc.smartshopcart.domain.reports.ReportData
import pt.vcc.smartshopcart.domain.reports.ReportLabels
import pt.vcc.smartshopcart.domain.reports.ReportMode
import pt.vcc.smartshopcart.domain.reports.ReportOption
import pt.vcc.smartshopcart.domain.reports.ReportSeries
import pt.vcc.smartshopcart.domain.reports.TimeGranularity
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Converte o `ReportData` (as mesmas séries do dashboard) no conteúdo paginável do PDF. */
object PdfReportContentBuilder {
    const val APP_NAME = "VCCSmartShopcart"
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")
    private val DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
    private val MONTH = DateTimeFormatter.ofPattern("MM/yyyy")
    private const val MAX_CATEGORIES = 8

    fun money(cents: Long) = "${centsPt(cents)} €"

    fun build(report: ReportData): PdfReportContent {
        val filter = report.filter
        val period = "${DATE.format(filter.startDate)} a ${DATE.format(filter.endDateInclusive)}"
        val generated = DATE_TIME.format(report.generatedAt.atZone(report.zoneId))
        val summary = report.summary
        val granularity = ReportSeries.defaultGranularity(filter.startDate, filter.endDateInclusive)
        val timeline = ReportSeries.timeline(report.rows, filter.startDate, filter.endDateInclusive, report.zoneId, granularity)
        val pointLabel = when (granularity) {
            TimeGranularity.MONTH -> MONTH
            else -> DATE
        }
        return PdfReportContent(
            title = "$APP_NAME — Relatório de compras e despesas",
            runningHeader = "$APP_NAME · Relatório $period",
            footerNote = "Gerado no dispositivo em $generated (${report.zoneId.id}). Valores em EUR.",
            subtitleLines = listOf("Período: $period", "Filtros: ${filtersDescription(report)}"),
            kpis = listOf(
                PdfKpi("Total gasto", money(summary.totalCents)),
                PdfKpi("Compras e despesas", summary.transactionCount.toString()),
                PdfKpi("Média por compra", money(summary.averageTransactionCents)),
                PdfKpi("Artigos", summary.itemCount.toString()),
                PdfKpi("Maior compra", money(summary.largestTransactionCents)),
            ),
            notes = listOfNotNull(comparisonNote(report)),
            warnings = buildList {
                if (report.dataQuality.transactionsWithoutItems > 0) {
                    add("Aviso: ${report.dataQuality.transactionsWithoutItems} compra(s) sem artigos não entram nos totais.")
                }
                if (report.dataQuality.totalMismatches > 0) {
                    add("Aviso: ${report.dataQuality.totalMismatches} compra(s) com total diferente da soma dos artigos.")
                }
            },
            categoriesTitle = "Gastos por categoria",
            categories = ReportSeries.collapse(ReportSeries.byCategory(report.rows), MAX_CATEGORIES).map {
                PdfBar(it.label, it.amountCents, "${money(it.amountCents)} (${percent(it.share * 100)})")
            },
            timelineTitle = "Evolução dos gastos (${granularityLabel(granularity)})",
            timeline = timeline.map { PdfBar(pointLabel.format(it.start), it.amountCents, money(it.amountCents)) },
            tableTitle = "Movimentos (${report.rows.size})",
            table = PdfTable(
                columns = listOf(
                    PdfColumn("Data", 1.25f),
                    PdfColumn("Estabelecimento", 1.5f),
                    PdfColumn("Categoria", 1.2f),
                    PdfColumn("Produto", 2.1f),
                    PdfColumn("Qtd.", 0.65f, PdfAlign.END),
                    PdfColumn("Total (€)", 0.9f, PdfAlign.END),
                ),
                rows = report.rows.sortedWith(compareBy({ it.occurredAt }, { it.transactionId }, { it.itemId })).map { r ->
                    listOf(
                        DATE_TIME.format(r.occurredAt.atZone(report.zoneId)),
                        r.establishmentName ?: ReportLabels.NO_ESTABLISHMENT,
                        r.categoryName ?: ReportLabels.NO_CATEGORY,
                        r.productName,
                        r.quantity.stripTrailingZeros().toPlainString().replace('.', ',') + " " + r.unit,
                        centsPt(r.totalCents),
                    )
                },
                emptyMessage = "Sem movimentos no período e filtros escolhidos.",
            ),
            emptyChartMessage = "Sem dados para mostrar.",
        )
    }

    private fun filtersDescription(report: ReportData): String {
        val filter = report.filter
        val parts = mutableListOf<String>()
        parts += if (filter.modes.size == ReportMode.entries.size) "todos os modos" else filter.modes.sorted().joinToString(", ") { ReportLabels.mode(it) }
        fun labels(ids: Set<String>, options: List<ReportOption>, name: String) {
            if (ids.isEmpty()) return
            val byId = options.associateBy { it.id }
            parts += "$name: " + ids.map { byId[it]?.label ?: it }.sorted().joinToString(", ")
        }
        labels(filter.categoryIds, report.options.categories, "categorias")
        labels(filter.establishmentIds, report.options.establishments, "estabelecimentos")
        labels(filter.productIds, report.options.products, "produtos")
        return parts.joinToString("; ")
    }

    private fun comparisonNote(report: ReportData): String? {
        val comparison = report.comparison ?: return null
        val previous = "${DATE.format(comparison.previousStartDate)} a ${DATE.format(comparison.previousEndDateInclusive)}"
        val sign = if (comparison.differenceCents > 0) "+" else ""
        val change = comparison.differencePercent?.let { " (${if (it > 0) "+" else ""}${percent(it)})" }.orEmpty()
        val incomplete = if (comparison.currentPeriodIncomplete) " O período atual ainda não terminou." else ""
        return "Período anterior ($previous): ${money(comparison.previousTotalCents)}; diferença $sign${money(comparison.differenceCents)}$change.$incomplete"
    }

    private fun percent(value: Double): String =
        if (abs(value) >= 10 || value == 0.0) "${Math.round(value)}%" else String.format(Locale.forLanguageTag("pt-PT"), "%.1f%%", value)

    private fun granularityLabel(granularity: TimeGranularity) = when (granularity) {
        TimeGranularity.DAY -> "por dia"
        TimeGranularity.WEEK -> "por semana"
        TimeGranularity.MONTH -> "por mês"
    }
}
