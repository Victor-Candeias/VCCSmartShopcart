package pt.vcc.smartshopcart.data.remote

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.SerializationException
import java.io.IOException
import pt.vcc.smartshopcart.domain.repository.RemoteProduct
import pt.vcc.smartshopcart.domain.repository.RemoteProductCatalog
import pt.vcc.smartshopcart.domain.repository.RemoteProductResult
import javax.inject.Inject

class OpenFoodFactsDataSource @Inject constructor(
    private val api: OpenFoodFactsApi,
) : RemoteProductCatalog {

    override suspend fun lookup(barcode: String): RemoteProductResult {
        repeat(MAX_ATTEMPTS - 1) {
            try {
                return fetch(barcode)
            } catch (e: CancellationException) {
                throw e
            } catch (_: IOException) {
                // Nova tentativa moderada (§6.2.7); a última falha é tratada abaixo.
            } catch (_: SerializationException) {
                return RemoteProductResult.Failed
            }
        }
        return try {
            fetch(barcode)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            RemoteProductResult.Failed
        }
    }

    private suspend fun fetch(barcode: String): RemoteProductResult {
        val response = api.product(barcode)
        if (response.code() == 404) return RemoteProductResult.NotFound
        if (!response.isSuccessful) return RemoteProductResult.Failed
        val body = response.body() ?: return RemoteProductResult.Failed
        val product = body.product ?: return RemoteProductResult.NotFound
        return RemoteProductResult.Found(
            RemoteProduct(
                barcode = barcode,
                name = listOf(product.productNamePt, product.productName, product.genericName).firstOrNull { !it.isNullOrBlank() }?.trim(),
                brand = product.brands?.split(',')?.firstOrNull()?.trim()?.ifEmpty { null },
                quantityLabel = product.quantity?.trim()?.ifEmpty { null },
                category = product.categories?.split(',')?.firstOrNull()?.trim()?.ifEmpty { null },
                imageUrl = product.imageFrontUrl?.trim()?.ifEmpty { null },
            ),
        )
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}
