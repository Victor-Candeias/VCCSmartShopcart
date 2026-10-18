package pt.vcc.smartshopcart.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.smartshopcart.domain.model.AppSettings
import pt.vcc.smartshopcart.domain.model.ThemeMode
import pt.vcc.smartshopcart.domain.repository.SettingsRepository
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    override val settings: Flow<AppSettings> = dataStore.data.map { prefs ->
        AppSettings(
            themeMode = prefs[THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            budgetWarningPercent = prefs[BUDGET_WARNING] ?: AppSettings.DEFAULT_BUDGET_WARNING_PERCENT,
        )
    }

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME] = mode.name }
    }

    override suspend fun setBudgetWarningPercent(percent: Int) {
        dataStore.edit { it[BUDGET_WARNING] = percent.coerceIn(50, 100) }
    }

    private companion object {
        val THEME = stringPreferencesKey("theme_mode")
        val BUDGET_WARNING = intPreferencesKey("budget_warning_percent")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class SettingsModule {
    @Binds
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    companion object {
        @Provides
        @Singleton
        fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore
    }
}
