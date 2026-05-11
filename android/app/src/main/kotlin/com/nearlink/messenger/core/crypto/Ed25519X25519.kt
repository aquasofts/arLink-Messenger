package com.nearlink.messenger.core.crypto

import com.goterl.lazysodium.LazySodiumAndroid
import com.goterl.lazysodium.SodiumAndroid
import com.goterl.lazysodium.interfaces.Sign

/**
 * Ed25519 签名 + X25519 密钥派生。
 *
 * 实现细节：使用 libsodium 的 `crypto_sign_ed25519_sk_to_curve25519`
 * 把 Ed25519 私钥派生为 X25519 私钥；公钥侧用 `crypto_sign_ed25519_pk_to_curve25519`。
 * 这样我们只需要保管一份长期身份私钥，就同时拥有签名与 DH 能力。
 */
object Ed25519X25519 {

    private val sodium = LazySodiumAndroid(SodiumAndroid())

    data class IdentityKeyPair(
        val edPub: ByteArray,  // 32B
        val edSk: ByteArray,   // 64B (libsodium 的扩展私钥)
        val xPub: ByteArray,   // 32B
        val xSk: ByteArray,    // 32B
    )

    fun generate(): IdentityKeyPair {
        val edPub = ByteArray(Sign.PUBLICKEYBYTES)
        val edSk = ByteArray(Sign.SECRETKEYBYTES)
        check(sodium.cryptoSignKeypair(edPub, edSk)) { "ed25519 keygen failed" }
        val xPub = ByteArray(32)
        val xSk = ByteArray(32)
        check(sodium.convertPublicKeyEd25519ToCurve25519(xPub, edPub)) { "ed→x pub failed" }
        check(sodium.convertSecretKeyEd25519ToCurve25519(xSk, edSk)) { "ed→x sk failed" }
        return IdentityKeyPair(edPub, edSk, xPub, xSk)
    }

    fun sign(sk: ByteArray, message: ByteArray): ByteArray {
        val sig = ByteArray(Sign.BYTES)
        val ok = sodium.cryptoSignDetached(sig, message, message.size.toLong(), sk)
        check(ok) { "ed25519 sign failed" }
        return sig
    }

    fun verify(pk: ByteArray, message: ByteArray, sig: ByteArray): Boolean =
        sodium.cryptoSignVerifyDetached(sig, message, message.size, pk)

    /** X25519 ECDH：返回 32B shared secret（未 hash）。 */
    fun dh(sk: ByteArray, peerPub: ByteArray): ByteArray {
        val out = ByteArray(32)
        check(sodium.cryptoScalarMult(out, sk, peerPub)) { "x25519 dh failed" }
        return out
    }
}
