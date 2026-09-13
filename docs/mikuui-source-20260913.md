# MikuUI 源码导入记录

## 内容

- 版本：Miku 1.8.1
- 来源：`MikuUI-github-source.zip`
- 目录：`MikuUI/`
- 类型：独立 Android 桌宠工程
- 功能：悬浮窗桌宠、自由行走、心情/饥饿、待办奖励、本地时间行为、AI 形象定制

## 目录整理

压缩包根目录中的 Gradle 工程完整保留在 `MikuUI/`。构建缓存、`build/`、`.gradle/`、`.kotlin/`、APK/AAB 和本地 `local.properties` 未纳入提交。

## API Key 安全

工程通过 `MikuUI/local.properties` 注入 DeepSeek、OpenAI、DashScope 配置。该文件被忽略，提交内容不包含真实密钥。首次使用 AI 接口时，在本地创建该文件并填写对应配置即可。

## 运行

用 Android Studio 打开 `MikuUI/`，使用 JDK 17、Android SDK 35，同步 Gradle 后运行 `app`。桌宠功能需要用户在系统中授予“显示在其他应用上层”权限；AI 出图功能需要对应服务的 API Key 和网络连接。
