package com.nearlink.messenger.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.AdvertisingSet
import android.bluetooth.le.AdvertisingSetCallback
import android.bluetooth.le.AdvertisingSetParameters
import android.content.Context
import android.os.ParcelUuid
import androidx.annotation.RequiresPermission
import com.nearlink.messenger.core.crypto.CryptoUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton
import java.security.SecureRandom

/**
 * BLE 广播器：周期性广播 NearLink 服务 UUID + 临时 ID + 公钥前缀。
 *
 * eph_id = HMAC-SHA256(rotationKey, epoch_minute)[:8]
 * 每分钟自动轮换，避免被静态跟踪。rotationKey 仅本机生成，不入网。
 */
@Singleton
class BleAdvertiser @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    private val rotationKey: ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }
    private var advertiserCallback: AdvertiseCallback? = null

    @Volatile var isRunning: Boolean = false
        private set

    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE])
    fun start(myEdPub: ByteArray) {
        val a = adapter ?: return
        val advertiser = a.bluetoothLeAdvertiser ?: return
        if (isRunning) return

        val ephId = computeEphId()
        val pubPrefix = myEdPub.copyOfRange(0, 6)
        val serviceData = byteArrayOf(0x01, 0x00) + ephId + pubPrefix  // version=1, flags=0

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .setConnectable(true)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)                    // 隐私：不广播本机名
            .addServiceUuid(ParcelUuid(BtUuids.SERVICE_UUID))
            .addServiceData(ParcelUuid(BtUuids.SERVICE_UUID), serviceData)
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) { isRunning = true }
            override fun onStartFailure(errorCode: Int) { isRunning = false }
        }
        advertiser.startAdvertising(settings, data, callback)
        advertiserCallback = callback
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_ADVERTISE])
    fun stop() {
        val a = adapter ?: return
        val advertiser = a.bluetoothLeAdvertiser ?: return
        advertiserCallback?.let { advertiser.stopAdvertising(it) }
        advertiserCallback = null
        isRunning = false
    }

    private fun computeEphId(): ByteArray {
        val epochMin = (System.currentTimeMillis() / 60_000L)
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(rotationKey, "HmacSHA256"))
        }
        val raw = mac.doFinal(longToBytes(epochMin))
        return raw.copyOfRange(0, 8)
    }

    private fun longToBytes(v: Long): ByteArray {
        val out = ByteArray(8)
        var x = v
        for (i in 7 downTo 0) {
            out[i] = (x and 0xFF).toByte()
            x = x ushr 8
        }
        return out
    }
}
