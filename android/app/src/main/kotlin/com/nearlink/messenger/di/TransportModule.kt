package com.nearlink.messenger.di

import com.nearlink.messenger.core.transport.TransportManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * TransportManager 已经 @Singleton 自带 @Inject 构造，
 * 这里仅为与同名"Transport"接口区分而保留，便于将来插入装饰器（指标/日志）。
 */
@Module
@InstallIn(SingletonComponent::class)
object TransportModule {
    // intentionally empty —— Hilt 通过构造注入直接拿到 TransportManager
    @Provides @Singleton fun expose(t: TransportManager): TransportManager = t
}
