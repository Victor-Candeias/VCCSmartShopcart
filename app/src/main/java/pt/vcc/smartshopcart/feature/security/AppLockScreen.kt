package pt.vcc.smartshopcart.feature.security

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.security.BiometricAvailability
import pt.vcc.smartshopcart.core.security.Biometrics
import pt.vcc.smartshopcart.core.security.findFragmentActivity

/**
 * Mostra [content] e, quando a app está bloqueada, sobrepõe o ecrã de desbloqueio. O conteúdo continua
 * composto (o estado da navegação mantém-se), mas fica sem semântica de acessibilidade enquanto bloqueado.
 */
@Composable
fun AppLockGate(viewModel: AppLockViewModel, content: @Composable () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val locked = state.gate != LockGate.UNLOCKED
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(locked) {
        if (locked) {
            focusManager.clearFocus(force = true)
            keyboard?.hide()
        }
    }
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)) { content() }
        when (state.gate) {
            LockGate.LOADING -> Surface(Modifier.fillMaxSize()) {}
            LockGate.LOCKED -> AppLockScreen(
                state = state,
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                onBiometricPromptShown = viewModel::onBiometricPromptShown,
                onBiometricSuccess = viewModel::onBiometricSuccess,
            )
            LockGate.UNLOCKED -> Unit
        }
    }
}

@Composable
private fun AppLockScreen(
    state: AppLockUiState,
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    onBiometricPromptShown: () -> Unit,
    onBiometricSuccess: () -> Unit,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val biometricAvailable = remember(state.biometricEnabled) {
        state.biometricEnabled && Biometrics.availability(context) == BiometricAvailability.AVAILABLE
    }
    val canUseBiometric = biometricAvailable && activity != null
    val title = stringResource(R.string.biometric_unlock_title)
    val usePin = stringResource(R.string.biometric_use_pin)
    val showBiometricPrompt = {
        activity?.let { Biometrics.authenticate(it, title, null, usePin, onSuccess = onBiometricSuccess) }
    }

    // "Voltar" não pode chegar à navegação por baixo: envia a app para segundo plano.
    BackHandler { activity?.moveTaskToBack(true) }

    LaunchedEffect(state.autoPromptBiometric, canUseBiometric) {
        if (state.autoPromptBiometric && canUseBiometric) {
            onBiometricPromptShown()
            showBiometricPrompt()
        }
    }

    val secondsLeft = rememberSecondsUntil(state.lockedOutUntil)
    val lockedOut = secondsLeft > 0

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
            Text(
                stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.semantics { heading() },
            )
            PinDots(entered = state.enteredDigits, total = state.pinLength, knownLength = true)
            PinMessage(
                text = when {
                    lockedOut -> stringResource(R.string.lock_locked_out, secondsLeft)
                    state.wrongAttemptsLeft != null ->
                        pluralStringResource(R.plurals.lock_wrong_pin, state.wrongAttemptsLeft, state.wrongAttemptsLeft)
                    else -> null
                },
            )
            PinPad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                enabled = !lockedOut && !state.verifying,
                modifier = Modifier.fillMaxWidth(),
                leading = if (canUseBiometric) {
                    { BiometricKey(onClick = { showBiometricPrompt() }) }
                } else {
                    null
                },
            )
        }
    }
}

/** Mensagem de erro com altura reservada para o teclado não saltar. */
@Composable
fun PinMessage(text: String?, modifier: Modifier = Modifier) {
    Box(modifier.heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
        if (text != null) {
            Text(
                text,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
            )
        }
    }
}
