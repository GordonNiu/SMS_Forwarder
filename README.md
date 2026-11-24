<div align="center">

# SMS Forwarder (`SMS_Forwarder`)

**A minimalist, security-first Android app that forwards incoming SMS and low-battery alerts directly to Telegram.**

极简、注重隐私安全的 Android 工具：将新收到的短信与低电量提醒直接加密转发至你的 Telegram。

![Platform](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-0D9488?style=flat-square)
![Target SDK](https://img.shields.io/badge/Target%20SDK-36-0D9488?style=flat-square)
![Dependencies](https://img.shields.io/badge/Dependencies-Zero-0F766E?style=flat-square)
![License](https://img.shields.io/badge/License-MIT-slate?style=flat-square)

[English](#-english) · [简体中文](#-简体中文)

</div>

---

## <a id="-english"></a>🇬🇧 English

### Why SMS Forwarder?

Designed for a spare Android phone left at home with a physical SIM card (e.g., to avoid losing a primary SIM abroad while still receiving 2FA verification codes anywhere):

- **Pure & Minimalist** — Zero third-party libraries, zero ads, zero analytics, and a tiny ~50 KB minified release APK.
- **Direct & Private** — Runs 100% locally on your device. No relay servers, no account registration. Messages go straight from your phone to `api.telegram.org` over HTTPS.
- **Additional Time Zone** — Optionally include a second time zone (e.g., home city time) alongside local device time in every forwarded SMS, with a searchable picker and popular city shortcuts.
- **Low-Power Battery Alerts** — Uses Android `JobScheduler` (15-minute background checks, surviving reboots and app updates) with no persistent notification, plus an extra urgent alert if battery drops to `≤ 5%`.
- **Light & Dark Theme** — Clean native Material card UI with automatic light/dark mode support.

### Security Highlights

| Layer | Protection |
| --- | --- |
| **Credential Storage** | Bot Token and Chat ID are encrypted locally with `AndroidKeyStore` (`AES/GCM/NoPadding`, 256-bit key). |
| **Network** | HTTPS-only (`usesCleartextTraffic="false"`), system CAs only, redirects disabled, credential format validation, and Telegram link previews disabled (`disable_web_page_preview` / `link_preview_options`) so one-time login links aren't pre-fetched. |
| **UI Privacy** | `FLAG_SECURE` blocks screenshots and recents previews; saved Bot Token is never re-populated into the input field; autofill, view state saving, and personalized keyboard learning are disabled. |
| **Backup & Isolation** | Cloud backup and Android 12+ device-to-device (D2D) transfers are disabled (`allowBackup="false"`, `dataExtractionRules`); `SmsReceiver` is restricted by `BROADCAST_SMS`. |

### Quick Start

#### 1. Create a Telegram Bot
1. Open `@BotFather` in Telegram, run `/newbot`, and copy the **Bot Token** (`123456789:ABCdef...`).
2. Open `@userinfobot` (or similar) to get your numeric **Chat ID**.
3. **Important:** Open your newly created bot in Telegram and tap **Start** (or send any message) so the bot is allowed to message you.

#### 2. Install & Configure
1. Download the latest APK from [Releases](../../releases) and install it on your Android 8.0+ phone.
2. Enter your **Bot Token** and **Chat ID**, then tap **Save and send test message**.
3. Tap **Turn on SMS forwarding** and grant the `RECEIVE_SMS` permission when prompted.
4. *(Optional)* Enable **Additional Time Zone** or **Low Battery Alert** in the cards below.

> **Security Tip:** Standard Telegram Bot chats are encrypted in transit/cloud, not end-to-end encrypted (E2EE). Enable **Two-Step Verification (2FA)** on your Telegram account and keep a screen lock enabled on your spare device.

### Permissions

| Permission | Purpose |
| --- | --- |
| `RECEIVE_SMS` | Listens only for newly arrived SMS messages. Does **not** read or upload SMS history. |
| `INTERNET` | Sends forwarded SMS and battery alerts directly to `https://api.telegram.org`. |
| `RECEIVE_BOOT_COMPLETED` | Automatically restores scheduled low-battery checks after device reboot (no runtime prompt). |

### Build from Source

Requires JDK 17 and Android SDK (Platform 36):

```bash
./gradlew assembleDebug
# Or minified release build:
./gradlew assembleRelease
```

---

## <a id="-简体中文"></a>🇨🇳 简体中文

### 项目简介

专为“放在家里的备用安卓机”打造：把实体 SIM 卡留在家里连接 Wi-Fi，即可随时随地在 Telegram 上接收短信验证码（2FA）和手机低电量预警，无需随身携带多部手机。

- **极致精简** — 零第三方依赖、无广告无埋点，R8 混淆压缩后的 Release APK 仅约 50 KB。
- **本地直连** — 完全在设备本地运行，无任何第三方中转服务器；短信直接通过 HTTPS 发送至 Telegram 官方 API（`api.telegram.org`）。
- **额外时区时间** — 转发短信时可同时附带另一个时区的时间（如北京时间），内置常用城市快捷标签、中英文与 UTC 偏移搜索。
- **低功耗电量提醒** — 基于系统 `JobScheduler` 每 15 分钟后台低功耗检查，无通知栏常驻通知，支持重启与覆盖安装后自动恢复；电量降至 `≤ 5%` 时额外发送紧急提醒。
- **浅色 / 深色模式** — 原生 Material 卡片界面，自动跟随系统浅色与深色主题。

### 安全设计

| 维度 | 实现机制 |
| --- | --- |
| **本地加密存储** | 使用 `AndroidKeyStore` 256 位密钥与 `AES/GCM/NoPadding` 加密保存 Bot Token 和 Chat ID。 |
| **网络传输加固** | 强制 HTTPS（`usesCleartextTraffic="false"`）、仅信任系统根证书、禁止 HTTP 重定向、严格校验凭据格式，并关闭 Telegram 链接预览，防止一次性登录链接被服务器预抓取。 |
| **界面防泄露** | 启用 `FLAG_SECURE` 禁止截屏与最近任务缩略图预览；已保存的 Token 不再回填明文；禁用自动填充、视图状态保存与输入法学习。 |
| **备份与组件隔离** | 禁用云端备份与 Android 12+ 设备间迁移（`allowBackup="false"`、`dataExtractionRules`）；短信接收器仅允许系统 `BROADCAST_SMS` 触发。 |

### 快速开始

#### 1. 准备 Telegram Bot
1. 在 Telegram 中搜索 `@BotFather`，发送 `/newbot` 创建机器人并记录 **Bot Token**（格式如 `123456789:ABCdef...`）。
2. 搜索 `@userinfobot` 获取你的数字 **Chat ID**。
3. **重要：**在 Telegram 中打开你新建的机器人并点击 **Start**（或随便发送一条消息），激活会话后机器人才有权向你推送消息。

#### 2. 安装与配置
1. 前往本仓库 [Releases](../../releases) 页面下载最新 APK 并安装（需 Android 8.0 及以上）。
2. 在主界面填写 **Bot Token** 和 **Chat ID**，点击 **保存并发送测试消息** 验证连通性。
3. 点击 **开启短信自动转发**，按提示授予短信接收权限。
4. *（可选）* 根据需要开启 **额外时区时间** 或 **低电量提醒**。

> **使用建议：** Telegram 普通机器人对话非端到端加密（E2EE），建议为你的 Telegram 账号开启**两步验证密码（2FA）**，并为备用机设置锁屏密码、关闭不必要的 USB 调试。

### 权限说明

| 权限 | 用途说明 |
| --- | --- |
| `RECEIVE_SMS` | 仅用于监听新到达的短信，不会读取、扫描或上传历史短信记录。 |
| `INTERNET` | 仅用于向 `https://api.telegram.org` 发送短信与电量提醒文本。 |
| `RECEIVE_BOOT_COMPLETED` | 用于手机重启后自动恢复低电量定时检查（无需弹窗授权）。 |

### 从源码编译

需要 JDK 17 与 Android SDK（Platform 36）：

```bash
./gradlew assembleDebug
# 编译混淆压缩后的 Release 包：
./gradlew assembleRelease
```

---

## License

This project is licensed under the **MIT License**.
