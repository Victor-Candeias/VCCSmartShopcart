package pt.vcc.smartshopcart.domain.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.domain.model.AppSettings
import pt.vcc.smartshopcart.domain.model.ThemeMode

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setBudgetWarningPercent(percent: Int)
}
