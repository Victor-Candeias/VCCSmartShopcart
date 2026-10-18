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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import pt.vcc.smartshopcart.R
import pt.vcc.smartshopcart.domain.model.AppSettings
import pt.vcc.smartshopcart.domain.model.ThemeMode
import pt.vcc.smartshopcart.domain.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setBudgetWarningPercent(percent: Int) {
        viewModelScope.launch { settingsRepository.setBudgetWarningPercent(percent) }
    }
}

private val budgetWarningOptions = listOf(70, 80, 90, 100)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
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
