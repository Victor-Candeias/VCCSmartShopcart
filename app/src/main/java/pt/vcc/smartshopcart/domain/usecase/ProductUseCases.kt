package pt.vcc.smartshopcart.domain.usecase

import pt.vcc.smartshopcart.core.barcode.Barcodes
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.network.NetworkMonitor
import pt.vcc.smartshopcart.domain.repository.RemoteProductCatalog
import pt.vcc.smartshopcart.domain.repository.RemoteProductResult
import pt.vcc.smartshopcart.domain.model.MeasureUnit
import pt.vcc.smartshopcart.domain.model.PriceSource
import pt.vcc.smartshopcart.domain.model.Product
import pt.vcc.smartshopcart.domain.model.ProductPrice
import pt.vcc.smartshopcart.domain.model.ProductSource
import pt.vcc.smartshopcart.domain.model.SessionStatus
import pt.vcc.smartshopcart.domain.model.ShoppingSessionItem
import pt.vcc.smartshopcart.domain.repository.ProductRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingListRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import java.math.BigDecimal
import javax.inject.Inject

enum class ProductLookupStatus {
    /** Encontrado no catálogo local. */
    LOCAL,

    /** Obtido agora da Open Food Facts e guardado localmente. */
    REMOTE,

    /** A Open Food Facts não conhece o código (ou não tem nome). */
    NOT_FOUND,

    /** Sem rede: não foi feita consulta remota. */
    OFFLINE,

    /** Falha de rede, timeout, limite ou resposta inválida. */
    REMOTE_ERROR,

    /** Etiqueta interna da loja (prefixo 20–29): nunca consultada remotamente (D7). */
    INTERNAL_CODE,
}

sealed interface ResolveProductResult {
    data object InvalidBarcode : ResolveProductResult

    data class Resolved(
        val barcode: String,
        val product: Product?,
        val status: ProductLookupStatus,
        val suggestedPrice: ProductPrice?,
    ) : ResolveProductResult
}

/**
 * `ProductResolver` (§5.2, §6.2): Room primeiro; Open Food Facts só com rede e para códigos
 * públicos; qualquer falha remota é não bloqueante. Inclui a sugestão do último preço no
 * mesmo supermercado (D17).
 */
class ResolveProductUseCase @Inject constructor(
    private val products: ProductRepository,
    private val remote: RemoteProductCatalog,
    private val network: NetworkMonitor,
    private val ids: IdProvider,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(rawBarcode: String, storeId: String?): ResolveProductResult {
        val barcode = Barcodes.normalize(rawBarcode) ?: return ResolveProductResult.InvalidBarcode
        val suggestion = products.lastPrice(barcode, storeId)
        products.findByBarcode(barcode)?.let {
            return ResolveProductResult.Resolved(barcode, it, ProductLookupStatus.LOCAL, suggestion)
        }
        val (product, status) = when {
            Barcodes.isInternalStoreCode(barcode) -> null to ProductLookupStatus.INTERNAL_CODE
            !network.isOnline() -> null to ProductLookupStatus.OFFLINE
            else -> when (val result = remote.lookup(barcode)) {
                is RemoteProductResult.Found -> {
                    val name = result.product.name
                    if (name.isNullOrBlank()) {
                        null to ProductLookupStatus.NOT_FOUND
                    } else {
                        val saved = Product(
                            id = ids.newId(),
                            barcode = barcode,
                            name = name,
                            brand = result.product.brand,
                            quantityLabel = result.product.quantityLabel,
                            category = result.product.category,
                            imageUrl = result.product.imageUrl,
                            source = ProductSource.OPEN_FOOD_FACTS,
                            updatedAt = time.now(),
                        )
                        products.save(saved)
                        saved to ProductLookupStatus.REMOTE
                    }
                }
                RemoteProductResult.NotFound -> null to ProductLookupStatus.NOT_FOUND
                RemoteProductResult.Failed -> null to ProductLookupStatus.REMOTE_ERROR
            }
        }
        return ResolveProductResult.Resolved(barcode, product, status, suggestion)
    }
}

data class ScannedItemInput(
    val barcode: String,
    val productId: String?,
    val name: String?,
    val quantity: BigDecimal,
    val unit: MeasureUnit,
    val unitPriceCents: Long,
    val suggestedPriceCents: Long? = null,
    val shoppingListItemId: String? = null,
)

/**
 * Confirma um produto lido por código de barras: cria um produto manual quando o código não é
 * conhecido e há nome (§5.1 "Produto não encontrado"), liga o item planeado ao produto e
 * adiciona o item à sessão. A fonte é `HISTORY` só se o preço sugerido for aceite sem alteração.
 */
class AddScannedItemUseCase @Inject constructor(
    private val sessions: ShoppingSessionRepository,
    private val products: ProductRepository,
    private val lists: ShoppingListRepository,
    private val addItem: AddSessionItemUseCase,
    private val ids: IdProvider,
    private val time: TimeProvider,
) {
    suspend operator fun invoke(sessionId: String, input: ScannedItemInput): ShoppingSessionItem {
        if (sessions.getSession(sessionId)?.status != SessionStatus.ACTIVE) throw SessionNotActiveException()
        val name = input.name?.trim()?.takeIf { it.isNotEmpty() }
        val productId = input.productId ?: name?.let {
            products.findByBarcode(input.barcode)?.id ?: Product(
                id = ids.newId(),
                barcode = input.barcode,
                name = it,
                brand = null,
                quantityLabel = null,
                category = null,
                imageUrl = null,
                source = ProductSource.MANUAL,
                updatedAt = time.now(),
            ).also { product -> products.save(product) }.id
        }
        input.shoppingListItemId?.let { listItemId ->
            val listItem = lists.getItem(listItemId)
            if (listItem != null && listItem.productId == null && productId != null) {
                lists.upsertItem(listItem.copy(productId = productId))
            }
        }
        val source = if (input.suggestedPriceCents != null && input.suggestedPriceCents == input.unitPriceCents) {
            PriceSource.HISTORY
        } else {
            PriceSource.MANUAL
        }
        return addItem(
            sessionId,
            NewSessionItem(
                unitPriceCents = input.unitPriceCents,
                quantity = input.quantity,
                unit = input.unit,
                name = name,
                barcode = input.barcode,
                productId = productId,
                shoppingListItemId = input.shoppingListItemId,
                priceSource = source,
            ),
        )
    }
}
