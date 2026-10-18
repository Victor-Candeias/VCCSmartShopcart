package pt.vcc.smartshopcart

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pt.vcc.smartshopcart.core.navigation.AppNavHost
import pt.vcc.smartshopcart.core.ui.theme.VCCSmartShopcartTheme
import pt.vcc.smartshopcart.domain.model.AppSettings
import pt.vcc.smartshopcart.domain.model.ThemeMode
import pt.vcc.smartshopcart.domain.repository.SettingsRepository
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            VCCSmartShopcartTheme(darkTheme = dark) {
                AppNavHost()
            }
        }
    }
}
