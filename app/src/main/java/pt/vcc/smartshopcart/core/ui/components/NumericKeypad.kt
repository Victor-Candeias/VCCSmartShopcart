package pt.vcc.smartshopcart.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import pt.vcc.smartshopcart.R

sealed interface KeypadKey {
    data class Digit(val value: Int) : KeypadKey
    data object Comma : KeypadKey
    data object Backspace : KeypadKey
}

/** Regras do teclado de preços: até 5 dígitos inteiros, uma vírgula e no máximo 2 decimais. */
object KeypadInput {
    private const val MAX_INTEGER_DIGITS = 5
    private const val MAX_DECIMALS = 2

    fun apply(current: String, key: KeypadKey): String = when (key) {
        KeypadKey.Backspace -> current.dropLast(1)
        KeypadKey.Comma -> when {
            ',' in current -> current
            current.isEmpty() -> "0,"
            else -> "$current,"
        }
        is KeypadKey.Digit -> {
            val comma = current.indexOf(',')
            when {
                comma >= 0 && current.length - comma - 1 >= MAX_DECIMALS -> current
                comma < 0 && current == "0" -> key.value.toString()
                comma < 0 && current.length >= MAX_INTEGER_DIGITS -> current
                else -> current + key.value
            }
        }
    }
}

@Composable
fun NumericKeypad(
    onKey: (KeypadKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rows = listOf(
        listOf(KeypadKey.Digit(1), KeypadKey.Digit(2), KeypadKey.Digit(3)),
        listOf(KeypadKey.Digit(4), KeypadKey.Digit(5), KeypadKey.Digit(6)),
        listOf(KeypadKey.Digit(7), KeypadKey.Digit(8), KeypadKey.Digit(9)),
        listOf(KeypadKey.Comma, KeypadKey.Digit(0), KeypadKey.Backspace),
    )
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    FilledTonalButton(
                        onClick = { onKey(key) },
                        modifier = Modifier.weight(1f).height(56.dp),
                    ) {
                        when (key) {
                            is KeypadKey.Digit -> Text(key.value.toString(), style = MaterialTheme.typography.headlineSmall)
                            KeypadKey.Comma -> Text(",", style = MaterialTheme.typography.headlineSmall)
                            KeypadKey.Backspace -> Icon(
                                painterResource(R.drawable.ic_backspace),
                                contentDescription = stringResource(R.string.keypad_backspace),
                            )
                        }
                    }
                }
            }
        }
    }
}
