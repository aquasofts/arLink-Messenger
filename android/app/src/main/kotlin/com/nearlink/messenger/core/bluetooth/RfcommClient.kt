package com.nearlink.messenger.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RFCOMM 客户端：按地址连接已知联系人。
 */
@Singleton
class RfcommClient @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }

    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
    suspend fun connect(address: String): BtSession = withContext(Dispatchers.IO) {
        val a = adapter ?: error("no bt adapter")
        val device: BluetoothDevice = a.getRemoteDevice(address)
        // 取消正在进行的发现，避免占用 BT 资源
        @Suppress("MissingPermission")
        if (a.isDiscovering) a.cancelDiscovery()
        val socket = device.createInsecureRfcommSocketToServiceRecord(BtUuids.RFCOMM_UUID)
        socket.connect()
        BtSession.fromSocket(socket)
    }
}
