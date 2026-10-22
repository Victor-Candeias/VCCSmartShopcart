package pt.vcc.smartshopcart.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.core.security.BiometricAvailability
import pt.vcc.smartshopcart.core.security.Biometrics
import pt.vcc.smartshopcart.core.security.findFragmentActivity
import pt.vcc.smartshopcart.domain.model.AppSettings
import pt.vcc.smartshopcart.domain.model.ThemeMode
import pt.vcc.smartshopcart.domain.repository.SettingsRepository
import pt.vcc.smartshopcart.domain.security.AppLockRepository
import pt.vcc.smartshopcart.domain.security.AppLockSettings
import pt.vcc.smartshopcart.feature.security.PinSetupMode
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val appLockRepository: AppLockRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    val lockSettings: StateFlow<AppLockSettings> = appLockRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppLockSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setBudgetWarningPercent(percent: Int) {
        viewModelScope.launch { settingsRepository.setBudgetWarningPercent(percent) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch { appLockRepository.setBiometricEnabled(enabled) }
    }
}

private val budgetWarningOptions = listOf(70, 80, 90, 100)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit,
    onOpenPinSetup: (PinSetupMode) -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val lock by viewModel.lockSettings.collectAsStateWithLifecycle()
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val sectionModifier = Modifier.padding(horizontal = 16.dp)
        Text(stringResource(R.string.nav_settings), style = MaterialTheme.typography.headlineMedium, modifier = sectionModifier)

        Text(stringResource(R.string.settings_theme), style = MaterialTheme.typography.titleMedium, modifier = sectionModifier)
        FlowRow(sectionModifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ThemeMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.themeMode == mode,
                    onClick = { viewModel.setThemeMode(mode) },
                    label = {
                        Text(
                            stringResource(
                                when (mode) {
                                    ThemeMode.SYSTEM -> R.string.settings_theme_system
                                    ThemeMode.LIGHT -> R.string.settings_theme_light
                                    ThemeMode.DARK -> R.string.settings_theme_dark
                                },
                            ),
                        )
                    },
                )
            }
        }

        HorizontalDivider()
        Text(stringResource(R.string.settings_budget_warning), style = MaterialTheme.typography.titleMedium, modifier = sectionModifier)
        Text(stringResource(R.string.settings_budget_warning_text), style = MaterialTheme.typography.bodyMedium, modifier = sectionModifier)
        FlowRow(sectionModifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            budgetWarningOptions.forEach { percent ->
                FilterChip(
                    selected = settings.budgetWarningPercent == percent,
                    onClick = { viewModel.setBudgetWarningPercent(percent) },
                    label = { Text(stringResource(R.string.settings_percent, percent)) },
                )
            }
        }

        HorizontalDivider()
        SecuritySection(
            lock = lock,
            onOpenPinSetup = onOpenPinSetup,
            onBiometricChanged = viewModel::setBiometricEnabled,
            sectionModifier = sectionModifier,
        )

        HorizontalDivider()
        Text(stringResource(R.string.settings_privacy), style = MaterialTheme.typography.titleMedium, modifier = sectionModifier)
        Text(stringResource(R.string.settings_privacy_text), style = MaterialTheme.typography.bodyMedium, modifier = sectionModifier)

        HorizontalDivider()
        ListItem(
            headlineContent = { Text(stringResource(R.string.settings_about)) },
            supportingContent = { Text(stringResource(R.string.settings_about_summary)) },
            leadingContent = { Icon(Icons.Filled.Info, contentDescription = null) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.clickable(onClick = onOpenAbout),
        )
    }
}

@Composable
private fun SecuritySection(
    lock: AppLockSettings,
    onOpenPinSetup: (PinSetupMode) -> Unit,
    onBiometricChanged: (Boolean) -> Unit,
    sectionModifier: Modifier,
) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    var availability by remember { mutableStateOf(Biometrics.availability(context)) }
    // O utilizador pode registar biometria nas definições do Android e voltar à app.
    LifecycleResumeEffect(context) {
        availability = Biometrics.availability(context)
        onPauseOrDispose { }
    }
    val enableTitle = stringResource(R.string.biometric_enable_title)
    val enableSubtitle = stringResource(R.string.biometric_enable_subtitle)
    val cancel = stringResource(R.string.action_cancel)

    Text(stringResource(R.string.security_title), style = MaterialTheme.typography.titleMedium, modifier = sectionModifier)
    ListItem(
        headlineContent = { Text(stringResource(R.string.security_pin_lock)) },
        supportingContent = {
            Text(stringResource(if (lock.pinEnabled) R.string.security_pin_lock_on else R.string.security_pin_lock_off))
        },
        trailingContent = { Switch(checked = lock.pinEnabled, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = lock.pinEnabled, role = Role.Switch) {
            onOpenPinSetup(if (lock.pinEnabled) PinSetupMode.DISABLE else PinSetupMode.CREATE)
        },
    )
    if (lock.pinEnabled) {
        ListItem(
            headlineContent = { Text(stringResource(R.string.security_change_pin)) },
            trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
            modifier = Modifier.clickable { onOpenPinSetup(PinSetupMode.CHANGE) },
        )
    }

    val canEnable = lock.pinEnabled && availability == BiometricAvailability.AVAILABLE && activity != null
    val canToggle = lock.biometricEnabled || canEnable
    ListItem(
        headlineContent = { Text(stringResource(R.string.security_biometric)) },
        supportingContent = {
            Text(
                stringResource(
                    when {
                        !lock.pinEnabled -> R.string.security_biometric_needs_pin
                        availability == BiometricAvailability.NONE_ENROLLED -> R.string.security_biometric_none_enrolled
                        availability == BiometricAvailability.UNAVAILABLE -> R.string.security_biometric_unavailable
                        else -> R.string.security_biometric_available
                    },
                ),
            )
        },
        trailingContent = { Switch(checked = lock.biometricEnabled, onCheckedChange = null, enabled = canToggle) },
        modifier = Modifier.toggleable(value = lock.biometricEnabled, enabled = canToggle, role = Role.Switch) { enable ->
            if (!enable) {
                onBiometricChanged(false)
            } else if (canEnable && activity != null) {
                // Só ativa depois de uma autenticação biométrica bem-sucedida.
                Biometrics.authenticate(activity, enableTitle, enableSubtitle, cancel, onSuccess = { onBiometricChanged(true) })
            }
        },
    )
    if (lock.pinEnabled) {
        Text(
            stringResource(R.string.security_forgot_pin_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = sectionModifier,
        )
    }
}
