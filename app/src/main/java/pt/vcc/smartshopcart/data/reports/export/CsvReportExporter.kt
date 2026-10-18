package pt.vcc.smartshopcart.data.reports.export

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import pt.vcc.smartshopcart.domain.reports.ReportData
import pt.vcc.smartshopcart.domain.reports.ReportLabels
import pt.vcc.smartshopcart.domain.reports.ReportSeries
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * CSV para Excel/LibreOffice em Portugal: UTF-8 com BOM, `;`, decimal `,`, datas no fuso do
 * relatório. Texto é sempre entre aspas e protegido contra fórmulas; números ficam tipados.
 */
class CsvReportExporter @Inject constructor() : ReportExporter {
    override val format = ExportFormat.CSV

    override suspend fun write(report: ReportData, output: OutputStream, options: ExportOptions) =
        withContext(Dispatchers.IO) {
            output.write(BOM)
            val writer = OutputStreamWriter(output, Charsets.UTF_8).buffered()
            fun line(cells: List<String>) {
                writer.write(cells.joinToString(SEPARATOR))
                writer.write(EOL)
            }
            val date = DateTimeFormatter.ISO_LOCAL_DATE.withZone(report.zoneId)
            val time = DateTimeFormatter.ofPattern("HH:mm").withZone(report.zoneId)
            fun Instant.dateCells() = listOf(date.format(this), time.format(this))

            when (options.granularity) {
                ExportGranularity.ITEMS -> {
                    line(ITEM_HEADER.map(::csvText))
                    report.rows.forEachIndexed { index, r ->
                        if (index % 500 == 0) ensureActive()
                        line(
                            r.occurredAt.dateCells() + listOf(
                                csvText(ReportLabels.mode(r.mode)),
                                csvText(r.establishmentName ?: ReportLabels.NO_ESTABLISHMENT),
                                csvText(r.categoryName ?: ReportLabels.NO_CATEGORY),
                                csvText(r.productName),
                                r.quantity.stripTrailingZeros().toPlainString().replace('.', ','),
                                csvText(r.unit),
                                centsPt(r.unitPriceCents),
                                centsPt(r.discountCents),
                                centsPt(r.totalCents),
                                csvText(r.transactionId),
                            ),
                        )
                    }
                }
                ExportGranularity.TRANSACTIONS -> {
                    line(TRANSACTION_HEADER.map(::csvText))
                    ReportSeries.transactions(report.rows).sortedBy { it.occurredAt }.forEach { t ->
                        ensureActive()
                        line(
                            t.occurredAt.dateCells() + listOf(
                                csvText(ReportLabels.mode(t.mode)),
                                csvText(t.establishmentName ?: ReportLabels.NO_ESTABLISHMENT),
                                t.itemCount.toString(),
                                centsPt(t.totalCents),
                                csvText(t.transactionId),
                            ),
                        )
                    }
                }
            }
            writer.flush()
        }

    companion object {
        const val SEPARATOR = ";"
        const val EOL = "\r\n"
        private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

        val ITEM_HEADER = listOf(
            "Data", "Hora", "Modo", "Estabelecimento", "Categoria", "Produto", "Quantidade", "Unidade",
            "Preço unitário (EUR)", "Desconto (EUR)", "Total (EUR)", "ID transação",
        )
        val TRANSACTION_HEADER = listOf("Data", "Hora", "Modo", "Estabelecimento", "Artigos", "Total (EUR)", "ID transação")
    }
}

/** Célula de texto: entre aspas, aspas duplicadas e prefixo `'` quando poderia ser lida como fórmula. */
fun csvText(raw: String): String {
    val safe = if (raw.trimStart().firstOrNull() in FORMULA_PREFIXES || raw.firstOrNull() in CONTROL_PREFIXES) "'$raw" else raw
    return "\"" + safe.replace("\"", "\"\"") + "\""
}

private val FORMULA_PREFIXES = setOf('=', '+', '-', '@')
private val CONTROL_PREFIXES = setOf('\t', '\r')
