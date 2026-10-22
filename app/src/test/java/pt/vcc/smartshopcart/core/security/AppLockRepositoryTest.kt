package pt.vcc.smartshopcart.core.security

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pt.vcc.smartshopcart.core.datastore.DataStoreAppLockRepository
import pt.vcc.smartshopcart.domain.security.PinCheckResult

class AppLockRepositoryTest {

    private lateinit var repository: DataStoreAppLockRepository
    private var now = 1_000_000L

    @Before
    fun setUp() {
        repository = DataStoreAppLockRepository(InMemoryPreferences(), PinHasher(iterations = 1_000)) { now }
    }

    @Test
    fun `sem PIN o bloqueio esta desativado`() = runBlocking {
        val settings = repository.settings.first()

        assertFalse(settings.pinEnabled)
        assertFalse(settings.biometricEnabled)
        assertEquals(PinCheckResult.Success, repository.verifyPin("0000"))
    }

    @Test
    fun `define e verifica o PIN guardando o comprimento`() = runBlocking {
        repository.setPin("13579")

        val settings = repository.settings.first()
        assertTrue(settings.pinEnabled)
        assertEquals(5, settings.pinLength)
        assertEquals(PinCheckResult.Success, repository.verifyPin("13579"))
        assertEquals(PinCheckResult.Wrong(attemptsLeft = 4), repository.verifyPin("13570"))
    }

    @Test
    fun `cinco falhas bloqueiam mesmo com o PIN certo ate o tempo passar`() = runBlocking {
        repository.setPin("2468")
        repeat(4) { repository.verifyPin("0000") }

        val locked = repository.verifyPin("0000")

        assertEquals(PinCheckResult.LockedOut(now + 30_000), locked)
        assertEquals(now + 30_000, repository.settings.first().lockedOutUntil)
        now += 29_999
        assertEquals(locked, repository.verifyPin("2468"))

        now += 1
        assertEquals(PinCheckResult.Success, repository.verifyPin("2468"))
        val settings = repository.settings.first()
        assertEquals(0, settings.failedAttempts)
        assertEquals(0L, settings.lockedOutUntil)
    }

    @Test
    fun `biometria so com PIN e desativar o PIN apaga tudo`() = runBlocking {
        repository.setBiometricEnabled(true)
        assertFalse(repository.settings.first().biometricEnabled)

        repository.setPin("2468")
        repository.setBiometricEnabled(true)
        assertTrue(repository.settings.first().biometricEnabled)

        repository.disablePin()
        val settings = repository.settings.first()
        assertFalse(settings.pinEnabled)
        assertFalse(settings.biometricEnabled)
        assertEquals(0, settings.pinLength)
    }

    @Test
    fun `alterar o PIN invalida o anterior`() = runBlocking {
        repository.setPin("2468")
        repository.setPin("975310")

        assertEquals(PinCheckResult.Wrong(attemptsLeft = 4), repository.verifyPin("2468"))
        assertEquals(PinCheckResult.Success, repository.verifyPin("975310"))
    }
}

/** DataStore em memória: o `File.renameTo` da JVM no Windows não substitui ficheiros existentes. */
private class InMemoryPreferences : DataStore<Preferences> {
    private val state = MutableStateFlow(emptyPreferences())
    private val mutex = Mutex()

    override val data: Flow<Preferences> = state

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences =
        mutex.withLock { transform(state.value).also { state.value = it } }
}
