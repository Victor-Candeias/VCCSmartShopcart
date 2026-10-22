package pt.vcc.smartshopcart.feature.security

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import pt.vcc.smartshopcart.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinSetupScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: PinSetupViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.finished) { if (state.finished) onFinished() }

    val secondsLeft = rememberSecondsUntil(state.lockedOutUntil)
    val lockedOut = state.step == PinSetupStep.CURRENT && secondsLeft > 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when (state.mode) {
                                PinSetupMode.CREATE -> R.string.pin_setup_create_title
                                PinSetupMode.CHANGE -> R.string.pin_setup_change_title
                                PinSetupMode.DISABLE -> R.string.pin_setup_disable_title
                            },
                        ),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Text(
                stringResource(
                    when (state.step) {
                        PinSetupStep.CURRENT -> R.string.pin_step_current
                        PinSetupStep.NEW -> R.string.pin_step_new
                        PinSetupStep.CONFIRM -> R.string.pin_step_confirm
                    },
                ),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            PinDots(
                entered = state.enteredDigits,
                total = state.dots,
                knownLength = state.step != PinSetupStep.NEW,
            )
            val attemptsLeft = state.attemptsLeft
            PinMessage(
                text = when {
                    lockedOut -> stringResource(R.string.lock_locked_out, secondsLeft)
                    state.error == PinSetupError.MISMATCH -> stringResource(R.string.pin_error_mismatch)
                    state.error == PinSetupError.WRONG_PIN && attemptsLeft != null ->
                        pluralStringResource(R.plurals.lock_wrong_pin, attemptsLeft, attemptsLeft)
                    else -> null
                },
            )
            PinPad(
                onDigit = viewModel::onDigit,
                onBackspace = viewModel::onBackspace,
                enabled = !lockedOut && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
            if (state.step == PinSetupStep.NEW) {
                Button(
                    onClick = viewModel::onContinue,
                    enabled = state.canContinue,
                    modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.action_continue))
                }
            }
        }
    }
}
