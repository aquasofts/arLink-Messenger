package com.nearlink.messenger.core.database

import androidx.room.RoomDatabase
import com.nearlink.messenger.data.local.entity.ContactEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SQLCipher 集成挂载点（默认关闭）。
 *
 * 启用方式（保留 TODO）：
 *   1. 在 app/build.gradle.kts 解开 `sqlcipher-android` 依赖。
 *   2. 让本类的 [applyIfEnabled] 持有 `net.zetetic.database.sqlcipher.SupportOpenHelperFactory`。
 *   3. 用户在设置页设置 passphrase 后，把 passphrase 喂给 SupportFactory。
 *   4. passphrase 本身建议用 Android Keystore 包裹后落 EncryptedFile（与 IdentityKeyStore 同方案）。
 *
 * 不要尝试在用户尚未设置口令的情况下静默启用——那会让 DB 不可用。
 */
@Singleton
class SqlCipherSupport @Inject constructor() {

    @Volatile
    private var enabled: Boolean = false

    @Volatile
    private var passphrase: ByteArray? = null

    fun setEnabled(enabled: Boolean, passphrase: ByteArray?) {
        this.enabled = enabled && passphrase != null && passphrase.isNotEmpty()
        this.passphrase = passphrase
    }

    fun isEnabled(): Boolean = enabled

    fun applyIfEnabled(builder: RoomDatabase.Builder<NearLinkDatabase>) {
        if (!enabled) return
        // TODO: 启用 SQLCipher
        // val factory = net.zetetic.database.sqlcipher.SupportOpenHelperFactory(passphrase!!)
        // builder.openHelperFactory(factory)
    }

    /**
     * 不需要的时候清零口令，避免长时间常驻内存。
     * 警惕：清零后 Room 重连会失败，仅在准备退出/锁定时调用。
     */
    fun zeroize() {
        passphrase?.fill(0)
        passphrase = null
    }
}

private fun ContactEntity.unused() = Unit  // 防止 IDE 错误删除依赖
