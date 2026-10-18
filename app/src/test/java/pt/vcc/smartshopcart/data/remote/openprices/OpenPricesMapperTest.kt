package pt.vcc.smartshopcart.data.remote.openprices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.smartshopcart.domain.model.PriceBasis
import java.time.LocalDate

class OpenPricesMapperTest {

    private val today = LocalDate.of(2026, 10, 18)
    private val barcode = "5601234567890"
    private val lisboa = OpenPriceLocationDto(
        id = 7,
        type = "OSM",
        osmName = "Continente Colombo",
        osmBrand = "Continente",
        displayName = "Continente Colombo, Avenida Lusíada, Lisboa, Portugal",
        city = "Lisboa",
        countryCode = "PT",
        country = "Portugal",
    )
    private val base = OpenPriceDto(
        id = 1,
        productCode = barcode,
        locationId = 7,
        price = 1.49,
        currency = "EUR",
        date = "2026-10-01",
        type = "PRODUCT",
        location = lisboa,
    )

    @Test
    fun `preco portugues valido e convertido para centimos com loja e cidade`() {
        val price = base.toReferencePrice(barcode, today)!!

        assertEquals(149L, price.priceCents)
        assertEquals("Continente", price.storeName)
        assertEquals("Lisboa", price.storeCity)
        assertEquals("PT", price.storeCountryCode)
        assertEquals(7L, price.locationId)
        assertEquals(LocalDate.of(2026, 10, 1), price.observedAt)
        assertEquals(PriceBasis.UNIT, price.basis)
        assertFalse(price.isDiscounted)
    }

    @Test
    fun `apenas precos de Espanha ou EUR sem pais nao sao portugueses`() {
        assertNull(base.copy(location = lisboa.copy(countryCode = "ES")).toReferencePrice(barcode, today))
        assertNull(base.copy(location = lisboa.copy(countryCode = null)).toReferencePrice(barcode, today))
        assertNull(base.copy(location = null).toReferencePrice(barcode, today))
        assertNull(base.copy(location = lisboa.copy(type = "ONLINE")).toReferencePrice(barcode, today))
    }

    @Test
    fun `localizacao obtida a parte e usada quando a resposta so traz location_id`() {
        val price = base.copy(location = null).toReferencePrice(barcode, today, location = lisboa)

        assertEquals("Continente", price?.storeName)
    }

    @Test
    fun `moeda tipo valor e duplicados invalidos sao ignorados`() {
        assertNull(base.copy(currency = "USD").toReferencePrice(barcode, today))
        assertNull(base.copy(currency = null).toReferencePrice(barcode, today))
        assertNull(base.copy(type = "CATEGORY").toReferencePrice(barcode, today))
        assertNull(base.copy(price = null).toReferencePrice(barcode, today))
        assertNull(base.copy(price = 0.0).toReferencePrice(barcode, today))
        assertNull(base.copy(price = -1.0).toReferencePrice(barcode, today))
        assertNull(base.copy(price = Double.POSITIVE_INFINITY).toReferencePrice(barcode, today))
        assertNull(base.copy(price = Double.NaN).toReferencePrice(barcode, today))
        assertNull(base.copy(duplicateOf = 99).toReferencePrice(barcode, today))
        assertNull(base.copy(productCode = "3017620422003").toReferencePrice(barcode, today))
    }

    @Test
    fun `datas futuras ou invalidas sao ignoradas e antigas ficam como historico`() {
        assertNull(base.copy(date = "2026-10-19").toReferencePrice(barcode, today))
        assertNull(base.copy(date = "18/10/2026").toReferencePrice(barcode, today))
        assertNull(base.copy(date = null).toReferencePrice(barcode, today))

        val old = base.copy(date = "2025-01-10").toReferencePrice(barcode, today)!!
        assertTrue(old.isHistorical(today))
        assertFalse(base.toReferencePrice(barcode, today)!!.isHistorical(today))
    }

    @Test
    fun `preco por kg nao e misturado com unidade e valores desconhecidos sao ignorados`() {
        assertEquals(PriceBasis.KILOGRAM, base.copy(pricePer = "KILOGRAM").toReferencePrice(barcode, today)?.basis)
        assertEquals(PriceBasis.UNIT, base.copy(pricePer = "UNIT").toReferencePrice(barcode, today)?.basis)
        assertEquals(PriceBasis.UNIT, base.copy(pricePer = "").toReferencePrice(barcode, today)?.basis)
        assertNull(base.copy(pricePer = "LITER").toReferencePrice(barcode, today))
    }

    @Test
    fun `promocao guarda tipo e preco sem desconto so quando descontado`() {
        val promo = base.copy(isDiscounted = true, discountType = "LOYALTY_PROGRAM", priceWithoutDiscount = 1.99)
            .toReferencePrice(barcode, today)!!
        assertTrue(promo.isDiscounted)
        assertEquals("LOYALTY_PROGRAM", promo.discountType)
        assertEquals(199L, promo.priceWithoutDiscountCents)

        val normal = base.copy(discountType = "SALE", priceWithoutDiscount = 1.99).toReferencePrice(barcode, today)!!
        assertNull(normal.discountType)
        assertNull(normal.priceWithoutDiscountCents)
    }

    @Test
    fun `conversao monetaria arredonda HALF_UP sem erros de virgula flutuante`() {
        assertEquals(29L, 0.285.toCentsOrNull())
        assertEquals(110L, 1.1.toCentsOrNull())
        assertEquals(1999L, 19.99.toCentsOrNull())
        assertNull(0.004.toCentsOrNull())
    }

    @Test
    fun `EAN com zeros a esquerda e preservado como texto`() {
        val code = "0012345678905"
        val price = base.copy(productCode = code).toReferencePrice(code, today)

        assertEquals(code, price?.barcode)
    }

    @Test
    fun `loja desconhecida nao e inventada`() {
        val price = base.copy(location = OpenPriceLocationDto(countryCode = "PT")).toReferencePrice(barcode, today)!!

        assertNull(price.storeName)
        assertNull(price.storeCity)
    }
}
