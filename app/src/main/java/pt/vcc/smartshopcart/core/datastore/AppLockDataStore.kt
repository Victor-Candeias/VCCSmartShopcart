package pt.vcc.smartshopcart.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.security.PinHasher
import pt.vcc.smartshopcart.domain.security.AppLockRepository
import pt.vcc.smartshopcart.domain.security.AppLockSettings
import pt.vcc.smartshopcart.domain.security.PinCheckResult
import pt.vcc.smartshopcart.domain.security.PinPolicy
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

/** DataStore dedicado ao bloqueio da app (separado das definições gerais). */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppLockPreferences

private val Context.appLockDataStore by preferencesDataStore(name = "app_lock")

class DataStoreAppLockRepository @Inject constructor(
    @param:AppLockPreferences private val dataStore: DataStore<Preferences>,
    private val hasher: PinHasher,
    private val timeProvider: TimeProvider,
) : AppLockRepository {

    override val settings: Flow<AppLockSettings> = dataStore.data.map { prefs ->
        val pinEnabled = prefs[PIN_HASH] != null
        AppLockSettings(
            pinEnabled = pinEnabled,
            pinLength = if (pinEnabled) prefs[PIN_LENGTH] ?: 0 else 0,
            biometricEnabled = pinEnabled && prefs[BIOMETRIC] == true,
            failedAttempts = prefs[FAILED_ATTEMPTS] ?: 0,
            lockedOutUntil = prefs[LOCKED_OUT_UNTIL] ?: 0,
        )
    }

    override suspend fun setPin(pin: String) {
        require(PinPolicy.isValid(pin)) { "PIN inválido" }
        val hash = withContext(Dispatchers.Default) { hasher.hash(pin) }
        dataStore.edit {
            it[PIN_HASH] = hash
            it[PIN_LENGTH] = pin.length
            it.remove(FAILED_ATTEMPTS)
            it.remove(LOCKED_OUT_UNTIL)
        }
    }

    override suspend fun verifyPin(pin: String): PinCheckResult {
        val prefs = dataStore.data.first()
        val hash = prefs[PIN_HASH] ?: return PinCheckResult.Success
        val now = timeProvider.now()
        val lockedUntil = prefs[LOCKED_OUT_UNTIL] ?: 0
        if (now < lockedUntil) return PinCheckResult.LockedOut(lockedUntil)

        val matches = withContext(Dispatchers.Default) { hasher.verify(pin, hash) }
        if (matches) {
            dataStore.edit {
                it.remove(FAILED_ATTEMPTS)
                it.remove(LOCKED_OUT_UNTIL)
            }
            return PinCheckResult.Success
        }

        var result: PinCheckResult = PinCheckResult.Wrong(PinPolicy.MAX_ATTEMPTS)
        dataStore.edit {
            val failed = (it[FAILED_ATTEMPTS] ?: 0) + 1
            it[FAILED_ATTEMPTS] = failed
            val lockout = PinPolicy.lockoutMillis(failed)
            result = if (lockout > 0) {
                val until = now + lockout
                it[LOCKED_OUT_UNTIL] = until
                PinCheckResult.LockedOut(until)
            } else {
                PinCheckResult.Wrong(PinPolicy.attemptsLeft(failed))
            }
        }
        return result
    }

    override suspend fun disablePin() {
        dataStore.edit { it.clear() }
    }

    override suspend fun setBiometricEnabled(enabled: Boolean) {
        dataStore.edit {
            if (it[PIN_HASH] != null) it[BIOMETRIC] = enabled
        }
    }

    private companion object {
        val PIN_HASH = stringPreferencesKey("pin_hash")
        val PIN_LENGTH = intPreferencesKey("pin_length")
        val BIOMETRIC = booleanPreferencesKey("biometric_enabled")
        val FAILED_ATTEMPTS = intPreferencesKey("failed_attempts")
        val LOCKED_OUT_UNTIL = longPreferencesKey("locked_out_until")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppLockModule {
    @Binds
    abstract fun bindAppLockRepository(impl: DataStoreAppLockRepository): AppLockRepository

    companion object {
        @Provides
        @Singleton
        @AppLockPreferences
        fun provideAppLockDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
            context.appLockDataStore
    }
}
