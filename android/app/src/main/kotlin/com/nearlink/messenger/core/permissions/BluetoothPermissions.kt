package com.nearlink.messenger.core.permissions

import android.Manifest
import android.os.Build

/**
 * 集中维护蓝牙相关权限分组（manifest 已声明，运行时按需申请）。
 */
object BluetoothPermissions {

    val runtime: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION, // 旧版 BLE 扫描必需
        )
    }

    val notification: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(Manifest.permission.POST_NOTIFICATIONS)
    } else emptyArray()

    val microphone: Array<String> = arrayOf(Manifest.permission.RECORD_AUDIO)
}
