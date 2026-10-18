package pt.vcc.smartshopcart.data.reports.export

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pt.vcc.smartshopcart.domain.reports.ReportData
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Escolhe o exportador pelo formato e trata do destino: documento escolhido pelo utilizador (SAF)
 * ou ficheiro temporário em `cache/exports` partilhado via [FileProvider]. Em erro ou cancelamento
 * o ficheiro parcial é apagado, para nunca ficar um ficheiro incompleto apresentado como sucesso.
 */
@Singleton
class ExportCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    exporters: Set<@JvmSuppressWildcards ReportExporter>,
) {
    private val byFormat: Map<ExportFormat, ReportExporter> = exporters.associateBy { it.format }

    /** Formatos com exportador registado, pela ordem de [ExportFormat]. */
    val availableFormats: List<ExportFormat> = ExportFormat.entries.filter { it in byFormat }

    private val exportDir: File get() = File(context.cacheDir, EXPORT_DIR)

    fun defaultFileName(report: ReportData, format: ExportFormat): String =
        "${FILE_PREFIX}_${report.filter.startDate}_${report.filter.endDateInclusive}.${format.extension}"

    suspend fun writeToUri(uri: Uri, report: ReportData, format: ExportFormat, options: ExportOptions) {
        val exporter = exporter(format)
        withContext(Dispatchers.IO) {
            try {
                val stream = context.contentResolver.openOutputStream(uri, "wt")
                    ?: throw IOException("Não foi possível abrir o destino.")
                stream.use { exporter.write(report, it, options) }
            } catch (failure: Throwable) {
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, uri) }
                throw failure
            }
        }
    }

    /** Escreve o relatório num ficheiro temporário (apagando exportações temporárias anteriores). */
    suspend fun writeShareFile(report: ReportData, format: ExportFormat, options: ExportOptions, fileName: String): File {
        val exporter = exporter(format)
        return withContext(Dispatchers.IO) {
            clearShareFiles()
            val dir = exportDir.apply { mkdirs() }
            val file = File(dir, sanitizeFileName(fileName, format))
            try {
                file.outputStream().buffered().use { exporter.write(report, it, options) }
                file
            } catch (failure: Throwable) {
                file.delete()
                throw failure
            }
        }
    }

    fun shareUri(file: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}$AUTHORITY_SUFFIX", file)

    fun clearShareFiles() {
        exportDir.listFiles()?.forEach { it.delete() }
    }

    /** Apaga ficheiros de partilha com mais de [maxAgeMillis] (24 h por omissão). */
    suspend fun clearStaleShareFiles(nowMillis: Long = System.currentTimeMillis(), maxAgeMillis: Long = STALE_AFTER_MILLIS) =
        withContext(Dispatchers.IO) {
            exportDir.listFiles()?.filter { nowMillis - it.lastModified() > maxAgeMillis }?.forEach { it.delete() }
            Unit
        }

    private fun exporter(format: ExportFormat): ReportExporter =
        byFormat[format] ?: throw UnsupportedOperationException("Formato ${format.name} ainda não disponível.")

    companion object {
        const val EXPORT_DIR = "exports"
        const val AUTHORITY_SUFFIX = ".fileprovider"
        const val FILE_PREFIX = "VCCSmartShopcart_Relatorio"
        const val STALE_AFTER_MILLIS = 24L * 60 * 60 * 1000
        private val INVALID_CHARS = Regex("""[\\/:*?"<>|\p{Cntrl}]""")

        /** Remove caracteres inválidos, limita o tamanho e garante a extensão do formato. */
        fun sanitizeFileName(name: String, format: ExportFormat): String {
            val extension = ".${format.extension}"
            val base = name.trim().removeSuffix(extension).replace(INVALID_CHARS, "_").trim('.', ' ').take(100)
            return (base.ifBlank { FILE_PREFIX }) + extension
        }
    }
}
