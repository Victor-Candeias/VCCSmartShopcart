package pt.vcc.smartshopcart.feature.prices

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.smartshopcart.domain.model.PriceBasis
import pt.vcc.smartshopcart.domain.model.ReferencePrice
import pt.vcc.smartshopcart.domain.model.ReferencePrices
import pt.vcc.smartshopcart.domain.model.ReferenceSyncState
import pt.vcc.smartshopcart.domain.model.ReferenceSyncStatus
import pt.vcc.smartshopcart.domain.repository.ReferencePriceRepository
import pt.vcc.smartshopcart.domain.repository.ReferenceRefreshResult
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class ProductPricesViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val repository = FakeRepository()
    private lateinit var viewModel: ProductPricesViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        // 2026-02-01T12:00:00Z
        viewModel = ProductPricesViewModel(repository) { 1_769_947_200_000 }
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `mostra a cache separada por base e atualiza sem forcar`() = runTest(dispatcher) {
        viewModel.state.launchIn(backgroundScope)
        repository.data.value = ReferencePrices(
            listOf(price(1, PriceBasis.UNIT), price(2, PriceBasis.KILOGRAM), price(3, PriceBasis.UNIT)),
            sync(ReferenceSyncStatus.SUCCESS),
        )

        viewModel.load(EAN)

        val state = viewModel.state.value
        assertEquals(EAN, state.barcode)
        assertEquals(listOf(1L, 3L), state.unitPrices.map { it.externalId })
        assertEquals(listOf(2L), state.kilogramPrices.map { it.externalId })
        assertEquals(LocalDate.of(2026, 2, 1), state.today)
        assertFalse(state.refreshing)
        assertNull(state.notice)
        assertEquals(listOf(EAN to false), repository.calls)
    }

    @Test
    fun `sem rede ou com 429 mantem a cache e mostra aviso discreto`() = runTest(dispatcher) {
        viewModel.state.launchIn(backgroundScope)
        repository.data.value = ReferencePrices(listOf(price(1, PriceBasis.UNIT)), sync(ReferenceSyncStatus.SUCCESS))
        repository.result = ReferenceRefreshResult.Offline

        viewModel.load(EAN)
        assertEquals(PricesNotice.OFFLINE, viewModel.state.value.notice)
        assertEquals(1, viewModel.state.value.unitPrices.size)

        repository.result = ReferenceRefreshResult.RateLimited(until = 1)
        viewModel.retry()
        assertEquals(PricesNotice.RATE_LIMITED, viewModel.state.value.notice)
        assertEquals(EAN to true, repository.calls.last())
    }

    @Test
    fun `o mesmo EAN em atualizacao nao gera nova chamada`() = runTest(dispatcher) {
        viewModel.state.launchIn(backgroundScope)
        val pending = CompletableDeferred<ReferenceRefreshResult>()
        repository.pending = pending

        viewModel.load(EAN)
        assertTrue(viewModel.state.value.refreshing)
        viewModel.load(EAN)
        assertEquals(1, repository.calls.size)

        pending.complete(ReferenceRefreshResult.Updated(0, incomplete = false))
        assertFalse(viewModel.state.value.refreshing)
    }

    private class FakeRepository : ReferencePriceRepository {
        val data = MutableStateFlow(ReferencePrices(emptyList(), null))
        val calls = mutableListOf<Pair<String, Boolean>>()
        var result: ReferenceRefreshResult = ReferenceRefreshResult.UpToDate
        var pending: CompletableDeferred<ReferenceRefreshResult>? = null

        override fun observe(barcode: String): Flow<ReferencePrices> = data

        override suspend fun refresh(barcode: String, force: Boolean): ReferenceRefreshResult {
            calls += barcode to force
            return pending?.await() ?: result
        }
    }

    private fun price(id: Long, basis: PriceBasis) = ReferencePrice(
        externalId = id,
        barcode = EAN,
        priceCents = 100 + id,
        storeName = "Continente",
        storeCity = "Lisboa",
        storeCountryCode = "PT",
        locationId = 33,
        observedAt = LocalDate.of(2026, 1, 10),
        isDiscounted = false,
        discountType = null,
        priceWithoutDiscountCents = null,
        basis = basis,
    )

    private fun sync(status: ReferenceSyncStatus) = ReferenceSyncState(1, 1, status, 3, incomplete = false, retryAfter = null)

    private companion object {
        const val EAN = "5601234567890"
    }
}
