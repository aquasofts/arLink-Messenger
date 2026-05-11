package com.nearlink.messenger.di

import com.nearlink.messenger.core.network.WebSocketEngine
import com.nearlink.messenger.core.transport.Transport
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttp(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BODY 会打印 base64 密文 —— 危险。保持 NONE，按需开启时只用 HEADERS。
            level = HttpLoggingInterceptor.Level.NONE
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)       // WS 长连
            .writeTimeout(15, TimeUnit.SECONDS)
            .pingInterval(0, TimeUnit.SECONDS)           // 应用层 ping 自己管
            .retryOnConnectionFailure(true)
            .addInterceptor(logging)
            .build()
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkBindingModule {

    @Binds
    @Singleton
    @IntoSet
    abstract fun bindWsTransport(engine: WebSocketEngine): Transport
}
