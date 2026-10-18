package pt.vcc.smartshopcart.core.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import pt.vcc.smartshopcart.core.database.entity.ExpenseCategoryEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseEntity
import pt.vcc.smartshopcart.core.database.entity.ExpenseItemEntity
import java.io.File

/**
 * Cria a base de dados numa versão antiga a partir do esquema exportado (`schemas/.../N.json`),
 * aplica as migrações e deixa o Room validar o esquema atual ao abrir (falha se divergir).
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    @After
    fun cleanUp() {
        context.deleteDatabase(DB_NAME)
    }

    @Test
    fun `migracao 1 para 3 preserva dados e cria tabelas Open Prices e de despesas`() = runTest {
        createVersion(1) { db ->
            db.insert(
                "products",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("id", "p1")
                    put("barcode", "0001234567890")
                    put("name", "Leite")
                    put("source", "MANUAL")
                    put("updatedAt", 1L)
                },
            )
        }

        val db = Room.databaseBuilder(context, ShoppingDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals("Leite", db.productDao().findByBarcode("0001234567890")?.name)
            assertTrue(db.externalPriceDao().getPrices("0001234567890").isEmpty())
            assertTrue(db.expenseDao().observeSummaries().first().isEmpty())
            assertEquals(3, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    @Test
    fun `migracao 2 para 3 preserva precos externos e permite gravar despesas`() = runTest {
        createVersion(2) { db ->
            db.insert(
                "external_price_sync",
                SQLiteDatabase.CONFLICT_FAIL,
                ContentValues().apply {
                    put("barcode", "5601234567890")
                    put("lastAttemptAt", 5L)
                    put("status", "EMPTY")
                    put("totalAvailable", 0)
                    put("incomplete", 0)
                },
            )
        }

        val db = Room.databaseBuilder(context, ShoppingDatabase::class.java, DB_NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(5L, db.externalPriceDao().getSync("5601234567890")?.lastAttemptAt)
            val dao = db.expenseDao()
            dao.insertCategoriesIgnoringExisting(listOf(ExpenseCategoryEntity("c", "Café", null, 0, true)))
            dao.upsertExpense(ExpenseEntity("e", null, 10, 85, null, 10, 10))
            dao.insertItems(listOf(ExpenseItemEntity("i", "e", null, "Café", 1, 85, 85, 0)))
            assertEquals(85L, dao.sumItems("e"))
            assertEquals(3, db.openHelper.readableDatabase.version)
        } finally {
            db.close()
        }
    }

    private fun createVersion(version: Int, seed: (SupportSQLiteDatabase) -> Unit) {
        val schema = Json.parseToJsonElement(schemaFile(version).readText()).jsonObject.getValue("database").jsonObject
        val statements = schema.getValue("entities").jsonArray.flatMap { entity ->
            val obj = entity.jsonObject
            val table = obj.getValue("tableName").jsonPrimitive.content
            val sqls = listOf(obj.getValue("createSql").jsonPrimitive.content) +
                (obj["indices"]?.jsonArray?.map { it.jsonObject.getValue("createSql").jsonPrimitive.content } ?: emptyList())
            sqls.map { it.replace("\${TABLE_NAME}", table) }
        } + schema.getValue("setupQueries").jsonArray.map { it.jsonPrimitive.content }

        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(DB_NAME)
                .callback(
                    object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) = statements.forEach(db::execSQL)
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                    },
                )
                .build(),
        )
        helper.writableDatabase.also(seed)
        helper.close()
    }

    private fun schemaFile(version: Int): File {
        val relative = "schemas/${ShoppingDatabase::class.java.name}/$version.json"
        return listOf(File(relative), File("app/$relative")).first { it.exists() }
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
