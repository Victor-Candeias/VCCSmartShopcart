package pt.vcc.smartshopcart.core.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import pt.vcc.smartshopcart.core.common.IdProvider
import pt.vcc.smartshopcart.core.common.SystemTimeProvider
import pt.vcc.smartshopcart.core.common.TimeProvider
import pt.vcc.smartshopcart.core.common.UuidIdProvider
import pt.vcc.smartshopcart.core.database.ALL_MIGRATIONS
import pt.vcc.smartshopcart.core.database.ShoppingDatabase
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ShoppingDatabase =
        Room.databaseBuilder(context, ShoppingDatabase::class.java, ShoppingDatabase.NAME)
            .addMigrations(*ALL_MIGRATIONS)
            .build()

    @Provides fun shoppingListDao(db: ShoppingDatabase) = db.shoppingListDao()
    @Provides fun shoppingSessionDao(db: ShoppingDatabase) = db.shoppingSessionDao()
    @Provides fun productDao(db: ShoppingDatabase) = db.productDao()
    @Provides fun storeDao(db: ShoppingDatabase) = db.storeDao()
    @Provides fun priceHistoryDao(db: ShoppingDatabase) = db.priceHistoryDao()
    @Provides fun priceCaptureDao(db: ShoppingDatabase) = db.priceCaptureDao()
    @Provides fun externalPriceDao(db: ShoppingDatabase) = db.externalPriceDao()
    @Provides fun expenseDao(db: ShoppingDatabase) = db.expenseDao()

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CommonModule {
    @Binds abstract fun bindTimeProvider(impl: SystemTimeProvider): TimeProvider
    @Binds abstract fun bindIdProvider(impl: UuidIdProvider): IdProvider
}
