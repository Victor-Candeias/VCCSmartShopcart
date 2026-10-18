package pt.vcc.smartshopcart.data.reports.export

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.smartshopcart.domain.reports.ReportData
import java.io.ByteArrayOutputStream

class JsonReportExporterTest {

    private val exporter = JsonReportExporter()

    private suspend fun export(report: ReportData, options: ExportOptions = ExportOptions()): String =
        ByteArrayOutputStream().also { exporter.write(report, it, options) }.toString(Charsets.UTF_8)

    @Test
    fun `cabecalho versionado com resumo e artigos do exemplo`() = runTest {
        val root = Json.parseToJsonElement(export(ReportFixtures.coffee())).jsonObject

        assertEquals(1, root["schemaVersion"]!!.jsonPrimitive.int)
        assertEquals("VCCSmartShopcart", root["application"]!!.jsonPrimitive.content)
        assertEquals("EUR", root["currency"]!!.jsonPrimitive.content)
        assertEquals("Europe/Lisbon", root["timeZone"]!!.jsonPrimitive.content)
        assertEquals("2026-04-15T11:00:00Z", root["generatedAt"]!!.jsonPrimitive.content)
        assertEquals(235L, root["summary"]!!.jsonObject["totalCents"]!!.jsonPrimitive.long)
        assertEquals(1, root["summary"]!!.jsonObject["transactionCount"]!!.jsonPrimitive.int)
        assertEquals("2026-04-01", root["period"]!!.jsonObject["start"]!!.jsonPrimitive.content)

        val items = root["items"]!!.jsonArray
        assertEquals(listOf("Café", "Bola de Berlim"), items.map { it.jsonObject["name"]!!.jsonPrimitive.content })
        val first = items[0].jsonObject
        assertTrue(first["quantity"]!!.jsonPrimitive.isString)
        assertEquals(85L, first["unitPriceCents"]!!.jsonPrimitive.long)
        assertEquals("2026-04-10T09:30+01:00", first["occurredAt"]!!.jsonPrimitive.content)
        assertFalse("transactions" in root)
    }

    @Test
    fun `quantidades exatas e granularidade por transacao`() = runTest {
        val items = Json.parseToJsonElement(export(ReportFixtures.tricky())).jsonObject["items"]!!.jsonArray
        assertEquals("1.25", items[1].jsonObject["quantity"]!!.jsonPrimitive.content)
        assertEquals("=HYPERLINK(\"x\")", items[0].jsonObject["name"]!!.jsonPrimitive.content)

        val root = Json.parseToJsonElement(export(ReportFixtures.tricky(), ExportOptions(ExportGranularity.TRANSACTIONS))).jsonObject
        assertFalse("items" in root)
        val transactions = root["transactions"]!!.jsonArray
        assertEquals(listOf(322L, 45L), transactions.map { it.jsonObject["totalCents"]!!.jsonPrimitive.long })
        assertEquals("TRANSACTIONS", root["granularity"]!!.jsonPrimitive.content)
    }

    @Test
    fun `json e lido de volta para os DTOs`() = runTest {
        val dto = JsonReportExporter.json.decodeFromString(ReportExportDto.serializer(), export(ReportFixtures.coffee()))
        assertEquals(ReportExportDto.from(ReportFixtures.coffee(), ExportOptions()), dto)
    }
}
