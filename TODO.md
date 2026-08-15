# TODO：未完成任务清单

> 记录日期：2026-08-07
> 未完成 / 暂缓的功能统一记录于此，完成后勾选并移除。

## 功能开发

### 无线调试配对协议（暂缓）
> 来自日记想法：「引导用户使用安卓自带的无线调试模式连接本应用，输入配对码直接连接」

- [ ] 应用内输入配对码连接本机 adbd，无需电脑
- [ ] 实现 AOSP 无线调试配对协议：
  - [ ] X25519 ECDH 密钥交换
  - [ ] scrypt 派生配对密钥
  - [ ] SP800-56A KDF 派生 AES-256-GCM 密钥
- [ ] 配对成功后 RSA 认证 main port
- [ ] 在 main port 上执行 `pm disable-user` / `pm enable` 命令

**当前状态**：`AdbWirelessClient.pair()` 为占位实现（返回 false），设置页点「连接」提示「开发中，敬请期待」。

**参考**：LineageOS `android_packages_modules_adb` 的 `pairing_connection/pairing_connection.cpp`、`pairing_auth/pairing_auth.cpp`

### 电脑客户端 ADB 连接模式（规划中，暂不开发）
> 手机通过内网/扫码连接电脑客户端，由电脑端管理应用冻结/解冻。

- 详见 [TODO-电脑客户端.md](TODO-电脑客户端.md)
