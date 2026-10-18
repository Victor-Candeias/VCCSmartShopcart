package pt.vcc.smartshopcart.core.money

import pt.vcc.smartshopcart.domain.model.MeasureUnit
import java.math.BigDecimal
import java.math.RoundingMode

/** Formata cêntimos em euros no formato português, por exemplo `1 234,56 €`. */
object MoneyFormatter {
    private const val NBSP = '\u00A0'

    fun format(cents: Long): String {
        val negative = cents < 0
        val abs = kotlin.math.abs(cents)
        val euros = (abs / 100).toString().reversed().chunked(3).joinToString(NBSP.toString()).reversed()
        val rest = (abs % 100).toString().padStart(2, '0')
        return (if (negative) "-" else "") + "$euros,$rest$NBSP€"
    }

    /** Valor sem símbolo, adequado a campos editáveis: `1,49`. */
    fun formatPlain(cents: Long): String {
        val abs = kotlin.math.abs(cents)
        return (if (cents < 0) "-" else "") + "${abs / 100},${(abs % 100).toString().padStart(2, '0')}"
    }

    fun formatPerUnit(cents: Long, unit: MeasureUnit): String =
        if (unit.priceUnit == MeasureUnit.UN) format(cents) else "${format(cents)}/${unit.priceUnit.symbol}"
}

/** Converte texto introduzido pelo utilizador (`1,49`, `1.49`, `1,49 €`, `2`) em cêntimos. */
object MoneyParser {
    private val pattern = Regex("""^\d{1,7}(?:[.,]\d{1,2})?$""")

    fun parseToCents(input: String): Long? {
        val cleaned = input.replace("€", "").replace('\u00A0', ' ').trim().replace(" ", "")
        if (!pattern.matches(cleaned)) return null
        return BigDecimal(cleaned.replace(',', '.'))
            .movePointRight(2)
            .setScale(0, RoundingMode.UNNECESSARY)
            .longValueExact()
    }
}

/** Converte quantidades (`1`, `0,750`, `1.5`) em [BigDecimal] positivo. */
object QuantityParser {
    private val pattern = Regex("""^\d{1,6}(?:[.,]\d{1,3})?$""")

    fun parse(input: String): BigDecimal? {
        val cleaned = input.trim()
        if (!pattern.matches(cleaned)) return null
        val value = BigDecimal(cleaned.replace(',', '.'))
        return if (value.signum() > 0) value.stripTrailingZerosSafe() else null
    }

    fun format(quantity: BigDecimal): String = quantity.stripTrailingZerosSafe().toPlainString().replace('.', ',')

    private fun BigDecimal.stripTrailingZerosSafe(): BigDecimal {
        val stripped = stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }
}

object LineTotalCalculator {
    /**
     * `unitPrice × quantidade` arredondado `HALF_UP` ao cêntimo, menos o desconto absoluto da
     * linha; nunca negativo (D20). Exemplo: `179 × 0,750 kg = 134,25 → 134`.
     */
    fun lineTotalCents(
        unitPriceCents: Long,
        quantity: BigDecimal,
        unit: MeasureUnit,
        discountCents: Long = 0,
    ): Long {
        require(unitPriceCents >= 0) { "Preço negativo" }
        require(discountCents >= 0) { "Desconto negativo" }
        val subtotal = BigDecimal.valueOf(unitPriceCents)
            .multiply(unit.toPriceUnitQuantity(quantity))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact()
        return (subtotal - discountCents).coerceAtLeast(0)
    }

    fun sum(lineTotalsCents: Iterable<Long>): Long = lineTotalsCents.fold(0L) { acc, v -> Math.addExact(acc, v) }
}
