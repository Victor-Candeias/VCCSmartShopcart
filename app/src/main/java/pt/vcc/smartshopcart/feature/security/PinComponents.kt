package pt.vcc.smartshopcart.feature.security

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import pt.vcc.smartshopcart.R

/** Pontos do PIN: [total] posições, [entered] preenchidas. Sem [knownLength] não anuncia o total. */
@Composable
fun PinDots(entered: Int, total: Int, knownLength: Boolean, modifier: Modifier = Modifier) {
    val description = if (knownLength) {
        stringResource(R.string.pin_dots_description, entered, total)
    } else {
        pluralStringResource(R.plurals.pin_digits_entered, entered, entered)
    }
    Row(
        modifier.clearAndSetSemantics {
            contentDescription = description
            liveRegion = LiveRegionMode.Polite
        },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        repeat(total) { index ->
            val filled = index < entered
            val color = MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .size(16.dp)
                    .then(if (filled) Modifier.background(color, CircleShape) else Modifier.border(2.dp, color, CircleShape)),
            )
        }
    }
}

/** Teclado numérico do PIN; [leading] ocupa a posição à esquerda do 0 (p. ex. biometria). */
@Composable
fun PinPad(
    onDigit: (Int) -> Unit,
    onBackspace: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    Column(modifier.widthIn(max = 360.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(listOf(1, 2, 3), listOf(4, 5, 6), listOf(7, 8, 9)).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { digit -> DigitKey(digit, enabled, onDigit, Modifier.weight(1f)) }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f).height(KEY_HEIGHT), contentAlignment = Alignment.Center) { leading?.invoke() }
            DigitKey(0, enabled, onDigit, Modifier.weight(1f))
            Box(Modifier.weight(1f).height(KEY_HEIGHT), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBackspace, enabled = enabled) {
                    Icon(
                        painterResource(R.drawable.ic_backspace),
                        contentDescription = stringResource(R.string.keypad_backspace),
                    )
                }
            }
        }
    }
}

@Composable
private fun DigitKey(digit: Int, enabled: Boolean, onDigit: (Int) -> Unit, modifier: Modifier) {
    FilledTonalButton(onClick = { onDigit(digit) }, enabled = enabled, modifier = modifier.height(KEY_HEIGHT)) {
        Text(digit.toString(), style = MaterialTheme.typography.headlineSmall)
    }
}

/** Segundos que faltam até [untilMillis], atualizados a cada segundo (0 quando já passou). */
@Composable
fun rememberSecondsUntil(untilMillis: Long): Int {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(untilMillis) {
        now = System.currentTimeMillis()
        while (now < untilMillis) {
            delay(1_000)
            now = System.currentTimeMillis()
        }
    }
    return if (untilMillis <= now) 0 else ((untilMillis - now + 999) / 1_000).toInt()
}

@Composable
fun BiometricKey(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painterResource(R.drawable.ic_fingerprint),
            contentDescription = stringResource(R.string.lock_use_biometric),
            modifier = Modifier.size(32.dp),
        )
    }
}

private val KEY_HEIGHT = 64.dp
