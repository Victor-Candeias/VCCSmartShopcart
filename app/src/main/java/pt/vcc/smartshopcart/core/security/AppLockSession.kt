package pt.vcc.smartshopcart.core.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pt.vcc.smartshopcart.core.common.TimeProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Estado de desbloqueio em memória (perde-se com o processo, pelo que a app arranca bloqueada).
 * Volta a bloquear quando a app regressa ao primeiro plano depois de [RELOCK_AFTER_MS] em segundo plano.
 */
@Singleton
class AppLockSession @Inject constructor(
    private val timeProvider: TimeProvider,
) {
    private val _unlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = _unlocked.asStateFlow()

    private var backgroundedAt: Long? = null

    fun markUnlocked() {
        backgroundedAt = null
        _unlocked.value = true
    }

    fun lock() {
        backgroundedAt = null
        _unlocked.value = false
    }

    fun onAppBackgrounded() {
        if (_unlocked.value) backgroundedAt = timeProvider.now()
    }

    fun onAppForegrounded() {
        val since = backgroundedAt ?: return
        backgroundedAt = null
        if (timeProvider.now() - since >= RELOCK_AFTER_MS) _unlocked.value = false
    }

    companion object {
        const val RELOCK_AFTER_MS = 30_000L
    }
}
