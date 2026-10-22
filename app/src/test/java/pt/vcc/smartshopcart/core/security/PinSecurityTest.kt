package pt.vcc.smartshopcart.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import pt.vcc.smartshopcart.domain.security.PinPolicy

class PinHasherTest {

    private val hasher = PinHasher(iterations = 1_000)

    @Test
    fun `verifica o PIN certo e rejeita outros`() {
        val stored = hasher.hash("2468")

        assertTrue(hasher.verify("2468", stored))
        assertFalse(hasher.verify("2469", stored))
        assertFalse(hasher.verify("24680", stored))
    }

    @Test
    fun `nao guarda o PIN em claro e usa sal diferente em cada hash`() {
        val first = hasher.hash("2468")
        val second = hasher.hash("2468")

        assertFalse("2468" in first)
        assertNotEquals(first, second)
        assertTrue(first.startsWith("pbkdf2-sha256\$1000\$"))
    }

    @Test
    fun `hash guardado com outras iteracoes continua valido`() {
        val stored = PinHasher(iterations = 2_000).hash("135790")

        assertTrue(hasher.verify("135790", stored))
    }

    @Test
    fun `hash mal formado nunca e aceite`() {
        listOf("", "2468", "pbkdf2-sha256\$0\$AA\$AA", "md5\$1000\$AA\$AA", "pbkdf2-sha256\$1000\$!!\$AA").forEach {
            assertFalse(it, hasher.verify("2468", it))
        }
    }
}

class PinPolicyTest {

    @Test
    fun `aceita apenas 4 a 6 digitos`() {
        assertTrue(PinPolicy.isValid("0000"))
        assertTrue(PinPolicy.isValid("123456"))
        assertFalse(PinPolicy.isValid("123"))
        assertFalse(PinPolicy.isValid("1234567"))
        assertFalse(PinPolicy.isValid("12a4"))
        assertFalse(PinPolicy.isValid("１２３４"))
    }

    @Test
    fun `bloqueia a cada 5 falhas com tempo a duplicar ate 10 minutos`() {
        assertEquals(0L, PinPolicy.lockoutMillis(0))
        assertEquals(0L, PinPolicy.lockoutMillis(4))
        assertEquals(30_000L, PinPolicy.lockoutMillis(5))
        assertEquals(0L, PinPolicy.lockoutMillis(6))
        assertEquals(60_000L, PinPolicy.lockoutMillis(10))
        assertEquals(480_000L, PinPolicy.lockoutMillis(25))
        assertEquals(600_000L, PinPolicy.lockoutMillis(30))
        assertEquals(600_000L, PinPolicy.lockoutMillis(5_000))
    }

    @Test
    fun `tentativas restantes antes do proximo bloqueio`() {
        assertEquals(5, PinPolicy.attemptsLeft(0))
        assertEquals(4, PinPolicy.attemptsLeft(1))
        assertEquals(1, PinPolicy.attemptsLeft(4))
        assertEquals(5, PinPolicy.attemptsLeft(5))
        assertEquals(4, PinPolicy.attemptsLeft(6))
    }
}

class AppLockSessionTest {

    private var now = 1_000_000L
    private val session = AppLockSession { now }

    @Test
    fun `comeca bloqueada e desbloqueia`() {
        assertFalse(session.unlocked.value)
        session.markUnlocked()
        assertTrue(session.unlocked.value)
    }

    @Test
    fun `volta a bloquear apenas apos 30 segundos em segundo plano`() {
        session.markUnlocked()

        session.onAppBackgrounded()
        now += AppLockSession.RELOCK_AFTER_MS - 1
        session.onAppForegrounded()
        assertTrue(session.unlocked.value)

        session.onAppBackgrounded()
        now += AppLockSession.RELOCK_AFTER_MS
        session.onAppForegrounded()
        assertFalse(session.unlocked.value)
    }

    @Test
    fun `voltar ao primeiro plano sem ter saido nao bloqueia`() {
        session.markUnlocked()
        now += 10 * AppLockSession.RELOCK_AFTER_MS
        session.onAppForegrounded()
        assertTrue(session.unlocked.value)
    }
}
