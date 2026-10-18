package pt.vcc.smartshopcart.data.remote.openprices

import pt.vcc.smartshopcart.domain.model.PriceBasis
import pt.vcc.smartshopcart.domain.model.ReferencePrice
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeParseException

/** Euros (desserializados como `Double`) para cêntimos, `HALF_UP` (OP6). `null` se não for finito e positivo. */
fun Double.toCentsOrNull(): Long? {
    if (!isFinite() || this <= 0.0) return null
    return BigDecimal.valueOf(this).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        .takeIf { it > 0 }
}

/** `price_per` → base interna (OP7); `null` quando o valor é desconhecido e o preço não pode ser comparado. */
fun parsePriceBasis(pricePer: String?): PriceBasis? = when (pricePer?.trim()?.uppercase()) {
    null, "", "UNIT" -> PriceBasis.UNIT
    "KILOGRAM" -> PriceBasis.KILOGRAM
    else -> null
}

/**
 * Converte uma observação Open Prices num [ReferencePrice] português, ou `null` se não for válida (OP5, OP6):
 * só `PRODUCT` em `EUR`, numa localização cujo país é `PT`, com valor positivo e data válida não futura.
 * [location] permite usar a localização obtida à parte quando a resposta só traz `location_id`.
 */
fun OpenPriceDto.toReferencePrice(
    barcode: String,
    today: LocalDate,
    location: OpenPriceLocationDto? = this.location,
): ReferencePrice? {
    if (duplicateOf != null) return null
    if (currency?.trim()?.uppercase() != "EUR" || type?.trim()?.uppercase() != "PRODUCT") return null
    if (productCode != null && productCode != barcode) return null
    val loc = location ?: return null
    if (loc.type?.uppercase() == "ONLINE") return null
    if (loc.countryCode?.trim()?.uppercase() != PORTUGAL) return null
    val cents = price?.toCentsOrNull() ?: return null
    val basis = parsePriceBasis(pricePer) ?: return null
    val observed = try {
        LocalDate.parse(date?.trim() ?: return null)
    } catch (_: DateTimeParseException) {
        return null
    }
    if (observed.isAfter(today)) return null
    val discounted = isDiscounted == true
    return ReferencePrice(
        externalId = id,
        barcode = barcode,
        priceCents = cents,
        storeName = listOf(loc.osmBrand, loc.osmName, loc.displayName?.substringBefore(','))
            .firstOrNull { !it.isNullOrBlank() }?.trim(),
        storeCity = loc.city?.trim()?.ifEmpty { null },
        storeCountryCode = PORTUGAL,
        locationId = locationId ?: loc.id,
        observedAt = observed,
        isDiscounted = discounted,
        discountType = discountType?.trim()?.ifEmpty { null }?.takeIf { discounted },
        priceWithoutDiscountCents = priceWithoutDiscount?.toCentsOrNull()?.takeIf { discounted && it > cents },
        basis = basis,
    )
}

private const val PORTUGAL = "PT"
