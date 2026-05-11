package com.nearlink.messenger.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BLE 扫描器：过滤 NearLink Service UUID。
 * 输出 [DiscoveredDevice]，包括 RSSI、临时 ID、公钥前缀。
 */
@Singleton
class BleScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN])
    fun discover(): Flow<DiscoveredDevice> = callbackFlow {
        val a = adapter ?: run { close(); return@callbackFlow }
        val scanner = a.bluetoothLeScanner ?: run { close(); return@callbackFlow }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(BtUuids.SERVICE_UUID))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val sd = result.scanRecord?.getServiceData(ParcelUuid(BtUuids.SERVICE_UUID))
                if (sd != null && sd.size >= 16) {
                    // version(1) flags(1) eph_id(8) pubkey_prefix(6)
                    val ephId = sd.copyOfRange(2, 10)
                    val pubPrefix = sd.copyOfRange(10, 16)
                    trySend(
                        DiscoveredDevice(
                            address = result.device.address,
                            rssi = result.rssi,
                            ephId = ephId,
                            pubPrefix = pubPrefix,
                            device = result.device,
                        )
                    )
                }
            }
            override fun onScanFailed(errorCode: Int) { close(IllegalStateException("scan failed: $errorCode")) }
        }
        scanner.startScan(listOf(filter), settings, callback)
        awaitClose { scanner.stopScan(callback) }
    }
}

data class DiscoveredDevice(
    val address: String,
    val rssi: Int,
    val ephId: ByteArray,
    val pubPrefix: ByteArray,
    val device: BluetoothDevice,
) {
    override fun equals(other: Any?): Boolean =
        this === other || (other is DiscoveredDevice && address == other.address)
    override fun hashCode(): Int = address.hashCode()
}
