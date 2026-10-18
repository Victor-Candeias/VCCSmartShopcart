package pt.vcc.smartshopcart.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import pt.vcc.smartshopcart.core.database.entity.ExternalPriceEntity
import pt.vcc.smartshopcart.core.database.entity.ExternalPriceSyncEntity

@Dao
interface ExternalPriceDao {
    @Query("SELECT * FROM external_prices WHERE barcode = :barcode ORDER BY observedAt DESC, externalId DESC")
    fun observePrices(barcode: String): Flow<List<ExternalPriceEntity>>

    @Query("SELECT * FROM external_prices WHERE barcode = :barcode ORDER BY observedAt DESC, externalId DESC")
    suspend fun getPrices(barcode: String): List<ExternalPriceEntity>

    @Upsert
    suspend fun upsertAll(prices: List<ExternalPriceEntity>)

    @Query("DELETE FROM external_prices WHERE barcode = :barcode")
    suspend fun deletePrices(barcode: String)

    @Query("SELECT * FROM external_price_sync WHERE barcode = :barcode")
    fun observeSync(barcode: String): Flow<ExternalPriceSyncEntity?>

    @Query("SELECT * FROM external_price_sync WHERE barcode = :barcode")
    suspend fun getSync(barcode: String): ExternalPriceSyncEntity?

    @Upsert
    suspend fun upsertSync(sync: ExternalPriceSyncEntity)

    /** Substitui os preços de um EAN e grava o estado da consulta de forma consistente (OP10). */
    @Transaction
    suspend fun replacePrices(barcode: String, prices: List<ExternalPriceEntity>, sync: ExternalPriceSyncEntity) {
        deletePrices(barcode)
        upsertAll(prices)
        upsertSync(sync)
    }

    @Query("DELETE FROM external_prices WHERE fetchedAt < :before")
    suspend fun deletePricesFetchedBefore(before: Long)

    @Query("DELETE FROM external_price_sync WHERE lastAttemptAt < :before")
    suspend fun deleteSyncAttemptedBefore(before: Long)

    /** Expira EANs não consultados desde [before] (OP10). */
    @Transaction
    suspend fun purgeOlderThan(before: Long) {
        deletePricesFetchedBefore(before)
        deleteSyncAttemptedBefore(before)
    }
}
