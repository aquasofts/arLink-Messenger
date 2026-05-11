package com.nearlink.messenger.di

import com.nearlink.messenger.core.crypto.AeadCipher
import com.nearlink.messenger.core.crypto.XChaChaPolyCipher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 切换 AEAD 实现就改这里：
 *   - XChaChaPolyCipher  (默认, libsodium)
 *   - AesGcmCipher       (回退, JCE)
 *
 * 若目标机型存在 libsodium .so 加载问题，可编译期开一个 buildConfigField 来动态选择。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {

    @Binds
    @Singleton
    abstract fun bindAead(impl: XChaChaPolyCipher): AeadCipher
}
