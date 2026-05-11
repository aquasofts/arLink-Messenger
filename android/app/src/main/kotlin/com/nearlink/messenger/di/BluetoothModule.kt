package com.nearlink.messenger.di

import com.nearlink.messenger.core.bluetooth.BluetoothEngine
import com.nearlink.messenger.core.transport.Transport
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BluetoothModule {
    @Binds
    @Singleton
    @IntoSet
    abstract fun bindBtTransport(engine: BluetoothEngine): Transport
}
