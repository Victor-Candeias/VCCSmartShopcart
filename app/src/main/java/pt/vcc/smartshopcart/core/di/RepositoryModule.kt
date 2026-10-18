package pt.vcc.smartshopcart.core.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pt.vcc.smartshopcart.data.repository.RoomShoppingListRepository
import pt.vcc.smartshopcart.data.repository.RoomShoppingSessionRepository
import pt.vcc.smartshopcart.data.repository.RoomStoreRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingListRepository
import pt.vcc.smartshopcart.domain.repository.ShoppingSessionRepository
import pt.vcc.smartshopcart.domain.repository.StoreRepository

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindSessionRepository(impl: RoomShoppingSessionRepository): ShoppingSessionRepository
    @Binds abstract fun bindStoreRepository(impl: RoomStoreRepository): StoreRepository
    @Binds abstract fun bindListRepository(impl: RoomShoppingListRepository): ShoppingListRepository
}
