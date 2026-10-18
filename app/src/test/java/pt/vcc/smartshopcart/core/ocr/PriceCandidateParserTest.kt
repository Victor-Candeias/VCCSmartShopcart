package pt.vcc.smartshopcart.core.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.smartshopcart.domain.model.PriceKind

class PriceCandidateParserTest {

    private fun parse(vararg lines: String) = PriceCandidateParser.parse(lines.map { OcrLine(it) })

    @Test
    fun `reconhece os formatos portugueses`() {
        listOf("1,49 €", "€ 1,49", "1.49€", "1€49", "1 € 49", "1 49 €", "PVP 1,49 EUR").forEach { text ->
            val result = parse(text)
            assertEquals(text, 149L, result.suggested?.cents)
            assertEquals(text, PriceKind.SALE, result.suggested?.kind)
        }
    }

    @Test
    fun `distingue preco de venda do preco por kg e por litro`() {
        val result = PriceCandidateParser.parse(
            listOf(OcrLine("Bananas da Madeira", 20), OcrLine("1,49 €", 90), OcrLine("1,49 €/kg", 20)),
        )
        assertEquals(setOf(PriceKind.SALE to 149L, PriceKind.PER_KG to 149L), result.candidates.map { it.kind to it.cents }.toSet())
        assertEquals(PriceKind.SALE, result.suggested?.kind)

        assertEquals(PriceKind.PER_L, parse("Leite 1,09 € / L").candidates.single().kind)
        assertEquals(PriceKind.PER_KG, parse("Preço/kg: 3,98 €").candidates.single().kind)
        assertEquals(PriceKind.PER_L, parse("0,45 €/lt").candidates.single().kind)
    }

    @Test
    fun `preco antigo, promocao e desconto`() {
        val sameLine = parse("Antes 1,99 € Agora 1,49 €")
        assertEquals(PriceKind.OLD, sameLine.candidates.single { it.cents == 199L }.kind)
        assertEquals(149L, sameLine.suggested?.cents)

        val labels = parse("-30%", "Poupe 0,50 €", "-0,20 €", "Promoção 1,19 €")
        assertTrue(labels.candidates.none { it.cents == 30L || it.cents == 3000L })
        assertEquals(PriceKind.DISCOUNT, labels.candidates.single { it.cents == 50L }.kind)
        assertEquals(PriceKind.DISCOUNT, labels.candidates.single { it.cents == 20L }.kind)
        assertEquals(119L, labels.suggested?.cents)
    }

    @Test
    fun `texto maior pesa mais do que a ordem ou o valor`() {
        val result = PriceCandidateParser.parse(
            listOf(OcrLine("2,49 €", 20), OcrLine("1,29 €", 100), OcrLine("3,99 €", 20)),
        )
        assertEquals(129L, result.suggested?.cents)
        assertEquals(3, result.candidates.size)
    }

    @Test
    fun `ignora numeros que nao sao precos e nao sugere sem confianca`() {
        assertTrue(parse("Peso 0,750 kg", "Lote 12 34", "Validade 12.10.2026", "1.234,56").candidates.isEmpty())

        val bare = parse("1,49")
        assertEquals(PriceKind.UNKNOWN, bare.candidates.single().kind)
        assertNull(bare.suggested)

        val perKgOnly = parse("2,98 €/kg")
        assertEquals(1, perKgOnly.candidates.size)
        assertNull(perKgOnly.suggested)
    }

    @Test
    fun `guarda o texto bruto e a linha de cada candidato`() {
        val result = parse("Atum", "Promo 0,99 €")
        assertEquals("Atum\nPromo 0,99 €", result.rawText)
        assertEquals("Promo 0,99 €", result.candidates.single().rawText)
    }
}
