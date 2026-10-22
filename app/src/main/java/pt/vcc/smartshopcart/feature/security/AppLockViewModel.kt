package pt.vcc.smartshopcart.feature.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.security.AppLockSession
import pt.vcc.smartshopcart.domain.security.AppLockRepository
import pt.vcc.smartshopcart.domain.security.AppLockSettings
import pt.vcc.smartshopcart.domain.security.PinCheckResult
import pt.vcc.smartshopcart.domain.security.PinPolicy
import javax.inject.Inject

enum class LockGate { LOADING, LOCKED, UNLOCKED }

data class AppLockUiState(
    val gate: LockGate = LockGate.LOADING,
    val pinEnabled: Boolean = false,
    val pinLength: Int = PinPolicy.MAX_LENGTH,
    val enteredDigits: Int = 0,
    val biometricEnabled: Boolean = false,
    /** Pedido biométrico automático ainda por mostrar neste bloqueio. */
    val autoPromptBiometric: Boolean = false,
    /** Tentativas restantes após um PIN errado (null sem erro). */
    val wrongAttemptsLeft: Int? = null,
    val lockedOutUntil: Long = 0,
    val verifying: Boolean = false,
)

private data class LockEntry(
    val pin: String = "",
    val wrongAttemptsLeft: Int? = null,
    val verifying: Boolean = false,
    val autoPrompted: Boolean = false,
)

/** Portão de bloqueio da app (âmbito da Activity) e introdução do PIN no ecrã de bloqueio. */
@HiltViewModel
class AppLockViewModel @Inject constructor(
    private val repository: AppLockRepository,
    private val session: AppLockSession,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    private val entry = MutableStateFlow(LockEntry())
    private var settings = AppLockSettings()

    val state: StateFlow<AppLockUiState> = combine(repository.settings, session.unlocked, entry) { s, unlocked, e ->
        settings = s
        val gate = if (!s.pinEnabled || unlocked) LockGate.UNLOCKED else LockGate.LOCKED
        AppLockUiState(
            gate = gate,
            pinEnabled = s.pinEnabled,
            pinLength = s.pinLength.takeIf { it in PinPolicy.MIN_LENGTH..PinPolicy.MAX_LENGTH } ?: PinPolicy.MAX_LENGTH,
            enteredDigits = e.pin.length,
            biometricEnabled = s.biometricEnabled,
            autoPromptBiometric = gate == LockGate.LOCKED && s.biometricEnabled && !e.autoPrompted,
            wrongAttemptsLeft = e.wrongAttemptsLeft,
            lockedOutUntil = s.lockedOutUntil,
            verifying = e.verifying,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppLockUiState())

    init {
        // Cada novo bloqueio volta a pedir a biometria automaticamente e começa sem PIN introduzido.
        viewModelScope.launch {
            session.unlocked.collect { unlocked -> if (unlocked) entry.value = LockEntry() }
        }
    }

    fun onDigit(digit: Int) {
        val current = entry.value
        val ui = state.value
        if (ui.gate != LockGate.LOCKED || current.verifying || isLockedOut()) return
        if (current.pin.length >= ui.pinLength) return
        val pin = current.pin + digit.coerceIn(0, 9)
        entry.update { it.copy(pin = pin) }
        if (pin.length == ui.pinLength) verify(pin)
    }

    fun onBackspace() {
        entry.update { if (it.verifying) it else it.copy(pin = it.pin.dropLast(1)) }
    }

    fun onBiometricPromptShown() {
        entry.update { it.copy(autoPrompted = true) }
    }

    fun onBiometricSuccess() {
        session.markUnlocked()
    }

    private fun isLockedOut(): Boolean = timeProvider.now() < settings.lockedOutUntil

    private fun verify(pin: String) {
        entry.update { it.copy(verifying = true) }
        viewModelScope.launch {
            when (val result = repository.verifyPin(pin)) {
                PinCheckResult.Success -> {
                    entry.update { it.copy(pin = "", wrongAttemptsLeft = null, verifying = false) }
                    session.markUnlocked()
                }
                is PinCheckResult.Wrong -> entry.update {
                    it.copy(pin = "", wrongAttemptsLeft = result.attemptsLeft, verifying = false)
                }
                is PinCheckResult.LockedOut -> entry.update {
                    it.copy(pin = "", wrongAttemptsLeft = null, verifying = false)
                }
            }
        }
    }
}
