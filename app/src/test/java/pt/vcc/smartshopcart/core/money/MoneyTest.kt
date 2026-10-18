package pt.vcc.smartshopcart.core.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import java.math.BigDecimal

class MoneyTest {

    @Test
    fun `1,79 por kg vezes 0,750 kg arredonda para 1,34`() {
        val total = LineTotalCalculator.lineTotalCents(179, BigDecimal("0.750"), MeasureUnit.KG)
        assertEquals(134, total)
        assertEquals("1,34\u00A0€", MoneyFormatter.format(total))
    }

    @Test
    fun `gramas sao convertidas para kg antes do calculo`() {
        assertEquals(134, LineTotalCalculator.lineTotalCents(179, BigDecimal("750"), MeasureUnit.G))
        assertEquals(100, LineTotalCalculator.lineTotalCents(200, BigDecimal("500"), MeasureUnit.ML))
    }

    @Test
    fun `arredondamento HALF_UP ao cêntimo`() {
        // 0,99 × 0,5 = 0,495 → 0,50
        assertEquals(50, LineTotalCalculator.lineTotalCents(99, BigDecimal("0.5"), MeasureUnit.KG))
        // 0,33 × 3 = 0,99 sem arredondamento
        assertEquals(99, LineTotalCalculator.lineTotalCents(33, BigDecimal("3"), MeasureUnit.UN))
    }

    @Test
    fun `desconto e subtraido e o total nunca fica negativo`() {
        assertEquals(250, LineTotalCalculator.lineTotalCents(150, BigDecimal("2"), MeasureUnit.UN, discountCents = 50))
        assertEquals(0, LineTotalCalculator.lineTotalCents(150, BigDecimal("1"), MeasureUnit.UN, discountCents = 500))
    }

    @Test
    fun `soma de linhas com quantidades nao perde cêntimos`() {
        val lines = listOf(
            LineTotalCalculator.lineTotalCents(149, BigDecimal("3"), MeasureUnit.UN),
            LineTotalCalculator.lineTotalCents(179, BigDecimal("0.750"), MeasureUnit.KG),
            LineTotalCalculator.lineTotalCents(1, BigDecimal("1"), MeasureUnit.UN),
        )
        assertEquals(447 + 134 + 1, LineTotalCalculator.sum(lines))
    }

    @Test
    fun `parse de preços com vírgula, ponto e símbolo`() {
        assertEquals(149L, MoneyParser.parseToCents("1,49"))
        assertEquals(149L, MoneyParser.parseToCents("1.49"))
        assertEquals(149L, MoneyParser.parseToCents("1,49 €"))
        assertEquals(200L, MoneyParser.parseToCents("2"))
        assertEquals(150L, MoneyParser.parseToCents("1,5"))
        assertNull(MoneyParser.parseToCents("1,499"))
        assertNull(MoneyParser.parseToCents("abc"))
        assertNull(MoneyParser.parseToCents(""))
    }

    @Test
    fun `parse e formatação de quantidades`() {
        assertEquals(BigDecimal("0.75"), QuantityParser.parse("0,750"))
        assertEquals(BigDecimal("2"), QuantityParser.parse("2"))
        assertNull(QuantityParser.parse("0"))
        assertNull(QuantityParser.parse("-1"))
        assertEquals("0,75", QuantityParser.format(BigDecimal("0.750")))
        assertEquals("10", QuantityParser.format(BigDecimal("1E+1")))
    }

    @Test
    fun `formatação com separador de milhares e negativos`() {
        assertEquals("1\u00A0234,56\u00A0€", MoneyFormatter.format(123456))
        assertEquals("0,05\u00A0€", MoneyFormatter.format(5))
        assertEquals("-2,00\u00A0€", MoneyFormatter.format(-200))
        assertEquals("1,79\u00A0€/kg", MoneyFormatter.formatPerUnit(179, MeasureUnit.G))
        assertEquals("1,49", MoneyFormatter.formatPlain(149))
    }
}
