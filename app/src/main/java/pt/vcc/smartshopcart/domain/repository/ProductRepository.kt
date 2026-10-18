package pt.vcc.smartshopcart.domain.repository

import pt.vcc.smartshopcart.domain.model.Product
import pt.vcc.smartshopcart.domain.model.ProductPrice

/** Catálogo local de produtos (Room). O código de barras não é chave primária (D7). */
interface ProductRepository {
    suspend fun findByBarcode(barcode: String): Product?
    suspend fun getById(id: String): Product?
    suspend fun save(product: Product)

    /** Último preço observado para o código no supermercado indicado (`null` = sem supermercado) (D17). */
    suspend fun lastPrice(barcode: String, storeId: String?): ProductPrice?
}