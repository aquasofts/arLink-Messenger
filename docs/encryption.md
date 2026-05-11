# NearLink 端到端加密协议

> 目标：服务器和窃听者都拿不到明文；身份与会话都是设备本地生成。

## 1. 算法选型

| 用途 | 首选 | 回退 |
|------|------|------|
| 身份签名 | **Ed25519**（libsodium / Tink） | java.security `Ed25519`（API 24+） |
| 密钥协商 | **X25519** ECDH | — |
| KDF | **HKDF-SHA256** | — |
| AEAD | **XChaCha20-Poly1305** (24B nonce) | AES-256-GCM (12B nonce, Tink) |
| 哈希 | SHA-256 / SHA-512 | — |
| 安全码 | SHA-512 → 30 位十进制截断 | — |

## 2. 身份生成（首启动）

```
1. 生成 (sk_id, pk_id) ← Ed25519.keypair()
2. device_id ← base32(SHA-256(pk_id))[..24]   // 不含 padding
3. 派生 X25519 静态对 (sk_x, pk_x)             // 由 sk_id 派生或独立生成
4. 私钥保护：
     - Android Keystore (StrongBox if available) 包裹 sk_id
     - sk_x 同方式包裹
   失败回退：AES-GCM 包裹后落 EncryptedFile（文件级加密）
5. 持久化：device_id, pk_id, pk_x, created_at
```

`pk_id` + `pk_x` 一起发布给联系人；`pk_x` 用于 ECDH。

## 3. 配对（Pairing）

近场流程，设备 A 与 B 已通过蓝牙连上 RFCOMM：

```
A → B : { device_id_A, pk_id_A, pk_x_A, sig_A = Ed25519(sk_id_A, "NL-PAIR" ‖ pk_x_A) }
B → A : { device_id_B, pk_id_B, pk_x_B, sig_B }
A: verify sig_B && device_id_B == base32(sha256(pk_id_B))[..24]
B: verify sig_A 同上
A,B: compute safety_number = SAFETY(pk_id_A, pk_id_B)   // §5
两端各自显示 safety_number（同序排列）
人工核对 → 双方点 "确认"
落库为联系人，trust_state = VERIFIED
```

## 4. 会话密钥派生（Per-conversation, "double-ratchet lite"）

我们采用一个简化但足够强的方案：

### 4.1 root_key

```
shared_static = X25519(sk_x_self, pk_x_peer)
root_key      = HKDF-SHA256(salt = sorted(device_id_self, device_id_peer),
                            ikm  = shared_static,
                            info = "NL-ROOT-v1",
                            len  = 32)
```

`root_key` 永久保存（与对端固定）。**不**在每条消息更新；棘轮在 §4.2 完成。

### 4.2 每条消息密钥（发送端）

```
(esk, epk) ← X25519.keypair()                 // 一次性
shared_eph = X25519(esk, pk_x_peer)
mk         = HKDF(salt = root_key,
                  ikm  = shared_eph,
                  info = "NL-MSG-v1" ‖ client_msg_id,
                  len  = 32)
nonce      = random(24)                        // XChaCha
aad        = utf8(from ‖ to ‖ conv_id ‖ client_msg_id)
ct         = XChaCha20-Poly1305.encrypt(mk, nonce, aad, plaintext)
wire = { epk, nonce, ct, aad-implicit }
擦除 esk, mk
```

### 4.3 每条消息密钥（接收端）

```
shared_eph = X25519(sk_x_self, epk_from_wire)
mk         = HKDF(root_key, shared_eph, "NL-MSG-v1" ‖ client_msg_id, 32)
plaintext  = XChaCha20-Poly1305.decrypt(mk, nonce, aad, ct)
擦除 mk
```

**前向安全**：每条消息独立的 `epk/esk` 提供 PFS。`esk` 立即销毁后，root_key 泄露也无法解 chat 之前消息（注意：root_key 泄露 + 拿到密文 + epk 仍可解，因为 epk 是公开的；要更强 PFS 需要完整双棘轮，列入 roadmap）。

> 列为 v2：完整 Signal Double Ratchet（含 chain key 推进 + DH ratchet）。

## 5. 安全码（Safety Number）

参考 Signal：

```
fingerprint(pk_id) = iter5200(SHA-512( pk_id ‖ device_id ))[..30]   // 取 30 字节
safety_number_bytes = sort([fingerprint(pk_id_A), fingerprint(pk_id_B)]).concat()
display = group_in_5_digits(base10(safety_number_bytes), 60_digits) // 12 组 × 5 位
```

UI 展示成 `12345 67890 ...` 的 12 组。两台设备显示**完全相同**才确认。

## 6. 明文消息结构（密文内部）

`plaintext` 解密后是一个紧凑 JSON：

```json
{
  "type": "text|image|file|audio|revoke|edit|reaction|read|typing",
  "client_msg_id": "...",
  "ts": 1715400000000,
  "body": { ... },           // 由 type 决定
  "ref": null                // 回复 / 编辑目标
}
```

例：text → `body = { "text": "hi" }`；image → `body = { "file_id": "...", "mime": "image/jpeg", "sha256": "...", "w": 1080, "h": 1920, "thumb_b64": "..." }`。

## 7. 联系人密钥变化

- 接收方收到来自 `device_id` 的消息但 `epk` 解密失败、或对端推送 `key_rotation` 信令（密文里）→ 标记 `trust_state = CHANGED`，UI 警告。
- 必须用户主动重新核对 safety_number 才转回 `VERIFIED`。

## 8. 实现要点

- `mk` / `esk` / `shared_*` 一律在 `ByteArray` 上操作，使用完调 `Arrays.fill(0)`。
- 不在日志打印任何密钥/密文（详见 security.md）。
- `nonce` 严禁复用，使用 OS RNG (`SecureRandom`)。
- 时钟偏移容忍：`ts` 与本机差超过 ±2h 的消息打 `clock_skew` 标记，但仍展示。

## 9. 测试矩阵（详见 testing.md）

- KAT：固定 keypair 与明文 → 期望密文/解密一致。
- 错位 AAD → 解密必须失败。
- nonce 复用攻击模拟 → 检测脚本必须报错。
- safety_number 对称性：A 看到与 B 看到一致。
