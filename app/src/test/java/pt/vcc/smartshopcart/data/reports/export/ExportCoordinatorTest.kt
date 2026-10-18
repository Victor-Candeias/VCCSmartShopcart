package pt.vcc.smartshopcart.data.reports.export

import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.domain.reports.ReportData
import java.io.File
import java.io.IOException
import java.io.OutputStream

@RunWith(RobolectricTestRunner::class)
class ExportCoordinatorTest {

    private lateinit var context: Context
    private lateinit var coordinator: ExportCoordinator
    private val exportDir get() = File(context.cacheDir, ExportCoordinator.EXPORT_DIR)

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        coordinator = ExportCoordinator(context, setOf(JsonReportExporter(), CsvReportExporter()))
    }

    @Test
    fun `formatos disponiveis seguem a ordem do enum e os restantes falham`() = runTest {
        assertEquals(listOf(ExportFormat.CSV, ExportFormat.JSON), coordinator.availableFormats)
        try {
            coordinator.writeShareFile(ReportFixtures.coffee(), ExportFormat.XLSX, ExportOptions(), "x")
            fail("XLSX ainda não tem exportador")
        } catch (expected: UnsupportedOperationException) {
            assertTrue(expected.message!!.contains("XLSX"))
        }
    }

    @Test
    fun `ficheiro de partilha fica em cache exports e substitui o anterior`() = runTest {
        val first = coordinator.writeShareFile(ReportFixtures.coffee(), ExportFormat.CSV, ExportOptions(), "antigo")
        val file = coordinator.writeShareFile(ReportFixtures.coffee(), ExportFormat.JSON, ExportOptions(), "Relatório: abril?")

        assertEquals(exportDir, file.parentFile)
        assertEquals("Relatório_ abril_.json", file.name)
        assertFalse(first.exists())
        assertTrue(file.readText().contains("\"schemaVersion\": 1"))

        coordinator.clearShareFiles()
        assertFalse(file.exists())
    }

    @Test
    fun `erro ou cancelamento apagam o ficheiro parcial`() = runTest {
        listOf(IOException("disco cheio"), CancellationException("cancelado")).forEach { failure ->
            val broken = ExportCoordinator(context, setOf(FailingExporter(failure)))
            try {
                broken.writeShareFile(ReportFixtures.coffee(), ExportFormat.CSV, ExportOptions(), "parcial")
                fail("devia falhar")
            } catch (expected: Exception) {
                assertEquals(failure.message, expected.message)
            }
            assertFalse(File(exportDir, "parcial.csv").exists())
        }
    }

    @Test
    fun `guardar via uri escreve o conteudo completo`() = runTest {
        val target = File(context.filesDir, "destino.csv")
        coordinator.writeToUri(Uri.fromFile(target), ReportFixtures.coffee(), ExportFormat.CSV, ExportOptions())

        assertTrue(target.readText(Charsets.UTF_8).contains("\"Bola de Berlim\""))
    }

    @Test
    fun `nome por omissao e sanitizacao`() {
        assertEquals(
            "VCCSmartShopcart_Relatorio_2026-04-01_2026-04-15.pdf",
            coordinator.defaultFileName(ReportFixtures.coffee(), ExportFormat.PDF),
        )
        assertEquals("a_b.csv", ExportCoordinator.sanitizeFileName("a/b.csv", ExportFormat.CSV))
        assertEquals("VCCSmartShopcart_Relatorio.csv", ExportCoordinator.sanitizeFileName("  ", ExportFormat.CSV))
    }

    @Test
    fun `limpeza automatica apaga apenas ficheiros com mais de 24 horas`() = runTest {
        exportDir.mkdirs()
        val now = System.currentTimeMillis()
        val old = File(exportDir, "antigo.csv").apply { writeText("x"); setLastModified(now - ExportCoordinator.STALE_AFTER_MILLIS - 60_000) }
        val recent = File(exportDir, "recente.csv").apply { writeText("x"); setLastModified(now - 60_000) }

        coordinator.clearStaleShareFiles(now)

        assertFalse(old.exists())
        assertTrue(recent.exists())
    }

    private class FailingExporter(private val failure: Exception) : ReportExporter {
        override val format = ExportFormat.CSV

        override suspend fun write(report: ReportData, output: OutputStream, options: ExportOptions) {
            output.write("meio ficheiro".toByteArray())
            output.flush()
            throw failure
        }
    }
}
