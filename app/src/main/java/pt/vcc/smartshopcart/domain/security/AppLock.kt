package pt.vcc.smartshopcart.domain.security

import kotlinx.coroutines.flow.Flow

/** Regras do PIN de bloqueio e do limite de tentativas. */
object PinPolicy {
    const val MIN_LENGTH = 4
    const val MAX_LENGTH = 6

    /** Falhas seguidas que provocam um bloqueio temporário. */
    const val MAX_ATTEMPTS = 5
    const val BASE_LOCKOUT_MS = 30_000L
    const val MAX_LOCKOUT_MS = 10 * 60_000L

    fun isValid(pin: String): Boolean = pin.length in MIN_LENGTH..MAX_LENGTH && pin.all { it in '0'..'9' }

    /** Tentativas que restam antes do próximo bloqueio, dadas as falhas seguidas já registadas. */
    fun attemptsLeft(failedAttempts: Int): Int = MAX_ATTEMPTS - failedAttempts.coerceAtLeast(0) % MAX_ATTEMPTS

    /** Bloqueio a aplicar após [failedAttempts] falhas: 30 s × 2^(n-1) a cada 5 falhas (máx. 10 min), senão 0. */
    fun lockoutMillis(failedAttempts: Int): Long {
        if (failedAttempts <= 0 || failedAttempts % MAX_ATTEMPTS != 0) return 0
        val block = failedAttempts / MAX_ATTEMPTS - 1
        if (block >= 5) return MAX_LOCKOUT_MS
        return (BASE_LOCKOUT_MS shl block).coerceAtMost(MAX_LOCKOUT_MS)
    }
}

data class AppLockSettings(
    val pinEnabled: Boolean = false,
    /** Número de dígitos do PIN atual (0 sem PIN). */
    val pinLength: Int = 0,
    val biometricEnabled: Boolean = false,
    val failedAttempts: Int = 0,
    /** Epoch millis até ao qual a introdução do PIN está bloqueada (0 = sem bloqueio). */
    val lockedOutUntil: Long = 0,
)

sealed interface PinCheckResult {
    data object Success : PinCheckResult
    data class Wrong(val attemptsLeft: Int) : PinCheckResult
    data class LockedOut(val untilMillis: Long) : PinCheckResult
}

interface AppLockRepository {
    val settings: Flow<AppLockSettings>

    /** Define (ou substitui) o PIN; repõe as tentativas falhadas. */
    suspend fun setPin(pin: String)

    /** Verifica o PIN respeitando o limite de tentativas. Sem PIN definido devolve sempre [PinCheckResult.Success]. */
    suspend fun verifyPin(pin: String): PinCheckResult

    /** Remove o PIN e desativa a biometria. */
    suspend fun disablePin()

    /** Só tem efeito com PIN ativo. */
    suspend fun setBiometricEnabled(enabled: Boolean)
}
