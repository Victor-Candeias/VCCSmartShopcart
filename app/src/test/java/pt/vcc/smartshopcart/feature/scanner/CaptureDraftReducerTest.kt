package pt.vcc.smartshopcart.feature.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import pt.vcc.smartshopcart.core.ocr.OcrLine
import pt.vcc.smartshopcart.core.ocr.PriceCandidateParser
import pt.vcc.smartshopcart.core.ocr.PriceParseResult
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PriceCandidate
import pt.vcc.smartshopcart.domain.model.PriceKind
import pt.vcc.smartshopcart.domain.model.PriceSource
import pt.vcc.smartshopcart.domain.model.Product
import pt.vcc.smartshopcart.domain.model.ProductPrice
import pt.vcc.smartshopcart.domain.model.ProductSource
import pt.vcc.smartshopcart.domain.usecase.ProductLookupStatus
import pt.vcc.smartshopcart.domain.usecase.ResolveProductResult

class CaptureDraftReducerTest {

    private val nutella = ResolveProductResult.Resolved(
        barcode = "3017620422003",
        product = Product("p1", "3017620422003", "Nutella", "Ferrero", "400 g", null, null, ProductSource.OPEN_FOOD_FACTS, 0),
        status = ProductLookupStatus.LOCAL,
        suggestedPrice = null,
    )
    private val label = PriceCandidateParser.parse(listOf(OcrLine("PROMO 1,49 €", 60), OcrLine("2,98 €/kg", 20)))

    @Test
    fun `codigo e depois preco resultam num unico rascunho`() {
        val withCode = CaptureDraftReducer.withProduct(null, nutella, null)
        val draft = CaptureDraftReducer.withPrices(withCode, label, null)!!

        assertEquals("3017620422003", draft.barcode)
        assertEquals("Nutella", draft.name)
        assertEquals(149L, draft.priceCents)
        assertEquals(true, draft.priceFromOcr)
    }

    @Test
    fun `preco e depois codigo resultam num unico rascunho e o preco lido mantem-se`() {
        val withPrice = CaptureDraftReducer.withPrices(null, label, null)!!
        assertNull(withPrice.barcode)

        val draft = CaptureDraftReducer.withProduct(
            withPrice,
            nutella.copy(suggestedPrice = ProductPrice(399, MeasureUnit.UN, "Continente", 0, PriceSource.MANUAL)),
            null,
        )

        assertEquals("3017620422003", draft.barcode)
        assertEquals("Nutella", draft.name)
        assertEquals(149L, draft.priceCents)
        assertEquals(399L, draft.suggestedPriceCents)
        assertEquals(label.candidates, draft.priceCandidates)
    }

    @Test
    fun `codigo e preco na mesma fotografia`() {
        val draft = CaptureDraftReducer.withPrices(CaptureDraftReducer.withProduct(null, nutella, null), label, null)!!
        assertEquals("3017620422003", draft.barcode)
        assertEquals(149L, draft.priceCents)
    }

    @Test
    fun `mesmo codigo lido de novo nao altera o rascunho e codigo diferente comeca outro`() {
        val draft = CaptureDraftReducer.withPrices(CaptureDraftReducer.withProduct(null, nutella, null), label, null)!!
        assertSame(draft, CaptureDraftReducer.withProduct(draft, nutella, null))

        val other = CaptureDraftReducer.withProduct(draft, nutella.copy(barcode = "5601234567892", product = null), null)
        assertEquals("5601234567892", other.barcode)
        assertEquals(emptyList<PriceCandidate>(), other.priceCandidates)
        assertEquals("", other.price)
    }

    @Test
    fun `varios precos sem sugestao segura obrigam a escolher`() {
        val ambiguous = PriceParseResult(
            candidates = listOf(
                PriceCandidate(149, PriceKind.UNKNOWN, 0.3f, "1,49"),
                PriceCandidate(199, PriceKind.UNKNOWN, 0.3f, "1,99"),
            ),
            suggested = null,
            rawText = "1,49\n1,99",
        )
        val draft = CaptureDraftReducer.withPrices(null, ambiguous, null)!!
        assertNull(draft.selectedCandidate)
        assertNull(draft.priceCents)

        val chosen = CaptureDraftReducer.selectCandidate(draft, 1)
        assertEquals(199L, chosen.priceCents)
        assertEquals(true, chosen.priceFromOcr)
    }

    @Test
    fun `escolher preco por kg muda a unidade`() {
        val draft = CaptureDraftReducer.withPrices(null, label, null)!!
        val perKg = CaptureDraftReducer.selectCandidate(draft, draft.priceCandidates.indexOfFirst { it.kind == PriceKind.PER_KG })
        assertEquals(298L, perKg.priceCents)
        assertEquals(MeasureUnit.KG, perKg.unit)
    }

    @Test
    fun `fotografia sem precos nao cria rascunho`() {
        assertNull(CaptureDraftReducer.withPrices(null, PriceCandidateParser.parse(listOf(OcrLine("Nutella 400 g"))), null))
    }
}
