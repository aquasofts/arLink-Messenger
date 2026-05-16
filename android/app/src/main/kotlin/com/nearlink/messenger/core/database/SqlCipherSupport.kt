package com.nearlink.messenger.core.database

import androidx.room.RoomDatabase
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SqlCipherSupport @Inject constructor() {

    fun isEnabled(): Boolean = false

    fun setEnabled(enabled: Boolean, passphrase: ByteArray?) {
        passphrase?.fill(0)
        check(!enabled) { "SQLCipher is not bundled in this build" }
    }

    fun applyIfEnabled(builder: RoomDatabase.Builder<NearLinkDatabase>) = Unit

    fun zeroize() = Unit
}
