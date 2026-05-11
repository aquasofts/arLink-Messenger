package com.nearlink.messenger.core.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import androidx.annotation.RequiresPermission
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * RFCOMM 服务端：注册 SDP 后阻塞 accept。
 * 每个 socket 通过 [BtSession] 暴露给上层。
 */
@Singleton
class RfcommServer @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val adapter: BluetoothAdapter? by lazy {
        (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter
    }
    private var serverSocket: BluetoothServerSocket? = null
    private var loopJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @SuppressLint("MissingPermission")
    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_CONNECT])
    fun listen(): Flow<BtSession> = callbackFlow {
        val a = adapter ?: run { close(IllegalStateException("no bt adapter")); return@callbackFlow }
        val server = try {
            a.listenUsingInsecureRfcommWithServiceRecord(BtUuids.RFCOMM_NAME, BtUuids.RFCOMM_UUID)
        } catch (t: SecurityException) {
            close(t); return@callbackFlow
        } catch (t: IOException) {
            close(t); return@callbackFlow
        }
        serverSocket = server
        loopJob = scope.launch {
            while (!isClosedForSend) {
                try {
                    val socket = server.accept()
                    trySend(BtSession.fromSocket(socket))
                } catch (t: IOException) {
                    close(t); break
                }
            }
        }
        awaitClose {
            try { server.close() } catch (_: IOException) {}
            loopJob?.cancel()
        }
    }

    fun shutdown() {
        try { serverSocket?.close() } catch (_: IOException) {}
        scope.cancel()
    }
}

/**
 * 已建立的 RFCOMM 通道。生命周期由调用方管理（close 时关 socket）。
 */
class BtSession internal constructor(
    val socket: BluetoothSocket,
) : AutoCloseable {
    val input get() = socket.inputStream
    val output get() = socket.outputStream
    val remoteAddress: String get() = socket.remoteDevice.address

    override fun close() {
        try { socket.close() } catch (_: IOException) {}
    }

    companion object {
        fun fromSocket(socket: BluetoothSocket): BtSession = BtSession(socket)
    }
}
