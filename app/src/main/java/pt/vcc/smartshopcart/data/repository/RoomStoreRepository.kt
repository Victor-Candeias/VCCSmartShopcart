package pt.vcc.smartshopcart.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.core.database.entity.StoreEntity
import pt.vcc.smartshopcart.data.mapper.toDomain
import pt.vcc.smartshopcart.domain.model.Store
import pt.vcc.smartshopcart.domain.repository.StoreRepository
import javax.inject.Inject

class RoomStoreRepository @Inject constructor(
    private val db: ShoppingDatabase,
    private val ids: IdProvider,
    private val time: TimeProvider,
) : StoreRepository {
    private val dao = db.storeDao()

    override fun observeStores(): Flow<List<Store>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getOrCreate(name: String): Store = db.withTransaction {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Nome vazio" }
        dao.findByName(trimmed)?.toDomain()
            ?: StoreEntity(ids.newId(), trimmed, time.now()).also { dao.insert(it) }.toDomain()
    }

    override suspend fun getById(id: String): Store? = dao.getById(id)?.toDomain()
}
