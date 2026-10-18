package pt.vcc.smartshopcart.domain.repository

import pt.vcc.smartshopcart.domain.model.PriceCapture

interface PriceCaptureRepository {
    suspend fun add(capture: PriceCapture)

    suspend fun getForItem(sessionItemId: String): List<PriceCapture>
}
