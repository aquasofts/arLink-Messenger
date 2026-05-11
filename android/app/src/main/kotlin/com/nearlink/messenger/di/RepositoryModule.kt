package com.nearlink.messenger.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Repository 全部 [@Inject constructor]，由 Hilt 自动构造。 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule
