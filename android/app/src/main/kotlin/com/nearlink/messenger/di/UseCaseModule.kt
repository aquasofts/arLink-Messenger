package com.nearlink.messenger.di

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** UseCase 全部 [@Inject constructor]，由 Hilt 自动构造，无需在此手写 @Provides。 */
@Module
@InstallIn(SingletonComponent::class)
object UseCaseModule
