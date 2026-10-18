package pt.vcc.smartshopcart.data.remote

import kotlinx.serialization.json.Json
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** `User-Agent` identificativo exigido pela Open Food Facts (D0). */
class UserAgentInterceptor(private val userAgent: String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(chain.request().newBuilder().header("User-Agent", userAgent).build())
}

object OpenFoodFactsClient {
    fun userAgent(versionName: String) = "VCCSmartShopcart/$versionName (https://github.com/Victor-Candeias/VCCSmartShopcart)"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun okHttp(userAgent: String): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(UserAgentInterceptor(userAgent))
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .callTimeout(10, TimeUnit.SECONDS)
        .build()

    fun api(client: OkHttpClient, baseUrl: String = OpenFoodFactsApi.BASE_URL): OpenFoodFactsApi = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()
        .create(OpenFoodFactsApi::class.java)
}