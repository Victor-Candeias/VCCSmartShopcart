package pt.vcc.smartshopcart.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pt.vcc.smartshopcart.data.reports.ReportsRepositoryImpl
import pt.vcc.smartshopcart.data.repository.OpenPricesRepository
import pt.vcc.smartshopcart.data.repository.RoomExpenseRepository
import pt.vcc.smartshopcart.data.repository.RoomPriceCaptureRepository
import pt.vcc.smartshopcart.data.repository.RoomProductRepository
import pt.vcc.smartshopcart.data.repository.RoomShoppingListRepository
import pt.vcc.smartshopcart.data.repository.RoomShoppingSessionRepository
import pt.vcc.smartshopcart.data.repository.RoomStoreRepository
import pt.vcc.smartshopcart.data.repository.RoomTransactionRunner
import pt.vcc.smartshopcart.domain.repository.ExpenseRepository
import pt.vcc.smartshopcart.domain.repository.PriceCaptureRepository
import pt.vcc.smartshopcart.domain.repository.ProductRepository
import pt.vcc.smartshopcart.domain.repository.ReferencePriceRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingListRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.repository.StoreRepository
import pt.vcc.smartshopcart.domain.repository.TransactionRunner
import pt.vcc.smartshopcart.domain.reports.ReportsRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindSessionRepository(impl: RoomShoppingSessionRepository): ShoppingSessionRepository
    @Binds abstract fun bindStoreRepository(impl: RoomStoreRepository): StoreRepository
    @Binds abstract fun bindListRepository(impl: RoomShoppingListRepository): ShoppingListRepository
    @Binds abstract fun bindProductRepository(impl: RoomProductRepository): ProductRepository
    @Binds abstract fun bindPriceCaptureRepository(impl: RoomPriceCaptureRepository): PriceCaptureRepository
    @Binds abstract fun bindTransactionRunner(impl: RoomTransactionRunner): TransactionRunner
    @Binds abstract fun bindReferencePriceRepository(impl: OpenPricesRepository): ReferencePriceRepository
    @Binds abstract fun bindExpenseRepository(impl: RoomExpenseRepository): ExpenseRepository
    @Binds abstract fun bindReportsRepository(impl: ReportsRepositoryImpl): ReportsRepository
}
