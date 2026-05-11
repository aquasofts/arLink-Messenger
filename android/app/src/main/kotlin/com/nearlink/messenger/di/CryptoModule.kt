package com.nearlink.messenger.di

import com.nearlink.messenger.core.crypto.AeadCipher
import com.nearlink.messenger.core.crypto.AesGcmCipher
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * AEAD 实现绑定：AES-256-GCM（JCE，零 JNI 依赖，所有 Android 8.0+ 设备原生支持）。
 *
 * 历史：曾经计划用 XChaCha20-Poly1305 (libsodium) 作为首选，AES-GCM 作为回退。
 * 但 lazysodium-android 的 5.1.x 在 Maven Central 不稳定（5.1.4 缺失），
 * 而且引入 JNA + 多 ABI .so 会显著增大 APK 与 CI 失败面。
 *
 * 因此当前版本：只用 AES-256-GCM。如需 XChaCha20-Poly1305，
 * 后续可在 [Roadmap §X] 通过 Tink Aead key template 加入。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoModule {

    @Binds
    @Singleton
    abstract fun bindAead(impl: AesGcmCipher): AeadCipher
}
