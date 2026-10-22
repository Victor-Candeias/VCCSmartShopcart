package pt.vcc.smartshopcart.feature.security

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
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
import pt.vcc.smartshopcart.core.security.AppLockSession
import pt.vcc.smartshopcart.domain.security.AppLockRepository
import pt.vcc.smartshopcart.domain.security.AppLockSettings
import pt.vcc.smartshopcart.domain.security.PinCheckResult
import pt.vcc.smartshopcart.domain.security.PinPolicy

@OptIn(ExperimentalCoroutinesApi::class)
class AppLockViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private var now = 1_000_000L
    private val repository = FakeAppLockRepository { now }
    private val session = AppLockSession { now }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = AppLockViewModel(repository, session) { now }

    @Test
    fun `sem PIN a app fica desbloqueada`() = runTest(dispatcher) {
        assertEquals(LockGate.UNLOCKED, viewModel().state.value.gate)
    }

    @Test
    fun `com PIN arranca bloqueada e o PIN certo desbloqueia`() = runTest(dispatcher) {
        repository.pin = "2468"
        val vm = viewModel()
        assertEquals(LockGate.LOCKED, vm.state.value.gate)
        assertEquals(4, vm.state.value.pinLength)

        "246".forEach { vm.onDigit(it.digitToInt()) }
        assertEquals(3, vm.state.value.enteredDigits)
        vm.onDigit(8)

        assertEquals(LockGate.UNLOCKED, vm.state.value.gate)
        assertTrue(session.unlocked.value)
    }

    @Test
    fun `PIN errado limpa a introducao e indica as tentativas restantes`() = runTest(dispatcher) {
        repository.pin = "2468"
        val vm = viewModel()

        "1111".forEach { vm.onDigit(it.digitToInt()) }

        assertEquals(LockGate.LOCKED, vm.state.value.gate)
        assertEquals(0, vm.state.value.enteredDigits)
        assertEquals(4, vm.state.value.wrongAttemptsLeft)
    }

    @Test
    fun `durante o bloqueio por tentativas ignora digitos`() = runTest(dispatcher) {
        repository.pin = "2468"
        val vm = viewModel()
        repeat(PinPolicy.MAX_ATTEMPTS) { "1111".forEach { vm.onDigit(it.digitToInt()) } }
        assertEquals(now + PinPolicy.BASE_LOCKOUT_MS, vm.state.value.lockedOutUntil)

        vm.onDigit(2)
        assertEquals(0, vm.state.value.enteredDigits)

        now += PinPolicy.BASE_LOCKOUT_MS
        "2468".forEach { vm.onDigit(it.digitToInt()) }
        assertEquals(LockGate.UNLOCKED, vm.state.value.gate)
    }

    @Test
    fun `biometria e pedida automaticamente uma vez por bloqueio`() = runTest(dispatcher) {
        repository.pin = "2468"
        repository.biometric = true
        val vm = viewModel()
        assertTrue(vm.state.value.autoPromptBiometric)

        vm.onBiometricPromptShown()
        assertFalse(vm.state.value.autoPromptBiometric)

        vm.onBiometricSuccess()
        assertEquals(LockGate.UNLOCKED, vm.state.value.gate)

        session.lock()
        assertEquals(LockGate.LOCKED, vm.state.value.gate)
        assertTrue(vm.state.value.autoPromptBiometric)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class PinSetupViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private var now = 1_000_000L
    private val repository = FakeAppLockRepository { now }
    private val session = AppLockSession { now }

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(mode: PinSetupMode) =
        PinSetupViewModel(SavedStateHandle(mapOf(PinSetupViewModel.MODE_KEY to mode.name)), repository, session) { now }

    private fun PinSetupViewModel.type(pin: String) = pin.forEach { onDigit(it.digitToInt()) }

    @Test
    fun `criar pede o PIN duas vezes e deixa a app desbloqueada`() = runTest(dispatcher) {
        val vm = viewModel(PinSetupMode.CREATE)
        assertEquals(PinSetupStep.NEW, vm.state.value.step)

        vm.type("123")
        assertFalse(vm.state.value.canContinue)
        vm.type("45")
        assertTrue(vm.state.value.canContinue)
        vm.onContinue()
        assertEquals(PinSetupStep.CONFIRM, vm.state.value.step)
        assertEquals(5, vm.state.value.dots)

        vm.type("12345")

        assertTrue(vm.state.value.finished)
        assertEquals("12345", repository.pin)
        assertTrue(session.unlocked.value)
    }

    @Test
    fun `confirmacao diferente volta ao novo PIN sem gravar`() = runTest(dispatcher) {
        val vm = viewModel(PinSetupMode.CREATE)
        vm.type("1234")
        vm.onContinue()

        vm.type("4321")

        assertEquals(PinSetupStep.NEW, vm.state.value.step)
        assertEquals(PinSetupError.MISMATCH, vm.state.value.error)
        assertEquals(0, vm.state.value.enteredDigits)
        assertNull(repository.pin)
    }

    @Test
    fun `alterar exige o PIN atual`() = runTest(dispatcher) {
        repository.pin = "2468"
        val vm = viewModel(PinSetupMode.CHANGE)
        assertEquals(PinSetupStep.CURRENT, vm.state.value.step)

        vm.type("1111")
        assertEquals(PinSetupError.WRONG_PIN, vm.state.value.error)
        assertEquals(4, vm.state.value.attemptsLeft)
        assertEquals(PinSetupStep.CURRENT, vm.state.value.step)

        vm.type("2468")
        assertEquals(PinSetupStep.NEW, vm.state.value.step)
        vm.type("975310")
        vm.onContinue()
        vm.type("975310")

        assertTrue(vm.state.value.finished)
        assertEquals("975310", repository.pin)
    }

    @Test
    fun `desativar com o PIN atual remove PIN e biometria`() = runTest(dispatcher) {
        repository.pin = "2468"
        repository.biometric = true
        val vm = viewModel(PinSetupMode.DISABLE)

        vm.type("2468")

        assertTrue(vm.state.value.finished)
        assertNull(repository.pin)
        assertFalse(repository.biometric)
    }
}

