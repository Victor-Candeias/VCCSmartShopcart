package pt.vcc.smartshopcart.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import pt.vcc.smartshopcart.core.database.dao.ExternalPriceDao
import pt.vcc.smartshopcart.core.database.dao.PriceCaptureDao
import pt.vcc.smartshopcart.core.database.dao.PriceHistoryDao
import pt.vcc.smartshopcart.core.database.dao.ProductDao
import pt.vcc.smartshopcart.core.database.dao.ShoppingListDao
import pt.vcc.smartshopcart.core.database.dao.ShoppingSessionDao
import pt.vcc.smartshopcart.core.database.dao.StoreDao
import pt.vcc.smartshopcart.core.database.entity.ExternalPriceEntity
import pt.vcc.smartshopcart.core.database.entity.ExternalPriceSyncEntity
import pt.vcc.smartshopcart.core.database.entity.PriceCaptureEntity
import pt.vcc.smartshopcart.core.database.entity.ProductEntity
import pt.vcc.smartshopcart.core.database.entity.ProductPriceHistoryEntity
import pt.vcc.smartshopcart.core.database.entity.SessionPlannedItemStateEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingListEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingListItemEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionEntity
import pt.vcc.smartshopcart.core.database.entity.ShoppingSessionItemEntity
import pt.vcc.smartshopcart.core.database.entity.StoreEntity

@Database(
    entities = [
        ShoppingListEntity::class,
        ShoppingListItemEntity::class,
        StoreEntity::class,
        ShoppingSessionEntity::class,
        ShoppingSessionItemEntity::class,
        ProductEntity::class,
        ProductPriceHistoryEntity::class,
        PriceCaptureEntity::class,
        SessionPlannedItemStateEntity::class,
        ExternalPriceEntity::class,
        ExternalPriceSyncEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class ShoppingDatabase : RoomDatabase() {
    abstract fun shoppingListDao(): ShoppingListDao
    abstract fun shoppingSessionDao(): ShoppingSessionDao
    abstract fun productDao(): ProductDao
    abstract fun storeDao(): StoreDao
    abstract fun priceHistoryDao(): PriceHistoryDao
    abstract fun priceCaptureDao(): PriceCaptureDao
    abstract fun externalPriceDao(): ExternalPriceDao

    companion object {
        const val NAME = "vccsmartshopcart.db"
    }
}
