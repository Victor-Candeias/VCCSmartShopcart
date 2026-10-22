package pt.vcc.smartshopcart

import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import pt.vcc.smartshopcart.core.navigation.AppNavHost
import pt.vcc.smartshopcart.core.security.AppLockSession
import pt.vcc.smartshopcart.core.ui.theme.VCCSmartShopcartTheme
import pt.vcc.smartshopcart.domain.model.AppSettings
import pt.vcc.smartshopcart.domain.model.ThemeMode
import pt.vcc.smartshopcart.domain.repository.SettingsRepository
import pt.vcc.smartshopcart.feature.security.AppLockGate
import pt.vcc.smartshopcart.feature.security.AppLockViewModel
import javax.inject.Inject

/** `FragmentActivity` porque o `BiometricPrompt` do androidx precisa de um `FragmentManager`. */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject lateinit var settingsRepository: SettingsRepository

    @Inject lateinit var appLockSession: AppLockSession

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by settingsRepository.settings.collectAsStateWithLifecycle(AppSettings())
            val lockViewModel: AppLockViewModel = hiltViewModel()
            val lockState by lockViewModel.state.collectAsStateWithLifecycle()
            LaunchedEffect(lockState.pinEnabled) {
                // Com PIN, a miniatura dos recentes não mostra o conteúdo da app.
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setRecentsScreenshotEnabled(!lockState.pinEnabled)
                }
            }
            val dark = when (settings.themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            VCCSmartShopcartTheme(darkTheme = dark) {
                AppLockGate(lockViewModel) {
                    AppNavHost()
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        appLockSession.onAppForegrounded()
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) appLockSession.onAppBackgrounded()
    }
}
