package pt.vcc.smartshopcart.domain.repository

import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.domain.model.Store

interface StoreRepository {
    fun observeStores(): Flow<List<Store>>

    /** Devolve o supermercado com o mesmo nome (sem distinguir maiúsculas) ou cria um novo. */
    suspend fun getOrCreate(name: String): Store

    suspend fun getById(id: String): Store?
}
