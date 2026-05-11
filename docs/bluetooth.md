# NearLink 蓝牙发现与传输协议

## 1. 角色与阶段

近场互通分两个阶段：

1. **发现 + 配对**：BLE 广播 + 扫描，定位附近运行 NearLink 的设备。
2. **数据通道**：升级到 **Bluetooth Classic RFCOMM**（或 BLE L2CAP CoC，二选一）传消息和文件。

> RFCOMM 比 GATT 写入更适合大数据流；GATT 用作发现握手。

## 2. UUID 命名

| 名称 | UUID | 用途 |
|------|------|------|
| Service UUID (BLE) | `7E2A0001-4F1B-4E2A-9E2A-NL000000C001` | 广播 service uuid，扫描端过滤 |
| Char Identity (BLE) | `7E2A0002-...C002` | 读：设备临时 ID + 公钥指纹前缀 |
| RFCOMM Service | `7E2A0010-4F1B-4E2A-9E2A-NL000000C010` | RFCOMM SDP 注册 UUID |
| L2CAP PSM | 动态分配 | 备用大数据通道 |

> 上面 UUID 中 `NL` 仅示意，实际请用真随机 UUID v4 固定值。

## 3. BLE 广播包

```
AdvertisementData {
  service_uuid: <NearLink Service UUID>,
  service_data: bytes(
    version       :u8     // = 1
    flags         :u8     // bit0 = 公开 device_id 前缀, bit1 = 已配对优先
    eph_id        :8B     // 临时 ID = HMAC(rotation_key, epoch_minute)[:8]
    pubkey_prefix :6B     // pk_id 的前 6 字节，便于扫描端筛选已知联系人
  ),
  local_name: "NL"        // 不要泄露真实昵称
}
```

`eph_id` 每分钟轮换，避免被跟踪。`rotation_key` 本机随机生成，不入网。

广播间隔：低功耗模式 1s，发现态 250ms。

## 4. 扫描

- Android 12+：需要 `BLUETOOTH_SCAN`（neverForLocation）。
- 过滤条件：service_uuid = NearLink。
- 列出设备时，按 `pubkey_prefix` 匹配本地联系人公钥前缀，已知联系人显示在前。

## 5. 配对握手（GATT 读 + RFCOMM）

```
扫描方 A 看到设备 B：
  1. A 通过 GATT 连接 B，读 Char Identity → 拿到 B 的 device_id_full + pk_id_b58 摘要
  2. A 在 UI 显示 "向 B 发起配对"
  3. A 解析 B 的 RFCOMM Service UUID，发起 BluetoothSocket 连接
  4. 双方进入 §6 的 RFCOMM 握手
  5. 通过 → 显示安全码 → 用户双方点确认 → 入库
```

## 6. RFCOMM 数据帧

二进制长度前缀分帧，**不**用换行做分隔（避免随机字节冲突）。

```
+--------+------+--------+----------+
| MAGIC  | VER  | TYPE   | LEN(u32) | <payload bytes>
| 0x4E4C | 0x01 | 0x..   | big-end  |
+--------+------+--------+----------+
```

| TYPE | 名称 | 方向 | payload | 说明 |
|------|------|------|---------|------|
| 0x01 | HELLO | 双向 | JSON{device_id, pk_id, pk_x, sig, app_ver} | 握手第一帧 |
| 0x02 | HELLO_ACK | 双向 | JSON{ok, reason?} | 握手确认 |
| 0x10 | MSG | 双向 | JSON 同 WS msg_send payload | 端到端密文 |
| 0x11 | MSG_ACK | 双向 | JSON{client_msg_id, status} | 链路 ACK |
| 0x12 | MSG_READ | 双向 | JSON{conv_id, up_to_msg_id} | 已读 |
| 0x13 | MSG_TYPING | 双向 | JSON{state} | 正在输入 |
| 0x20 | FILE_INIT | 双向 | JSON{file_id, total_size, sha256, chunk_size} | 大文件协商 |
| 0x21 | FILE_CHUNK | 双向 | u32 idx ‖ raw bytes | 分片（密文已在外层） |
| 0x22 | FILE_DONE | 双向 | JSON{file_id, ok} | 完成 |
| 0xF0 | PING | 双向 | 8B nonce | 心跳 |
| 0xF1 | PONG | 双向 | 8B nonce echo | |
| 0xFF | BYE | 双向 | optional reason | 关闭 |

约束：
- 单帧 LEN 上限 256KiB，超过的强制分片走 0x21。
- HELLO 必须是 RFCOMM 建立后**第一帧**，否则关闭。

## 7. 大文件流程（蓝牙）

```
A → B : 0x20 FILE_INIT { file_id, total, sha256, chunk_size=64KiB }
B → A : 0x11 MSG_ACK ok
loop:
  A → B : 0x21 FILE_CHUNK <idx> <encrypted-bytes>
  B → A : 0x11 MSG_ACK { client_msg_id = file_id+":"+idx, status: "received" }
  按 8 个分片发一组 ACK 也可，由滑动窗口控制
A → B : 0x22 FILE_DONE
B 计算整体 SHA-256，与 FILE_INIT 比对，不匹配 → 拒收 + 通知用户
```

## 8. 断线重连

- RFCOMM `read()` 抛 IOException → 立刻 `close()`。
- 5/15/45/90s 退避重试连接（前提：远端仍在 BLE 广播）。
- 未收到 ACK 的消息状态保持 `PENDING`，下一次 RFCOMM 建立后批量重发；服务器通道也可接管。

## 9. 权限

| Android 版本 | 必需权限 |
|--------------|----------|
| ≤ 11 | `BLUETOOTH`, `BLUETOOTH_ADMIN`, `ACCESS_FINE_LOCATION` |
| 12+ | `BLUETOOTH_SCAN`(neverForLocation), `BLUETOOTH_ADVERTISE`, `BLUETOOTH_CONNECT` |

不申请 location（扫描 flag 已声明 neverForLocation）。

## 10. 安全注意

- 配对前 RFCOMM 通道**不可信**：所有应用层消息必须经 §encryption 签名 / AEAD 校验。
- HELLO 中 `sig` 必须用 `pk_id` 验签，否则丢弃并 BYE。
- 不要把昵称写到 BLE local_name（隐私）。
- 不要把任何长期密钥发出蓝牙广播；只发临时 `eph_id` + `pubkey_prefix`。
