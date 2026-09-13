# 灵伴 · AI 手机桌宠

这是一个可直接打开的单文件移动端原型，展示了 AI 手机管家的核心体验：桌宠形象、主动提醒、今日摘要、快捷指令和聊天入口。

## 运行

直接用浏览器打开 `index.html` 即可。当前为前端交互原型，发送消息会显示模拟响应；后续可接入任意 OpenAI-compatible API、SQLite/本地加密存储和 Android 原生工具。

## 下一步

- 接入真实模型和流式回复
- 增加可编辑的长期记忆页面
- 接入 Android 日历、提醒、通知和语音
- 替换 CSS 宠物为 Live2D 角色

## MikuUI 源码分支

本仓库的 `MikuUI/` 目录保存 MikuUI 1.8.1 Android 完整源码快照，来源于
`MikuUI-github-source.zip`（2026-09-13）。它是独立的 Miku UI 参考工程，使用
`com.example.desktoppet` 源码包与 `com.geekathon.guardpet` 应用 ID，不会覆盖
仓库根目录的网页原型。

在 Android Studio 中打开 `MikuUI/`，使用 JDK 17 和 Android SDK 35。API Key
通过 `MikuUI/local.properties` 注入，禁止把真实密钥提交到 Git；示例配置留空时，
应用仍可使用本地形象生成能力。
