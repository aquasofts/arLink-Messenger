package com.nearlink.messenger.domain.usecase

import android.Manifest
import androidx.annotation.RequiresPermission
import com.nearlink.messenger.core.bluetooth.BleAdvertiser
import com.nearlink.messenger.core.bluetooth.BleScanner
import com.nearlink.messenger.core.bluetooth.DiscoveredDevice
import com.nearlink.messenger.data.repository.IdentityRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** 启动 BLE 广播 + 扫描，返回流式发现结果。调用方负责权限。 */
class DiscoverPeersUseCase @Inject constructor(
    private val advertiser: BleAdvertiser,
    private val scanner: BleScanner,
    private val identity: IdentityRepository,
) {
    @RequiresPermission(allOf = [
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
    ])
    suspend fun startAdvertising() {
        val pub = identity.publicIdentity()
        advertiser.start(pub.edPub)
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE])
    fun stopAdvertising() = advertiser.stop()

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun observeNearby(): Flow<DiscoveredDevice> = scanner.discover()
}
