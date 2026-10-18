package pt.vcc.smartshopcart.core.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import pt.vcc.smartshopcart.BuildConfig
import pt.vcc.smartshopcart.core.network.ConnectivityNetworkMonitor
import pt.vcc.smartshopcart.core.network.NetworkMonitor
import pt.vcc.smartshopcart.data.remote.OpenFoodFactsApi
import pt.vcc.smartshopcart.data.remote.OpenFoodFactsClient
import pt.vcc.smartshopcart.data.remote.OpenFoodFactsDataSource
import pt.vcc.smartshopcart.data.remote.openprices.OpenPricesApi
import pt.vcc.smartshopcart.data.remote.openprices.OpenPricesClient
import pt.vcc.smartshopcart.domain.repository.RemoteProductCatalog
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds abstract fun bindNetworkMonitor(impl: ConnectivityNetworkMonitor): NetworkMonitor
    @Binds abstract fun bindRemoteCatalog(impl: OpenFoodFactsDataSource): RemoteProductCatalog

    companion object {
        @Provides
        @Singleton
        fun provideOpenFoodFactsApi(): OpenFoodFactsApi =
            OpenFoodFactsClient.api(OpenFoodFactsClient.okHttp(OpenFoodFactsClient.userAgent(BuildConfig.VERSION_NAME)))

        @Provides
        @Singleton
        fun provideOpenPricesApi(): OpenPricesApi =
            OpenPricesClient.api(OpenPricesClient.okHttp(OpenFoodFactsClient.userAgent(BuildConfig.VERSION_NAME)))
    }
}