# SMS2Telegram

A minimalist Android app that automatically forwards incoming SMS messages to a Telegram bot. / 自动将 Android 手机短信转发到 Telegram 的极简应用。

## 💡 Background / 开发背景

**EN:**
I keep my primary physical SIM card in an old spare Android phone at home to prevent the hassle of replacing it if lost abroad. However, this makes it inconvenient to receive 2FA (Two-Factor Authentication) SMS codes. I built this minimalist app to solve this pain point. The spare phone stays at home connected to WiFi, silently listens for incoming SMS, and instantly forwards them to my Telegram. I can receive important codes anywhere without carrying the spare phone.

**ZH:**
由于我的国内手机卡是实体卡，如果不慎丢失，跨国补卡的过程将极为繁琐。为了规避风险，我将这张实体卡长期插在家里的一台旧安卓备用机中。但这就导致日常接收平台登录验证码时极其不便。为了解决这个痛点，我开发了这款极简应用。备用机只需放在家中连接 WiFi，应用会在后台默默监听新短信，并瞬间推送到我的 Telegram 上。不带手机，也能随时随地接收重要验证码。

## ✨ Core Features / 核心特性

**EN:**
*   **Pure & Minimalist:** No complex routing rules or unnecessary UI elements. It focuses solely on reading SMS and forwarding them to Telegram.
*   **Absolute Security:** Runs entirely locally on your device. No user registration, no third-party servers. SMS data is transmitted securely via HTTPS directly to the official Telegram API.
*   **Low Power Consumption:** Built with native code without background wake-locks. Highly battery-efficient, making it perfect for old Android devices running 24/7.

**ZH:**
*   **纯粹极简：**没有复杂的路由分流规则，没有多余的 UI 元素，只专注于“读取短信并转发到 Telegram”这一核心诉求。
*   **绝对安全：**应用完全在设备本地运行，无需注册账号，无任何第三方服务器中转。短信数据仅通过 HTTPS 加密传输至 Telegram 官方 API。
*   **低功耗：**纯原生代码编写，无后台唤醒链，极度省电，非常适合老旧安卓设备长期挂机。

## 🚀 Quick Start / 快速开始

**EN:**
**1. Prepare Telegram Bot**
1. Search for `@BotFather` in Telegram.
2. Send the `/newbot` command and follow the instructions to create a bot. Save the generated **Bot Token** (e.g., `123456789:ABCdef...`).
3. Search for `@userinfobot` to get your Telegram account's **Chat ID** (a string of numbers).
4. **Important:** Send an arbitrary message to your newly created bot in Telegram to initiate the chat, otherwise, the bot cannot send messages to you.

**2. Install and Configure App**
1. Download the latest APK file from the `Releases` page of this repository and install it.
2. Upon first launch, you **must grant** the "Receive/Read SMS" permissions.
3. Enter your **Bot Token** and **Chat ID** in the app's main interface and save.
4. Keep the app running in the background. Send a test SMS to this phone, and the Telegram bot will push the message to you immediately.

**ZH:**
**1. 准备 Telegram Bot**
1. 在 Telegram 中搜索并进入 `@BotFather`。
2. 发送 `/newbot` 指令，按提示创建机器人，并记录下生成的 **Bot Token**（格式类似 `123456789:ABCdefGHIjklm...`）。
3. 搜索并进入 `@userinfobot`，获取你当前 Telegram 账号的 **Chat ID**（一串纯数字）。
4. **重要：**在 Telegram 中主动向你新建的机器人发送一条任意消息，以激活会话，否则机器人无法主动向你发消息。

**2. 安装与配置 App**
1. 在本仓库的 `Releases` 页面下载最新版本的 APK 文件并安装。
2. 首次启动应用时，**必须允许**应用获取“接收/读取短信”权限。
3. 在应用主界面填入准备好的 **Bot Token** 和 **Chat ID** 并保存。
4. 保持应用后台运行，现在你可以向这台手机发送一条测试短信，Telegram 机器人将立即把短信内容推送给你。

## 🔒 Privacy & Permissions / 隐私与权限说明

**EN:**
This app strictly follows the principle of least privilege, requesting only what is essential for its core functionality:
*   `RECEIVE_SMS` / `READ_SMS`: Used strictly to listen for new SMS messages to extract text. It does not scan or upload your SMS history.
*   `INTERNET`: Used exclusively to send text via the `api.telegram.org` interface.

**ZH:**
本应用严格遵守最小权限原则，仅申请实现核心功能所必需的权限：
*   `RECEIVE_SMS` / `READ_SMS`：仅用于监听新到达的短信以便提取内容，不会扫描或上传历史短信记录。
*   `INTERNET`：仅用于调用 `api.telegram.org` 接口发送文本。

## 📄 License / 许可证

**EN:** This project is licensed under the MIT License.
**ZH:** 本项目采用 MIT License 开源协议。
