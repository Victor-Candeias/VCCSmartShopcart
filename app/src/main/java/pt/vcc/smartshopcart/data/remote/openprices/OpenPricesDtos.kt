package pt.vcc.smartshopcart.data.remote.openprices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Página de `GET /api/v1/prices` (OP4). Todos os campos têm valor por omissão para tolerar respostas parciais. */
@Serializable
data class OpenPricesPageDto(
    val items: List<OpenPriceDto> = emptyList(),
    val page: Int = 1,
    val pages: Int = 1,
    val size: Int = 0,
    val total: Int = 0,
)

@Serializable
data class OpenPriceDto(
    val id: Long,
    @SerialName("product_code") val productCode: String? = null,
    @SerialName("location_id") val locationId: Long? = null,
    val price: Double? = null,
    val currency: String? = null,
    val date: String? = null,
    @SerialName("price_per") val pricePer: String? = null,
    @SerialName("price_is_discounted") val isDiscounted: Boolean? = null,
    @SerialName("price_without_discount") val priceWithoutDiscount: Double? = null,
    @SerialName("discount_type") val discountType: String? = null,
    val type: String? = null,
    @SerialName("duplicate_of") val duplicateOf: Long? = null,
    val location: OpenPriceLocationDto? = null,
)

/** `Location` embebida nos preços e devolvida diretamente por `GET /api/v1/locations/{id}`. */
@Serializable
data class OpenPriceLocationDto(
    val id: Long? = null,
    val type: String? = null,
    @SerialName("osm_name") val osmName: String? = null,
    @SerialName("osm_brand") val osmBrand: String? = null,
    @SerialName("osm_display_name") val displayName: String? = null,
    @SerialName("osm_address_city") val city: String? = null,
    @SerialName("osm_address_country_code") val countryCode: String? = null,
    @SerialName("osm_address_country") val country: String? = null,
)
