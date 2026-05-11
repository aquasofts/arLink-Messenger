package com.nearlink.messenger.core.crypto

import android.content.Context
import androidx.security.crypto.EncryptedFile
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 身份密钥的本地保管。
 *
 * 设计：
 *  1. Ed25519 与 X25519 长期密钥由 [Ed25519X25519.generate] 生成，两段互相独立。
 *  2. 私钥用 Android Keystore 的 MasterKey 包裹（AES-GCM via Jetpack Security's EncryptedFile）。
 *  3. 文件落在 app 私有目录，不进 Room，避免 DB 备份/导出误带。
 *  4. 公钥单独以 plain 文件缓存便于启动快速读取；私钥仅在 sign/DH 时解密进内存，使用完 wipe。
 *
 * 二进制布局：
 *   identity.pub     —— 64B = edPub(32) || xPub(32)
 *   identity.sk.enc  —— 被 EncryptedFile 包裹的 64B = edPriv(32) || xPriv(32)
 *
 * 失败回退路径：MasterKey 在被 Root 的机器上可能失败；抛异常交由 UI 处理。
 */
@Singleton
class IdentityKeyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val root: File by lazy { File(context.filesDir, "identity").apply { mkdirs() } }

    private val pubFile get() = File(root, "identity.pub")
    private val skFile get() = File(root, "identity.sk.enc")

    suspend fun hasIdentity(): Boolean = withContext(Dispatchers.IO) {
        pubFile.exists() && skFile.exists()
    }

    /**
     * 生成并落盘。幂等：已存在时直接返回原公钥。
     */
    suspend fun initializeIfAbsent(): PublicIdentity = withContext(Dispatchers.IO) {
        if (hasIdentity()) return@withContext loadPublic()

        val kp = Ed25519X25519.generate()
        try {
            pubFile.writeBytes(encodePublic(kp.edPub, kp.xPub))
            writeEncrypted(skFile, encodeSecret(kp.edPriv, kp.xPriv))
        } finally {
            CryptoUtils.wipe(kp.edPriv, kp.xPriv)
        }
        PublicIdentity(kp.edPub, kp.xPub, deviceIdOf(kp.edPub))
    }

    suspend fun loadPublic(): PublicIdentity = withContext(Dispatchers.IO) {
        val (edPub, xPub) = decodePublic(pubFile.readBytes())
        PublicIdentity(edPub, xPub, deviceIdOf(edPub))
    }

    /**
     * 使用长期 Ed25519 私钥签名：私钥临时加载，函数返回即擦除。
     */
    suspend fun sign(message: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val (edPriv, _) = loadSecretInternal()
        try {
            Ed25519X25519.sign(edPriv, message)
        } finally {
            CryptoUtils.wipe(edPriv)
        }
    }

    /**
     * 使用长期 X25519 私钥与对端 X25519 公钥做 ECDH，返回 32B 共享秘密。
     */
    suspend fun dh(peerXPub: ByteArray): ByteArray = withContext(Dispatchers.IO) {
        val (_, xPriv) = loadSecretInternal()
        try {
            Ed25519X25519.dh(xPriv, peerXPub)
        } finally {
            CryptoUtils.wipe(xPriv)
        }
    }

    // ------- 私有实现 -------

    private fun loadSecretInternal(): Pair<ByteArray, ByteArray> {
        val raw = readEncrypted(skFile)
        return decodeSecret(raw)
    }

    private fun writeEncrypted(file: File, data: ByteArray) {
        if (file.exists()) file.delete()
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encFile = EncryptedFile.Builder(
            context,
            file,
            master,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        encFile.openFileOutput().use { it.write(data) }
    }

    private fun readEncrypted(file: File): ByteArray {
        val master = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        val encFile = EncryptedFile.Builder(
            context,
            file,
            master,
            EncryptedFile.FileEncryptionScheme.AES256_GCM_HKDF_4KB
        ).build()
        return encFile.openFileInput().use { it.readBytes() }
    }

    private fun encodePublic(edPub: ByteArray, xPub: ByteArray): ByteArray {
        require(edPub.size == 32 && xPub.size == 32)
        return edPub + xPub
    }

    private fun decodePublic(raw: ByteArray): Pair<ByteArray, ByteArray> {
        require(raw.size == 64) { "identity.pub corrupted" }
        return raw.copyOfRange(0, 32) to raw.copyOfRange(32, 64)
    }

    private fun encodeSecret(edPriv: ByteArray, xPriv: ByteArray): ByteArray {
        require(edPriv.size == 32 && xPriv.size == 32)
        return edPriv + xPriv
    }

    private fun decodeSecret(raw: ByteArray): Pair<ByteArray, ByteArray> {
        require(raw.size == 64) { "identity.sk corrupted" }
        return raw.copyOfRange(0, 32) to raw.copyOfRange(32, 64)
    }

    private fun deviceIdOf(edPub: ByteArray): String =
        CryptoUtils.base32Lower(CryptoUtils.sha256(edPub)).take(24)
}

data class PublicIdentity(
    val edPub: ByteArray,
    val xPub: ByteArray,
    val deviceId: String,
)
