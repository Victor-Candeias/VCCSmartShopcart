package pt.vcc.smartshopcart.data.repository

import kotlinx.serialization.json.Json
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import pt.vcc.smartshopcart.core.database.entity.PriceCaptureEntity
import pt.vcc.smartshopcart.domain.model.PriceCandidate
import pt.vcc.smartshopcart.domain.model.PriceCapture
import pt.vcc.smartshopcart.domain.repository.PriceCaptureRepository
import javax.inject.Inject

/** Guarda os candidatos OCR em JSON (D8). */
class RoomPriceCaptureRepository @Inject constructor(db: ShoppingDatabase) : PriceCaptureRepository {
    private val dao = db.priceCaptureDao()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun add(capture: PriceCapture) = dao.insert(
        PriceCaptureEntity(
            id = capture.id,
            sessionItemId = capture.sessionItemId,
            rawText = capture.rawText,
            candidatesJson = json.encodeToString(capture.candidates),
            detectedPriceCents = capture.detectedPriceCents,
            confirmedPriceCents = capture.confirmedPriceCents,
            status = capture.status,
            capturedAt = capture.capturedAt,
        ),
    )

    override suspend fun getForItem(sessionItemId: String): List<PriceCapture> =
        dao.getForItem(sessionItemId).map { entity ->
            PriceCapture(
                id = entity.id,
                sessionItemId = entity.sessionItemId,
                rawText = entity.rawText,
                candidates = json.decodeFromString<List<PriceCandidate>>(entity.candidatesJson),
                detectedPriceCents = entity.detectedPriceCents,
                confirmedPriceCents = entity.confirmedPriceCents,
                status = entity.status,
                capturedAt = entity.capturedAt,
            )
        }
}
