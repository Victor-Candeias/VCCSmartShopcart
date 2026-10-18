package pt.vcc.smartshopcart.data.reports.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToStream
import pt.vcc.smartshopcart.domain.reports.ReportData
import pt.vcc.smartshopcart.domain.reports.ReportSeries
import java.io.OutputStream
import javax.inject.Inject

/**
 * JSON versionado para interoperabilidade (e futuro restauro). Usa DTOs próprios: preços em
 * cêntimos inteiros, quantidades em texto, datas ISO-8601 e sem dados pessoais.
 */
class JsonReportExporter @Inject constructor() : ReportExporter {
    override val format = ExportFormat.JSON

    @OptIn(ExperimentalSerializationApi::class)
    override suspend fun write(report: ReportData, output: OutputStream, options: ExportOptions) =
        withContext(Dispatchers.IO) {
            json.encodeToStream(ReportExportDto.from(report, options), output)
            output.flush()
        }

    companion object {
        @OptIn(ExperimentalSerializationApi::class)
        val json = Json {
            prettyPrint = true
            explicitNulls = false
            encodeDefaults = true
        }
    }
}

@Serializable
data class ReportExportDto(
    val schemaVersion: Int,
    val application: String,
    val generatedAt: String,
    val timeZone: String,
    val currency: String,
    val granularity: String,
    val period: PeriodDto,
    val filters: FiltersDto,
    val summary: SummaryDto,
    val dataQuality: DataQualityDto,
    val items: List<ItemDto>? = null,
    val transactions: List<TransactionDto>? = null,
) {
    @Serializable
    data class PeriodDto(val start: String, val endInclusive: String, val preset: String)

    @Serializable
    data class FiltersDto(
        val modes: List<String>,
        val categoryIds: List<String>,
        val establishmentIds: List<String>,
        val productIds: List<String>,
    )

    @Serializable
    data class SummaryDto(
        val totalCents: Long,
        val transactionCount: Int,
        val averageTransactionCents: Long,
        val itemCount: Int,
        val largestTransactionCents: Long,
    )

    @Serializable
    data class DataQualityDto(val transactionsWithoutItems: Int, val totalMismatches: Int)

    @Serializable
    data class ItemDto(
        val transactionId: String,
        val itemId: String,
        val occurredAt: String,
        val mode: String,
        val establishmentId: String?,
        val establishment: String?,
        val categoryId: String?,
        val category: String?,
        val productId: String?,
        val name: String,
        val quantity: String,
        val unit: String,
        val unitPriceCents: Long,
        val discountCents: Long,
        val totalCents: Long,
    )

    @Serializable
    data class TransactionDto(
        val transactionId: String,
        val occurredAt: String,
        val mode: String,
        val establishment: String?,
        val itemCount: Int,
        val totalCents: Long,
    )

    companion object {
        const val APPLICATION = "VCCSmartShopcart"
        const val CURRENCY = "EUR"

        fun from(report: ReportData, options: ExportOptions): ReportExportDto {
            val filter = report.filter
            val zone = report.zoneId
            return ReportExportDto(
                schemaVersion = report.schemaVersion,
                application = APPLICATION,
                generatedAt = report.generatedAt.toString(),
                timeZone = zone.id,
                currency = CURRENCY,
                granularity = options.granularity.name,
                period = PeriodDto(filter.startDate.toString(), filter.endDateInclusive.toString(), filter.preset.name),
                filters = FiltersDto(
                    modes = filter.modes.map { it.name }.sorted(),
                    categoryIds = filter.categoryIds.sorted(),
                    establishmentIds = filter.establishmentIds.sorted(),
                    productIds = filter.productIds.sorted(),
                ),
                summary = with(report.summary) {
                    SummaryDto(totalCents, transactionCount, averageTransactionCents, itemCount, largestTransactionCents)
                },
                dataQuality = DataQualityDto(report.dataQuality.transactionsWithoutItems, report.dataQuality.totalMismatches),
                items = if (options.granularity != ExportGranularity.ITEMS) null else report.rows.map { r ->
                    ItemDto(
                        transactionId = r.transactionId,
                        itemId = r.itemId,
                        occurredAt = r.occurredAt.atZone(zone).toOffsetDateTime().toString(),
                        mode = r.mode.name,
                        establishmentId = r.establishmentId,
                        establishment = r.establishmentName,
                        categoryId = r.categoryId,
                        category = r.categoryName,
                        productId = r.productId,
                        name = r.productName,
                        quantity = r.quantity.stripTrailingZeros().toPlainString(),
                        unit = r.unit,
                        unitPriceCents = r.unitPriceCents,
                        discountCents = r.discountCents,
                        totalCents = r.totalCents,
                    )
                },
                transactions = if (options.granularity != ExportGranularity.TRANSACTIONS) {
                    null
                } else {
                    ReportSeries.transactions(report.rows).sortedBy { it.occurredAt }.map { t ->
                        TransactionDto(
                            transactionId = t.transactionId,
                            occurredAt = t.occurredAt.atZone(zone).toOffsetDateTime().toString(),
                            mode = t.mode.name,
                            establishment = t.establishmentName,
                            itemCount = t.itemCount,
                            totalCents = t.totalCents,
                        )
                    }
                },
            )
        }
    }
}
