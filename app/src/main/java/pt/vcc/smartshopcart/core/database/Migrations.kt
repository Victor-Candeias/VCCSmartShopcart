package pt.vcc.smartshopcart.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** v1 → v2: tabelas de preços de referência Open Prices (OP9). Não altera dados existentes. */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `external_prices` (`externalId` INTEGER NOT NULL, `barcode` TEXT NOT NULL,
            `priceCents` INTEGER NOT NULL, `currency` TEXT NOT NULL, `storeName` TEXT, `storeCity` TEXT,
            `storeCountryCode` TEXT NOT NULL, `locationId` INTEGER, `observedAt` TEXT NOT NULL,
            `isDiscounted` INTEGER NOT NULL, `discountType` TEXT, `priceWithoutDiscountCents` INTEGER,
            `basis` TEXT NOT NULL, `fetchedAt` INTEGER NOT NULL, PRIMARY KEY(`externalId`))
            """.trimIndent(),
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_external_prices_barcode_observedAt` ON `external_prices` (`barcode`, `observedAt`)",
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `external_price_sync` (`barcode` TEXT NOT NULL, `lastAttemptAt` INTEGER NOT NULL,
            `lastSuccessAt` INTEGER, `status` TEXT NOT NULL, `totalAvailable` INTEGER NOT NULL,
            `incomplete` INTEGER NOT NULL, `retryAfter` INTEGER, PRIMARY KEY(`barcode`))
            """.trimIndent(),
        )
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2)
