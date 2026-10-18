package pt.vcc.smartshopcart.domain.repository

/** Dados de catálogo obtidos remotamente; nunca inclui preços (§6.1). */
data class RemoteProduct(
    val barcode: String,
    val name: String?,
    val brand: String?,
    val quantityLabel: String?,
    val category: String?,
    val imageUrl: String?,
)

sealed interface RemoteProductResult {
    data class Found(val product: RemoteProduct) : RemoteProductResult
    data object NotFound : RemoteProductResult
    data object Failed : RemoteProductResult
}

fun interface RemoteProductCatalog {
    suspend fun lookup(barcode: String): RemoteProductResult
}
