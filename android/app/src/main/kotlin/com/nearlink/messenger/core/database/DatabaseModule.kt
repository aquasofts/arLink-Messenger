package com.nearlink.messenger.core.database

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext ctx: Context,
        sqlCipher: SqlCipherSupport,
    ): NearLinkDatabase {
        val builder: RoomDatabase.Builder<NearLinkDatabase> =
            Room.databaseBuilder(ctx, NearLinkDatabase::class.java, NearLinkDatabase.NAME)
                .fallbackToDestructiveMigrationOnDowngrade()
        // 用户启用了 SQLCipher 时，注入 SupportFactory
        sqlCipher.applyIfEnabled(builder)
        return builder.build()
    }

    @Provides fun contactDao(db: NearLinkDatabase) = db.contactDao()
    @Provides fun conversationDao(db: NearLinkDatabase) = db.conversationDao()
    @Provides fun messageDao(db: NearLinkDatabase) = db.messageDao()
    @Provides fun keyDao(db: NearLinkDatabase) = db.keyDao()
    @Provides fun outboxDao(db: NearLinkDatabase) = db.outboxDao()
}
