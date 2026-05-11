package com.nearlink.messenger.domain.usecase

import com.nearlink.messenger.core.crypto.PublicIdentity
import com.nearlink.messenger.data.repository.IdentityRepository
import javax.inject.Inject

/**
 * 首启动：生成本地身份；返回公钥与 device_id。幂等。
 */
class BootstrapIdentityUseCase @Inject constructor(
    private val repo: IdentityRepository,
) {
    suspend operator fun invoke(): PublicIdentity = repo.bootstrap()
}
