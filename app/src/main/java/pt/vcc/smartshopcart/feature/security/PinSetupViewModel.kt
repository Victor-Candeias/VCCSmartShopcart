package pt.vcc.smartshopcart.feature.security

import androidx.lifecycle.SavedStateHandle
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

enum class PinSetupMode { CREATE, CHANGE, DISABLE }

enum class PinSetupStep { CURRENT, NEW, CONFIRM }

enum class PinSetupError { WRONG_PIN, MISMATCH }

data class PinSetupUiState(
    val mode: PinSetupMode = PinSetupMode.CREATE,
    val step: PinSetupStep = PinSetupStep.NEW,
    val enteredDigits: Int = 0,
    /** Número de pontos a mostrar: comprimento esperado (PIN atual/confirmação) ou o máximo ao criar. */
    val dots: Int = PinPolicy.MAX_LENGTH,
    val canContinue: Boolean = false,
    val error: PinSetupError? = null,
    val attemptsLeft: Int? = null,
    val lockedOutUntil: Long = 0,
    val busy: Boolean = false,
    val finished: Boolean = false,
)

private data class PinSetupEntry(
    val step: PinSetupStep,
    val pin: String = "",
    val newPin: String = "",
    val error: PinSetupError? = null,
    val attemptsLeft: Int? = null,
    val busy: Boolean = false,
    val finished: Boolean = false,
)

/** Criar (novo → confirmar), alterar (atual → novo → confirmar) ou desativar (atual) o PIN. */
@HiltViewModel
class PinSetupViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: AppLockRepository,
    private val session: AppLockSession,
    private val timeProvider: TimeProvider,
) : ViewModel() {

    val mode: PinSetupMode = savedStateHandle.get<String>(MODE_KEY)
        ?.let { runCatching { PinSetupMode.valueOf(it) }.getOrNull() } ?: PinSetupMode.CREATE

    private val entry = MutableStateFlow(
        PinSetupEntry(step = if (mode == PinSetupMode.CREATE) PinSetupStep.NEW else PinSetupStep.CURRENT),
    )
    private var settings = AppLockSettings()

    val state: StateFlow<PinSetupUiState> = combine(repository.settings, entry) { s, e ->
        settings = s
        PinSetupUiState(
            mode = mode,
            step = e.step,
            enteredDigits = e.pin.length,
            dots = expectedLength(e, s) ?: PinPolicy.MAX_LENGTH,
            canContinue = e.step == PinSetupStep.NEW && PinPolicy.isValid(e.pin) && !e.busy,
            error = e.error,
            attemptsLeft = e.attemptsLeft,
            lockedOutUntil = s.lockedOutUntil,
            busy = e.busy,
            finished = e.finished,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, PinSetupUiState(mode = mode, step = entry.value.step))

    fun onDigit(digit: Int) {
        val current = entry.value
        if (current.busy || current.finished) return
        if (current.step == PinSetupStep.CURRENT && timeProvider.now() < settings.lockedOutUntil) return
        val max = expectedLength(current, settings) ?: PinPolicy.MAX_LENGTH
        if (current.pin.length >= max) return
        val pin = current.pin + digit.coerceIn(0, 9)
        entry.update { it.copy(pin = pin) }
        if (pin.length < max) return
        when (current.step) {
            PinSetupStep.CURRENT -> verifyCurrent(pin)
            PinSetupStep.CONFIRM -> confirm(pin, current.newPin)
            PinSetupStep.NEW -> Unit
        }
    }

    fun onBackspace() {
        entry.update { if (it.busy) it else it.copy(pin = it.pin.dropLast(1)) }
    }

    fun onContinue() {
        val current = entry.value
        if (current.step != PinSetupStep.NEW || !PinPolicy.isValid(current.pin) || current.busy) return
        entry.update { it.copy(step = PinSetupStep.CONFIRM, newPin = it.pin, pin = "", error = null) }
    }

    private fun expectedLength(e: PinSetupEntry, s: AppLockSettings): Int? = when (e.step) {
        PinSetupStep.CURRENT -> s.pinLength.takeIf { it in PinPolicy.MIN_LENGTH..PinPolicy.MAX_LENGTH }
        PinSetupStep.CONFIRM -> e.newPin.length
        PinSetupStep.NEW -> null
    }

    private fun verifyCurrent(pin: String) {
        entry.update { it.copy(busy = true) }
        viewModelScope.launch {
            when (val result = repository.verifyPin(pin)) {
                PinCheckResult.Success -> if (mode == PinSetupMode.DISABLE) {
                    repository.disablePin()
                    entry.update { it.copy(pin = "", busy = false, finished = true) }
                } else {
                    entry.update {
                        it.copy(step = PinSetupStep.NEW, pin = "", error = null, attemptsLeft = null, busy = false)
                    }
                }
                is PinCheckResult.Wrong -> entry.update {
                    it.copy(pin = "", error = PinSetupError.WRONG_PIN, attemptsLeft = result.attemptsLeft, busy = false)
                }
                is PinCheckResult.LockedOut -> entry.update {
                    it.copy(pin = "", error = null, attemptsLeft = null, busy = false)
                }
            }
        }
    }

    private fun confirm(pin: String, newPin: String) {
        if (pin != newPin) {
            entry.update { it.copy(step = PinSetupStep.NEW, pin = "", newPin = "", error = PinSetupError.MISMATCH) }
            return
        }
        entry.update { it.copy(busy = true) }
        viewModelScope.launch {
            // Desbloqueia antes de gravar para que ativar o PIN não mostre o ecrã de bloqueio.
            session.markUnlocked()
            repository.setPin(newPin)
            entry.update { it.copy(pin = "", newPin = "", busy = false, finished = true) }
        }
    }

    companion object {
        const val MODE_KEY = "mode"
    }
}
