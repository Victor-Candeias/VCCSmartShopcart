package pt.vcc.smartshopcart.data.remote.openprices

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import pt.vcc.smartshopcart.data.remote.UserAgentInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Cliente Open Prices (OP2): mesmo `User-Agent` da app, JSON tolerante e sem registo HTTP. */
object OpenPricesClient {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun okHttp(userAgent: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(UserAgentInterceptor(userAgent))
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("Accept", "application/json").build())
        }
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .callTimeout(20, TimeUnit.SECONDS)
        .build()

    fun api(client: OkHttpClient, baseUrl: String = OpenPricesApi.BASE_URL): OpenPricesApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OpenPricesApi::class.java)
}
