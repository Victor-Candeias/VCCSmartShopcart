package pt.vcc.smartshopcart.data.seed

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import pt.vcc.smartshopcart.core.di.IoDispatcher
import pt.vcc.smartshopcart.domain.model.expense.ExpenseCategory
import pt.vcc.smartshopcart.domain.model.expense.ExpenseProduct
import pt.vcc.smartshopcart.domain.repository.ExpenseRepository
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class ExpenseCatalogSeed(
    val version: Int,
    val categories: List<SeedCategory>,
    val products: List<SeedProduct>,
) {
    @Serializable
    data class SeedCategory(val id: String, val name: String, val icon: String? = null)

    @Serializable
    data class SeedProduct(
        val id: String,
        val categoryId: String,
        val name: String,
        val icon: String? = null,
        val defaultPriceCents: Long? = null,
        val favorite: Boolean = false,
    )

    fun toCategories(): List<ExpenseCategory> =
        categories.mapIndexed { index, c -> ExpenseCategory(c.id, c.name, c.icon, sortOrder = index, isActive = true) }

    /** `sortOrder` sequencial dentro de cada categoria, pela ordem do ficheiro. */
    fun toProducts(): List<ExpenseProduct> {
        val nextOrder = mutableMapOf<String, Int>()
        return products.map { p ->
            val order = nextOrder.getOrDefault(p.categoryId, 0)
            nextOrder[p.categoryId] = order + 1
            ExpenseProduct(
                id = p.id,
                categoryId = p.categoryId,
                name = p.name,
                icon = p.icon,
                defaultPriceCents = p.defaultPriceCents,
                isFavorite = p.favorite,
                isActive = true,
                sortOrder = order,
            )
        }
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): ExpenseCatalogSeed {
            val seed = json.decodeFromString<ExpenseCatalogSeed>(text)
            val categoryIds = seed.categories.map { it.id }
            require(categoryIds.toSet().size == categoryIds.size) { "Categorias duplicadas no seed" }
            require(seed.products.map { it.id }.toSet().size == seed.products.size) { "Produtos duplicados no seed" }
            require(seed.products.all { it.categoryId in categoryIds }) { "Produto com categoria inexistente no seed" }
            require(seed.products.all { (it.defaultPriceCents ?: 0) >= 0 }) { "Preço negativo no seed" }
            return seed
        }
    }
}

/** Origem do texto JSON do catálogo predefinido. */
fun interface ExpenseCatalogSeedSource {
    fun read(): String
}

class AssetExpenseCatalogSeedSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : ExpenseCatalogSeedSource {
    override fun read(): String = context.assets.open(ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }

    private companion object {
        const val ASSET = "expense_catalog.json"
    }
}

/** Última versão do seed já importada (0 = nunca). */
interface ExpenseSeedVersionStore {
    suspend fun importedVersion(): Int
    suspend fun setImportedVersion(version: Int)
}

/**
 * Importa o catálogo predefinido quando a versão do seed é superior à já importada. A inserção
 * ignora IDs existentes, por isso nunca duplica nem sobrescreve alterações do utilizador
 * (nomes, preços, ordem, favoritos, ocultação) e não recria produtos personalizados.
 */
@Singleton
class ExpenseCatalogSeeder @Inject constructor(
    private val source: ExpenseCatalogSeedSource,
    private val versions: ExpenseSeedVersionStore,
    private val repository: ExpenseRepository,
    @IoDispatcher private val io: CoroutineDispatcher,
) {
    private val mutex = Mutex()

    /** Devolve `true` quando houve importação. */
    suspend fun seedIfNeeded(): Boolean = mutex.withLock {
        withContext(io) {
            val seed = ExpenseCatalogSeed.parse(source.read())
            if (versions.importedVersion() >= seed.version) return@withContext false
            repository.importCatalog(seed.toCategories(), seed.toProducts())
            versions.setImportedVersion(seed.version)
            true
        }
    }
}
