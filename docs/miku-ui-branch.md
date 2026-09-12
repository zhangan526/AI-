# Miku UI 版本

## 来源

仓库根目录下的 `MikuUI/` 来自用户提供的 `AI桌宠-miku极简高级版-完整源码.zip`，包含原包的 113 个文件。用户确认按原样上传为独立 UI 版本；本次没有移植或修改应用代码。

分支：`codex/ui-miku-modern`，基于 `main` 的 `7bf62be`。

原压缩包 SHA-256：`71D338C55519241D544DF9C0032ADA62426CFBDAB1D2C1A571AAF5334B972B68`。

## 内容

- 极简高级版主页 UI：品牌头部、状态徽章、宠物预览、心情/饱食度/食物卡片
- 喂食、摸摸、小憩等快捷操作
- 可折叠的自由行走、宠物大小、行走速度设置
- 待办列表与完成奖励控制窗
- Miku 桌宠素材和本地时间行为

## 与 GuardPet 的关系

此版本使用 `com.example.desktoppet` 包名，是独立的 UI 原型工程，放在 `MikuUI/`，不会覆盖产品工程 `GuardPet/`。`GuardPet` 中的闪记、大爆炸提取文字、番茄钟和 Reef 专注功能仍保持不变。

## 运行

用 Android Studio 打开 `MikuUI/`，使用 JDK 17、Android SDK 35，同步 Gradle 后运行 `:app:assembleDebug`。

源码的实际版本为 `1.8.0`（versionCode 11）；原包 README 的 `1.7.0` 标注已过时，为保留原包内容未修改。

## 本次验证

- 解压内容与导入文件逐一核对，共 113 个文件；源码和资源不含本机凭据或构建输出。
- 46 个 XML 文件解析通过。
- 2026-09-12 使用本机 JBR 21、Gradle 8.9、Android SDK 35 执行 `:app:assembleDebug --offline`：`BUILD SUCCESSFUL`，36 个任务执行成功。Java/Kotlin 编译目标为 17。
- 构建提示 `android.overridePathCheck` 为实验选项，以及 `SOFT_INPUT_ADJUST_RESIZE` 已弃用；均未阻止编译。
- 未进行手机安装或悬浮窗交互测试。

## 后续合并注意

若要把这套界面合入 `GuardPet`，需要逐项映射 ViewBinding ID、字符串和 drawable，并保留守伴的权限页、透明大爆炸 Activity、唯一 `PetService` 与 Reef 署名；不要直接替换 `GuardPet/app`。
