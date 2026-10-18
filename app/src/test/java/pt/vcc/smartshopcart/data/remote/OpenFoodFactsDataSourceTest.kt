package pt.vcc.smartshopcart.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.smartshopcart.domain.repository.RemoteProduct
import pt.vcc.smartshopcart.domain.repository.RemoteProductResult

class OpenFoodFactsDataSourceTest {

    private lateinit var server: MockWebServer
    private lateinit var dataSource: OpenFoodFactsDataSource
    private val userAgent = OpenFoodFactsClient.userAgent("9.9.9")

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val api = OpenFoodFactsClient.api(OpenFoodFactsClient.okHttp(userAgent), server.url("/").toString())
        dataSource = OpenFoodFactsDataSource(api)
    }

    @After
    fun tearDown() = server.shutdown()

    @Test
    fun `mapeia produto encontrado e envia User-Agent`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200).setBody(
                """
                {"code":"3017620422003","status":"success","result":{"id":"product_found"},
                 "product":{"product_name":"Nutella","brands":"Ferrero, Nutella","quantity":"400 g",
                 "categories":"Barradas, Cacau","image_front_url":"https://img/x.jpg","unknown":1}}
                """.trimIndent(),
            ),
        )

        val result = dataSource.lookup("3017620422003")

        assertEquals(
            RemoteProductResult.Found(
                RemoteProduct("3017620422003", "Nutella", "Ferrero", "400 g", "Barradas", "https://img/x.jpg"),
            ),
            result,
        )
        val request = server.takeRequest()
        assertEquals("VCCSmartShopcart/9.9.9 (https://github.com/Victor-Candeias/VCCSmartShopcart)", request.getHeader("User-Agent"))
        assertTrue(request.path!!.startsWith("/api/v3/product/3017620422003?fields="))
    }

    @Test
    fun `prefere nome em portugues e tolera campos ausentes`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("""{"status":"success","product":{"product_name":"Milk","product_name_pt":"Leite","brands":null}}"""),
        )

        val result = dataSource.lookup("5601234567892") as RemoteProductResult.Found

        assertEquals("Leite", result.product.name)
        assertEquals(null, result.product.brand)
    }

    @Test
    fun `404 e produto nao encontrado`() = runTest {
        server.enqueue(MockResponse().setResponseCode(404).setBody("""{"status":"failure","result":{"id":"product_not_found"}}"""))

        assertEquals(RemoteProductResult.NotFound, dataSource.lookup("3017620422003"))
    }

    @Test
    fun `erro do servidor ou resposta invalida e falha nao bloqueante`() = runTest {
        server.enqueue(MockResponse().setResponseCode(503))
        assertEquals(RemoteProductResult.Failed, dataSource.lookup("3017620422003"))

        server.enqueue(MockResponse().setResponseCode(200).setBody("<html>"))
        assertEquals(RemoteProductResult.Failed, dataSource.lookup("3017620422003"))
    }
}