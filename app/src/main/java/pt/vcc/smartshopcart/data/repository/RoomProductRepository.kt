package pt.vcc.smartshopcart.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.data.mapper.toDomain
import pt.vcc.smartshopcart.data.mapper.toEntity
import pt.vcc.smartshopcart.domain.model.Product
import pt.vcc.smartshopcart.domain.model.ProductPrice
import pt.vcc.smartshopcart.domain.repository.ProductRepository
import javax.inject.Inject

class RoomProductRepository @Inject constructor(
    private val db: ShoppingDatabase,
) : ProductRepository {
    private val products = db.productDao()
    private val history = db.priceHistoryDao()

    override suspend fun findByBarcode(barcode: String): Product? = products.findByBarcode(barcode)?.toDomain()

    override suspend fun getById(id: String): Product? = products.getById(id)?.toDomain()

    override suspend fun save(product: Product) = products.upsert(product.toEntity())

    override suspend fun lastPrice(barcode: String, storeId: String?): ProductPrice? {
        val entry = history.lastForBarcode(barcode, storeId) ?: return null
        val storeName = entry.storeId?.let { db.storeDao().getById(it)?.name }
        return ProductPrice(entry.priceCents, entry.unit, storeName, entry.observedAt, entry.source)
    }

    override fun observePriceHistory(barcode: String): Flow<List<ProductPrice>> =
        history.observeForBarcode(barcode).map { rows ->
            rows.map { ProductPrice(it.price.priceCents, it.price.unit, it.storeName, it.price.observedAt, it.price.source) }
        }
}