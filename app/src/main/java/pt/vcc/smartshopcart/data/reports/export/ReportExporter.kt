package pt.vcc.smartshopcart.data.reports.export

import pt.vcc.smartshopcart.domain.reports.ReportData
import java.io.OutputStream
import java.math.BigDecimal

/**
 * Escreve o mesmo [ReportData] num formato. Não fecha [output]: quem abre o destino é responsável
 * por o fechar (e por apagar o ficheiro parcial em caso de erro).
 */
interface ReportExporter {
    val format: ExportFormat

    suspend fun write(report: ReportData, output: OutputStream, options: ExportOptions = ExportOptions())
}

/** Cêntimos em texto pt-PT sem símbolo nem separador de milhares: `1234,56`, `-0,05`. */
fun centsPt(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString().replace('.', ',')
