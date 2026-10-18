package pt.vcc.smartshopcart.domain.reports

import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Fatia de um agrupamento (categoria, estabelecimento, produto, modo). */
data class ReportBreakdown(
    val id: String,
    val label: String,
    val amountCents: Long,
    val transactionCount: Int,
    val itemCount: Int,
    /** Fração do total (0..1). */
    val share: Double,
)

data class ProductBreakdown(
    val id: String,
    val label: String,
    val amountCents: Long,
    /** Número de transações em que o produto aparece. */
    val frequency: Int,
    val quantity: BigDecimal,
    val unit: String,
)

enum class TimeGranularity { DAY, WEEK, MONTH }

/** Ponto da linha temporal; [start] é o primeiro dia do intervalo (dia, segunda-feira ou dia 1). */
data class TimelinePoint(val start: LocalDate, val amountCents: Long, val transactionCount: Int)

data class ReportTransaction(
    val transactionId: String,
    val mode: ReportMode,
    val occurredAt: Instant,
    val establishmentName: String?,
    val itemCount: Int,
    val totalCents: Long,
)

object ReportSeries {

    fun byCategory(rows: List<ReportRow>): List<ReportBreakdown> =
        breakdown(rows, { it.categoryKey }, { it.categoryName ?: ReportLabels.NO_CATEGORY })

    fun byEstablishment(rows: List<ReportRow>): List<ReportBreakdown> =
        breakdown(rows, { it.establishmentKey }, { it.establishmentName ?: ReportLabels.NO_ESTABLISHMENT })

    fun byMode(rows: List<ReportRow>): List<ReportBreakdown> =
        breakdown(rows, { it.mode.name }, { ReportLabels.mode(it.mode) })

    fun byProduct(rows: List<ReportRow>): List<ProductBreakdown> = rows
        .groupBy { it.productKey }
        .map { (key, items) ->
            val units = items.map { it.unit }.distinct()
            ProductBreakdown(
                id = key,
                label = items.first().productName,
                amountCents = items.sumOf { it.totalCents },
                frequency = items.map { it.mode to it.transactionId }.distinct().size,
                quantity = if (units.size == 1) items.fold(BigDecimal.ZERO) { acc, row -> acc + row.quantity } else BigDecimal.ZERO,
                unit = units.singleOrNull().orEmpty(),
            )
        }
        .sortedWith(compareByDescending<ProductBreakdown> { it.amountCents }.thenBy { it.label })

    /** Granularidade por omissão: dias até ~2 meses, semanas até ~6 meses, depois meses. */
    fun defaultGranularity(start: LocalDate, endInclusive: LocalDate): TimeGranularity {
        val days = ChronoUnit.DAYS.between(start, endInclusive) + 1
        return when {
            days <= 62 -> TimeGranularity.DAY
            days <= 182 -> TimeGranularity.WEEK
            else -> TimeGranularity.MONTH
        }
    }

    /** Linha temporal contínua: intervalos sem movimentos aparecem com zero. */
    fun timeline(
        rows: List<ReportRow>,
        start: LocalDate,
        endInclusive: LocalDate,
        zone: ZoneId,
        granularity: TimeGranularity = defaultGranularity(start, endInclusive),
    ): List<TimelinePoint> {
        fun bucket(date: LocalDate): LocalDate = when (granularity) {
            TimeGranularity.DAY -> date
            TimeGranularity.WEEK -> weekStart(date)
            TimeGranularity.MONTH -> date.withDayOfMonth(1)
        }
        val grouped = rows.groupBy { bucket(it.occurredAt.atZone(zone).toLocalDate()) }
        val points = mutableListOf<TimelinePoint>()
        var cursor = bucket(start)
        val last = bucket(endInclusive)
        while (!cursor.isAfter(last)) {
            val items = grouped[cursor].orEmpty()
            points += TimelinePoint(
                start = cursor,
                amountCents = items.sumOf { it.totalCents },
                transactionCount = items.map { it.mode to it.transactionId }.distinct().size,
            )
            cursor = when (granularity) {
                TimeGranularity.DAY -> cursor.plusDays(1)
                TimeGranularity.WEEK -> cursor.plusWeeks(1)
                TimeGranularity.MONTH -> cursor.plusMonths(1)
            }
        }
        return points
    }

    /** Mantém as [maxSlices] - 1 maiores fatias e agrega as restantes em "Outros" (gráfico circular). */
    fun collapse(items: List<ReportBreakdown>, maxSlices: Int = 6): List<ReportBreakdown> {
        if (items.size <= maxSlices) return items
        val kept = items.take(maxSlices - 1)
        val rest = items.drop(maxSlices - 1)
        return kept + ReportBreakdown(
            id = OTHERS_ID,
            label = ReportLabels.OTHERS,
            amountCents = rest.sumOf { it.amountCents },
            transactionCount = rest.sumOf { it.transactionCount },
            itemCount = rest.sumOf { it.itemCount },
            share = rest.sumOf { it.share },
        )
    }

    /** Transações (compras/despesas) com os totais dos artigos visíveis, mais recentes primeiro. */
    fun transactions(rows: List<ReportRow>): List<ReportTransaction> = rows
        .groupBy { it.mode to it.transactionId }
        .map { (key, items) ->
            ReportTransaction(
                transactionId = key.second,
                mode = key.first,
                occurredAt = items.first().occurredAt,
                establishmentName = items.first().establishmentName,
                itemCount = items.size,
                totalCents = items.sumOf { it.totalCents },
            )
        }
        .sortedWith(compareByDescending<ReportTransaction> { it.occurredAt }.thenBy { it.transactionId })

    private fun breakdown(
        rows: List<ReportRow>,
        key: (ReportRow) -> String,
        label: (ReportRow) -> String,
    ): List<ReportBreakdown> {
        val total = rows.sumOf { it.totalCents }
        return rows.groupBy(key)
            .map { (id, items) ->
                val amount = items.sumOf { it.totalCents }
                ReportBreakdown(
                    id = id,
                    label = label(items.first()),
                    amountCents = amount,
                    transactionCount = items.map { it.mode to it.transactionId }.distinct().size,
                    itemCount = items.size,
                    share = if (total == 0L) 0.0 else amount.toDouble() / total,
                )
            }
            .sortedWith(compareByDescending<ReportBreakdown> { it.amountCents }.thenBy { it.label })
    }

    const val OTHERS_ID = "others"
}