/** Repositório em memória com a mesma política de tentativas do real. */
private class FakeAppLockRepository(private val now: () -> Long) : AppLockRepository {
    private val state = MutableStateFlow(AppLockSettings())
    override val settings: StateFlow<AppLockSettings> = state

    var pin: String? = null
        set(value) {
            field = value
            state.update { it.copy(pinEnabled = value != null, pinLength = value?.length ?: 0, biometricEnabled = value != null && biometric) }
        }

    var biometric: Boolean = false
        set(value) {
            field = value
            state.update { it.copy(biometricEnabled = pin != null && value) }
        }

    override suspend fun setPin(pin: String) {
        this.pin = pin
        state.update { it.copy(failedAttempts = 0, lockedOutUntil = 0) }
    }

    override suspend fun verifyPin(pin: String): PinCheckResult {
        val current = this.pin ?: return PinCheckResult.Success
        val s = state.value
        if (now() < s.lockedOutUntil) return PinCheckResult.LockedOut(s.lockedOutUntil)
        if (pin == current) {
            state.update { it.copy(failedAttempts = 0, lockedOutUntil = 0) }
            return PinCheckResult.Success
        }
        val failed = s.failedAttempts + 1
        val lockout = PinPolicy.lockoutMillis(failed)
        state.update { it.copy(failedAttempts = failed, lockedOutUntil = if (lockout > 0) now() + lockout else it.lockedOutUntil) }
        return if (lockout > 0) PinCheckResult.LockedOut(now() + lockout) else PinCheckResult.Wrong(PinPolicy.attemptsLeft(failed))
    }

    override suspend fun disablePin() {
        biometric = false
        pin = null
        state.value = AppLockSettings()
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        if (pin != null) biometric = enabled
    }
}
