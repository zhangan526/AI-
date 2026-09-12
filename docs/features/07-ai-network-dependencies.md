# 大模型网络基础依赖

## 用户可见行为

为后续接入 DeepSeek 等 OpenAI 兼容大模型提供网络请求和协程运行时；本次改动不改变现有界面行为。

## 入口（手势 / 主页按钮 / 配置页）

暂无。该功能只增加依赖和网络权限，模型客户端及聊天入口在后续功能中接入。

## 模块与关键类

- `:app` 使用 version catalog 管理依赖。
- OkHttp 负责 HTTPS 请求。
- `kotlinx-coroutines-android` 用于在后台线程执行请求并回到 Android 主线程更新 UI。

## 权限

`AndroidManifest.xml` 声明 `android.permission.INTERNET`。该权限为普通权限，不需要运行时申请。

## 不要做的事

- 不要把 DeepSeek API Key 写入源码、资源文件或提交到仓库。
- 不要在主线程直接执行网络请求。

## 验证步骤

在 `GuardPet/` 目录执行 `./gradlew :app:assembleDebug`，确认依赖可解析且 Debug 包构建成功。

2026-09-12：配置 JDK 17、Gradle 9.5.0 和本机 Android SDK 后，Debug 构建成功。OkHttp 4.12.0 与协程 Android 1.10.2 已通过依赖解析和编译验证；当前尚未实现或测试 DeepSeek 请求。环境排障记录见 `../TROUBLESHOOTING.md`。
