package pt.vcc.smartshopcart.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import pt.vcc.smartshopcart.data.seed.AssetExpenseCatalogSeedSource
import pt.vcc.smartshopcart.data.seed.ExpenseCatalogSeedSource
import pt.vcc.smartshopcart.data.seed.ExpenseSeedVersionStore
import javax.inject.Inject

/** Estado local do modo Despesas Rápidas guardado nas preferências da app. */
class ExpensePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ExpenseSeedVersionStore {

    override suspend fun importedVersion(): Int = dataStore.data.first()[SEED_VERSION] ?: 0

    override suspend fun setImportedVersion(version: Int) {
        dataStore.edit { it[SEED_VERSION] = version }
    }

    private companion object {
        val SEED_VERSION = intPreferencesKey("expense_catalog_seed_version")
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExpensePreferencesModule {
    @Binds
    abstract fun bindSeedVersionStore(impl: ExpensePreferences): ExpenseSeedVersionStore

    @Binds
    abstract fun bindSeedSource(impl: AssetExpenseCatalogSeedSource): ExpenseCatalogSeedSource
}
