package pt.vcc.smartshopcart.data.remote.openprices

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.smartshopcart.data.remote.OpenFoodFactsClient

class OpenPricesApiTest {

    private lateinit var server: MockWebServer
    private lateinit var api: OpenPricesApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        api = OpenPricesClient.api(OpenPricesClient.okHttp(OpenFoodFactsClient.userAgent("9.9.9")), server.url("/api/v1/").toString())
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `le pagina real com location embebida e envia cabecalhos e filtros`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"items":[{"id":2207,"product_id":238068,"location_id":33,"proof_id":1223,
                  "product":{"id":238068,"code":"3017620422003"},
                  "location":{"id":33,"type":"OSM","osm_name":"Pingo Doce Alvalade","osm_brand":"Pingo Doce",
                    "osm_display_name":"Pingo Doce Alvalade, Lisboa, Portugal","osm_address_city":"Lisboa",
                    "osm_address_country":"Portugal","osm_address_country_code":"PT","osm_lat":38.7},
                  "type":"PRODUCT","product_code":"3017620422003","price":3.21,"price_is_discounted":false,
                  "price_without_discount":null,"discount_type":null,"price_per":null,"currency":"EUR",
                  "date":"2026-01-11","duplicate_of":null,"tags":[]}],
                 "page":1,"pages":84,"size":100,"total":167}
                """.trimIndent(),
            ),
        )

        val response = api.getPrices("3017620422003")
        val body = response.body()!!

        assertEquals(84, body.pages)
        assertEquals(167, body.total)
        val item = body.items.single()
        assertEquals(3.21, item.price!!, 0.0)
        assertEquals("PT", item.location?.countryCode)
        assertEquals("Pingo Doce", item.location?.osmBrand)

        val request = server.takeRequest()
        assertEquals("VCCSmartShopcart/9.9.9 (https://github.com/Victor-Candeias/VCCSmartShopcart)", request.getHeader("User-Agent"))
        assertEquals("application/json", request.getHeader("Accept"))
        val url = request.requestUrl!!
        assertEquals("/api/v1/prices", url.encodedPath)
        assertEquals("3017620422003", url.queryParameter("product_code"))
        assertEquals("EUR", url.queryParameter("currency"))
        assertEquals("PRODUCT", url.queryParameter("type"))
        assertEquals("-date", url.queryParameter("order_by"))
        assertEquals("100", url.queryParameter("size"))
    }

    @Test
    fun `location sem wrapper e respostas 429 expoem Retry-After`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"id":33,"type":"OSM","osm_name":"Continente","osm_address_country_code":"PT","extra":1}"""),
        )
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "120"))

        val location = api.getLocation(33).body()!!
        assertEquals("Continente", location.osmName)
        assertTrue(server.takeRequest().path!!.endsWith("/api/v1/locations/33"))

        val limited = api.getPrices("3017620422003")
        assertEquals(429, limited.code())
        assertEquals("120", limited.headers()["Retry-After"])
    }
}
