package pt.vcc.smartshopcart.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** API v3 de produtos da Open Food Facts. Só recebe o código de barras (§6.1). */
interface OpenFoodFactsApi {
    @GET("api/v3/product/{barcode}")
    suspend fun product(
        @Path("barcode") barcode: String,
        @Query("fields") fields: String = FIELDS,
    ): Response<OffProductResponse>

    companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"
        const val FIELDS = "code,product_name,product_name_pt,generic_name,brands,quantity,categories,image_front_url"
    }
}

@Serializable
data class OffProductResponse(
    val code: String? = null,
    val status: String? = null,
    val product: OffProduct? = null,
)

@Serializable
data class OffProduct(
    @SerialName("product_name") val productName: String? = null,
    @SerialName("product_name_pt") val productNamePt: String? = null,
    @SerialName("generic_name") val genericName: String? = null,
    val brands: String? = null,
    val quantity: String? = null,
    val categories: String? = null,
    @SerialName("image_front_url") val imageFrontUrl: String? = null,
)
