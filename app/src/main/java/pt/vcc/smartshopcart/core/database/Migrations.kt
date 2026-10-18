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

/**
 * v2 → v3: tabelas do modo Despesas Rápidas (catálogo, estabelecimentos, despesas e itens).
 * Não altera dados existentes; o catálogo predefinido é importado depois pelo seeder.
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            """
            CREATE TABLE IF NOT EXISTS `expense_categories` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `icon` TEXT,
            `sortOrder` INTEGER NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))
            """,
            """
            CREATE TABLE IF NOT EXISTS `expense_products` (`id` TEXT NOT NULL, `categoryId` TEXT NOT NULL,
            `name` TEXT NOT NULL, `icon` TEXT, `defaultPriceCents` INTEGER, `isFavorite` INTEGER NOT NULL,
            `isActive` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL, PRIMARY KEY(`id`),
            FOREIGN KEY(`categoryId`) REFERENCES `expense_categories`(`id`) ON UPDATE NO ACTION ON DELETE NO ACTION )
            """,
            "CREATE INDEX IF NOT EXISTS `index_expense_products_categoryId_sortOrder` ON `expense_products` (`categoryId`, `sortOrder`)",
            """
            CREATE TABLE IF NOT EXISTS `establishments` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT,
            `isFavorite` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))
            """,
            "CREATE INDEX IF NOT EXISTS `index_establishments_name` ON `establishments` (`name`)",
            """
            CREATE TABLE IF NOT EXISTS `expenses` (`id` TEXT NOT NULL, `establishmentId` TEXT,
            `occurredAt` INTEGER NOT NULL, `totalCents` INTEGER NOT NULL, `notes` TEXT, `createdAt` INTEGER NOT NULL,
            `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`),
            FOREIGN KEY(`establishmentId`) REFERENCES `establishments`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )
            """,
            "CREATE INDEX IF NOT EXISTS `index_expenses_occurredAt` ON `expenses` (`occurredAt`)",
            "CREATE INDEX IF NOT EXISTS `index_expenses_establishmentId` ON `expenses` (`establishmentId`)",
            """
            CREATE TABLE IF NOT EXISTS `expense_items` (`id` TEXT NOT NULL, `expenseId` TEXT NOT NULL, `productId` TEXT,
            `nameSnapshot` TEXT NOT NULL, `quantity` INTEGER NOT NULL, `unitPriceCents` INTEGER NOT NULL,
            `totalCents` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`id`),
            FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE ,
            FOREIGN KEY(`productId`) REFERENCES `expense_products`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )
            """,
            "CREATE INDEX IF NOT EXISTS `index_expense_items_expenseId` ON `expense_items` (`expenseId`)",
            "CREATE INDEX IF NOT EXISTS `index_expense_items_productId` ON `expense_items` (`productId`)",
        ).forEach { db.execSQL(it.trimIndent()) }
    }
}

/**
 * v3 → v4: snapshots para relatórios (categoria nos itens de despesa; nome/categoria do produto nos
 * itens de sessões concluídas), preenchidos a partir do catálogo atual, e índice por data de conclusão.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        listOf(
            "ALTER TABLE `expense_items` ADD COLUMN `categoryIdSnapshot` TEXT",
            "ALTER TABLE `expense_items` ADD COLUMN `categoryNameSnapshot` TEXT",
            """
            UPDATE `expense_items` SET
                `categoryIdSnapshot` = (SELECT p.categoryId FROM expense_products p WHERE p.id = expense_items.productId),
                `categoryNameSnapshot` = (
                    SELECT c.name FROM expense_products p JOIN expense_categories c ON c.id = p.categoryId
                    WHERE p.id = expense_items.productId
                )
            WHERE `productId` IS NOT NULL
            """,
            "ALTER TABLE `shopping_session_items` ADD COLUMN `productNameSnapshot` TEXT",
            "ALTER TABLE `shopping_session_items` ADD COLUMN `categorySnapshot` TEXT",
            """
            UPDATE `shopping_session_items` SET
                `productNameSnapshot` = (SELECT p.name FROM products p WHERE p.id = shopping_session_items.productId),
                `categorySnapshot` = (SELECT p.category FROM products p WHERE p.id = shopping_session_items.productId)
            WHERE `productId` IS NOT NULL
                AND `sessionId` IN (SELECT id FROM shopping_sessions WHERE status = 'COMPLETED')
            """,
            "CREATE INDEX IF NOT EXISTS `index_shopping_sessions_status_completedAt` ON `shopping_sessions` (`status`, `completedAt`)",
        ).forEach { db.execSQL(it.trimIndent()) }
    }
}

val ALL_MIGRATIONS = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
