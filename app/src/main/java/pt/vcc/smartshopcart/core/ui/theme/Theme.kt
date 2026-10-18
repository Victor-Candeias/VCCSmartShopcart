package pt.vcc.smartshopcart.core.ui.theme

import android.os.Build
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Green = Color(0xFF1B6E3A)
private val GreenLight = Color(0xFF8BD8A0)

private val LightColors = lightColorScheme(primary = Green, secondary = Color(0xFF4F6354))
private val DarkColors = darkColorScheme(primary = GreenLight, secondary = Color(0xFFB6CCB9))

@Composable
fun VCCSmartShopcartTheme(
    darkTheme: Boolean,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
