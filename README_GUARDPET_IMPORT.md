# GuardPet 最新源码导入

本分支导入 `GuardPet (1).zip` 中的完整 Android 工程，工程位于 `GuardPet/`。

## 内容

- `app`：守伴桌宠、闪记、番茄钟、抓字和最新 `LetterActivity`
- `reef`：专注拦截与设置模块
- `appintro`：引导页库
- `third_party`：第三方许可证与说明

压缩包内的 `.gradle`、`.gradle-new`、模块 `build/` 目录、锁文件和本地配置已排除，避免提交构建缓存或机器私有文件。

## 运行

用 Android Studio 打开 `GuardPet/`，使用 JDK 17、Android SDK 37、Gradle 9.5。首次同步需要下载 Android 依赖。桌宠需要悬浮窗权限；番茄钟、抓字和拦截功能还需要用量访问/无障碍权限。

## 安全检查

已检查压缩包源码，未发现 GitHub Token、API Key 明文或私钥模式。不要将 `local.properties` 或任何真实密钥提交到仓库。
