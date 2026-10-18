package pt.vcc.smartshopcart.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import pt.vcc.smartshopcart.data.seed.AssetExpenseCatalogSeedSource
import pt.vcc.smartshopcart.data.seed.ExpenseCatalogSeedSource
import pt.vcc.smartshopcart.data.seed.ExpenseSeedVersionStore
import pt.vcc.smartshopcart.domain.model.expense.ExpenseEditorDraft
import pt.vcc.smartshopcart.domain.repository.ExpenseDraftStore
import javax.inject.Inject

/** Estado local do modo Despesas Rápidas guardado nas preferências da app. */
class ExpensePreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : ExpenseSeedVersionStore, ExpenseDraftStore {

    override suspend fun importedVersion(): Int = dataStore.data.first()[SEED_VERSION] ?: 0

    override suspend fun setImportedVersion(version: Int) {
        dataStore.edit { it[SEED_VERSION] = version }
    }

    /** Um rascunho ilegível (p. ex. de uma versão incompatível) é tratado como inexistente. */
    override val draft: Flow<ExpenseEditorDraft?> = dataStore.data.map { prefs ->
        prefs[DRAFT]?.let { runCatching { json.decodeFromString<ExpenseEditorDraft>(it) }.getOrNull() }
    }

    override suspend fun get(): ExpenseEditorDraft? = draft.first()

    override suspend fun save(draft: ExpenseEditorDraft) {
        val text = json.encodeToString(ExpenseEditorDraft.serializer(), draft)
        dataStore.edit { it[DRAFT] = text }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(DRAFT) }
    }

    private companion object {
        val SEED_VERSION = intPreferencesKey("expense_catalog_seed_version")
        val DRAFT = stringPreferencesKey("expense_draft")
        val json = Json { ignoreUnknownKeys = true }
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class ExpensePreferencesModule {
    @Binds
    abstract fun bindSeedVersionStore(impl: ExpensePreferences): ExpenseSeedVersionStore

    @Binds
    abstract fun bindDraftStore(impl: ExpensePreferences): ExpenseDraftStore

    @Binds
    abstract fun bindSeedSource(impl: AssetExpenseCatalogSeedSource): ExpenseCatalogSeedSource
}
