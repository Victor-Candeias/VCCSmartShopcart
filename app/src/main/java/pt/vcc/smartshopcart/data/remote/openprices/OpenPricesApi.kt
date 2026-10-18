package pt.vcc.smartshopcart.data.remote.openprices

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Leitura pública da API Open Prices (sem chave). Devolve `Response` para tratar 404, 429 e `Retry-After`. */
interface OpenPricesApi {
    @GET("prices")
    suspend fun getPrices(
        @Query("product_code") barcode: String,
        @Query("currency") currency: String = "EUR",
        @Query("type") type: String = "PRODUCT",
        @Query("order_by") orderBy: String = "-date",
        @Query("page") page: Int = 1,
        @Query("size") size: Int = PAGE_SIZE,
    ): Response<OpenPricesPageDto>

    @GET("locations/{id}")
    suspend fun getLocation(@Path("id") id: Long): Response<OpenPriceLocationDto>

    companion object {
        const val BASE_URL = "https://prices.openfoodfacts.org/api/v1/"
        const val PAGE_SIZE = 100
    }
}
