package pt.vcc.smartshopcart.data.reports.export

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import pt.vcc.smartshopcart.domain.reports.ReportData
import java.io.ByteArrayOutputStream

class CsvReportExporterTest {

    private val exporter = CsvReportExporter()

    private suspend fun export(report: ReportData, options: ExportOptions = ExportOptions()): ByteArray =
        ByteArrayOutputStream().also { exporter.write(report, it, options) }.toByteArray()

    private fun lines(bytes: ByteArray): List<String> =
        String(bytes, 3, bytes.size - 3, Charsets.UTF_8).split("\r\n").dropLast(1)

    @Test
    fun `utf8 com BOM separador ponto e virgula e decimal com virgula`() = runTest {
        val bytes = export(ReportFixtures.coffee())

        assertArrayEquals(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()), bytes.copyOf(3))
        val lines = lines(bytes)
        assertEquals(CsvReportExporter.ITEM_HEADER.joinToString(";") { "\"$it\"" }, lines[0])
        assertEquals(
            "2026-04-10;09:30;\"Despesa rápida\";\"Pastelaria Sé\";\"Café e pastelaria\";\"Café\";1;\"un\";0,85;0,00;0,85;\"abc\"",
            lines[1],
        )
        assertEquals(3, lines.size)
    }

    @Test
    fun `texto escapado e protegido contra formulas sem alterar numeros`() = runTest {
        val lines = lines(export(ReportFixtures.tricky()))

        assertEquals(
            "2026-04-02;18:05;\"Calculadora\";\"Loja; \"\"A\"\"\";\"Sem categoria\";\"'=HYPERLINK(\"\"x\"\")\";1;\"un\";1,99;0,00;1,99;\"t1\"",
            lines[1],
        )
        assertEquals(
            "2026-04-02;18:05;\"Calculadora\";\"Loja; \"\"A\"\"\";\"Fruta\";\"Maçãs\";1,25;\"kg\";1,43;0,20;1,23;\"t1\"",
            lines[2],
        )
        assertEquals(
            "2026-04-03;08:00;\"Lista planeada\";\"Sem estabelecimento\";\"Padaria\";\"'-Pão\";1;\"un\";0,45;0,00;0,45;\"t2\"",
            lines[3],
        )
    }

    @Test
    fun `granularidade por transacao agrega artigos`() = runTest {
        val lines = lines(export(ReportFixtures.tricky(), ExportOptions(ExportGranularity.TRANSACTIONS)))

        assertEquals(CsvReportExporter.TRANSACTION_HEADER.joinToString(";") { "\"$it\"" }, lines[0])
        assertEquals("2026-04-02;18:05;\"Calculadora\";\"Loja; \"\"A\"\"\";2;3,22;\"t1\"", lines[1])
        assertEquals("2026-04-03;08:00;\"Lista planeada\";\"Sem estabelecimento\";1;0,45;\"t2\"", lines[2])
    }

    @Test
    fun `relatorio vazio tem so cabecalho e nao fecha o stream`() = runTest {
        var closed = false
        val output = object : ByteArrayOutputStream() {
            override fun close() {
                closed = true
            }
        }
        exporter.write(ReportFixtures.report(emptyList()), output, ExportOptions())

        assertEquals(1, lines(output.toByteArray()).size)
        assertEquals(false, closed)
    }

    @Test
    fun `celulas de texto e valores negativos`() {
        assertEquals("\"'+351\"", csvText("+351"))
        assertEquals("\"'@soma\"", csvText("@soma"))
        assertEquals("\"texto normal\"", csvText("texto normal"))
        assertEquals("-0,05", centsPt(-5))
        assertEquals("1234,56", centsPt(123_456))
    }
}
