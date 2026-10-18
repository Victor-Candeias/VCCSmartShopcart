package pt.vcc.smartshopcart.data.reports.export.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.smartshopcart.data.reports.export.ReportFixtures
import pt.vcc.smartshopcart.domain.reports.ReportRow
import java.time.LocalDateTime

class PdfLayoutEngineTest {

    /** Largura fixa por carácter: determinística e independente de fontes. */
    private val measurer = PdfTextMeasurer { text, style -> text.length * style.size * 0.5f }
    private val spec = PdfPageSpec.A4
    private val engine = PdfLayoutEngine(measurer, spec)

    private fun rows(count: Int, name: (Int) -> String = { "Produto %04d".format(it) }): List<ReportRow> =
        (0 until count).map { index ->
            ReportFixtures.row(
                transactionId = "t${index / 3}",
                at = LocalDateTime.of(2026, 4, 1 + index % 14, 8 + index % 10, index % 60),
                product = name(index),
                totalCents = 100L + index,
            )
        }

    private fun layout(rows: List<ReportRow>) = engine.layout(PdfReportContentBuilder.build(ReportFixtures.report(rows)))

    private fun List<PdfPage>.texts() = flatMap { page -> page.ops.filterIsInstance<PdfOp.Text>() }

    private fun PdfPage.isTableHeader(op: PdfOp.Text) = op.style == PdfTextStyle.BODY_BOLD && op.text == "Produto"

    private fun assertDecorated(pages: List<PdfPage>) {
        pages.forEach { page ->
            val texts = page.ops.filterIsInstance<PdfOp.Text>().map { it.text }
            assertTrue("rodapé da página ${page.number}", "Página ${page.number} de ${pages.size}" in texts)
            assertTrue(texts.any { it.startsWith("VCCSmartShopcart · Relatório 01/04/2026 a 15/04/2026") })
        }
    }

    private fun assertInsideMargins(pages: List<PdfPage>) {
        pages.forEach { page ->
            page.ops.filterIsInstance<PdfOp.Text>().forEach { op ->
                val width = measurer.width(op.text, op.style)
                assertTrue("x ${op.text}", op.x >= spec.margin - 0.01f)
                assertTrue("largura ${op.text}", op.x + width <= spec.width - spec.margin + 0.01f)
                assertTrue("baseline ${op.text}", op.baseline in spec.margin..(spec.height - spec.margin))
            }
            // As 5 primeiras operações são cabeçalho/rodapé; o resto fica na área de conteúdo.
            page.ops.drop(5).forEach { op ->
                val bottom = when (op) {
                    is PdfOp.Text -> op.baseline
                    is PdfOp.Rect -> op.bottom
                    is PdfOp.Line -> maxOf(op.y1, op.y2)
                    is PdfOp.Polyline -> op.points.maxOf { it.second }
                }
                assertTrue("$op abaixo da área útil", bottom <= spec.contentBottom + 0.01f)
            }
        }
    }

    @Test
    fun `sem linhas gera uma pagina com mensagens de vazio`() {
        val pages = layout(emptyList())

        assertEquals(1, pages.size)
        val texts = pages.texts().map { it.text }
        assertTrue("Sem movimentos no período e filtros escolhidos." in texts)
        assertTrue("Sem dados para mostrar." in texts)
        assertFalse(pages.first().ops.filterIsInstance<PdfOp.Text>().any { pages.first().isTableHeader(it) })
        assertDecorated(pages)
        assertInsideMargins(pages)
    }

    @Test
    fun `uma linha cabe numa pagina com cabecalho de tabela`() {
        val pages = layout(rows(1))

        assertEquals(1, pages.size)
        val texts = pages.texts()
        assertEquals(1, texts.count { pages.first().isTableHeader(it) })
        assertTrue(texts.any { it.text == "Produto 0000" })
        assertTrue(texts.any { it.text == "1,00 €" && it.style == PdfTextStyle.HEADING })
        assertTrue(pages.first().ops.any { it is PdfOp.Rect && it.color == PdfColors.ACCENT })
        assertDecorated(pages)
        assertInsideMargins(pages)
    }

    @Test
    fun `cinquenta linhas repetem o cabecalho em cada pagina da tabela`() {
        val pages = layout(rows(50))

        assertTrue(pages.size >= 2)
        val firstTablePage = pages.indexOfFirst { page -> page.ops.filterIsInstance<PdfOp.Text>().any { page.isTableHeader(it) } }
        pages.drop(firstTablePage).forEach { page ->
            assertEquals(1, page.ops.filterIsInstance<PdfOp.Text>().count { page.isTableHeader(it) })
        }
        val products = pages.texts().map { it.text }.filter { it.startsWith("Produto ") }
        assertEquals((0 until 50).map { "Produto %04d".format(it) }.sorted(), products.sorted())
        assertDecorated(pages)
        assertInsideMargins(pages)
    }

    @Test
    fun `mil linhas paginam sem perder linhas nem sair das margens`() {
        val pages = layout(rows(1000))

        assertTrue("páginas: ${pages.size}", pages.size in 20..60)
        val products = pages.texts().filter { it.text.startsWith("Produto ") && it.style == PdfTextStyle.BODY }
        assertEquals(1000, products.map { it.text }.toSet().size)
        assertEquals(pages.size, pages.texts().count { it.text.startsWith("Página ") })
        assertDecorated(pages)
        assertInsideMargins(pages)
    }

    @Test
    fun `nomes portugueses longos sao quebrados sem perder caracteres`() {
        val name = "Pão de forma integral com sementes de girassol, linhaça e açúcar"
        val pages = layout(rows(3) { name })

        val cellLines = pages.texts().filter { it.style == PdfTextStyle.BODY && it.text.isNotBlank() && name.contains(it.text) }
        assertTrue("devia ocupar várias linhas", cellLines.size > 3)
        assertEquals(name, cellLines.take(cellLines.size / 3).joinToString(" ") { it.text })
        assertInsideMargins(pages)
    }

    @Test
    fun `quebra de linha parte palavras longas e reticencias respeitam a largura`() {
        val wrapped = engine.wrap("Supercalifragilístico curto", PdfTextStyle.BODY, 45f)
        assertTrue(wrapped.all { measurer.width(it, PdfTextStyle.BODY) <= 45f })
        assertEquals("Supercalifragilístico curto", wrapped.joinToString(""))
        assertEquals(listOf("Supercalif", "ragilístic", "o curto"), wrapped)
        assertEquals(listOf(""), engine.wrap("", PdfTextStyle.BODY, 45f))

        val cut = engine.ellipsize("Estabelecimento muito comprido", PdfTextStyle.BODY, 60f)
        assertTrue(cut.endsWith(PdfLayoutEngine.ELLIPSIS))
        assertTrue(measurer.width(cut, PdfTextStyle.BODY) <= 60f)
    }
}
