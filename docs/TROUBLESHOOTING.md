# 排障日志

按时间追加。格式：症状 → 试过什么 → 原因 → 办法。

## 2026-09-12 本机 Java 与 Gradle 环境配置

**症状：** Gradle 首先报 `JAVA_HOME is not set`；安装 Java 后下载 Gradle 又报 `Connection refused`。

**原因：** 本机未安装 JDK；项目强制使用未启动的 `127.0.0.1:7890` 代理。

**办法：** 安装 Eclipse Temurin JDK 17，设置用户级 `JAVA_HOME` 并把 JDK 的 `bin` 加入用户 `Path`；注释项目 `gradle.properties` 中的代理配置，仅在代理实际监听时启用。新打开的终端会自动读取更新后的环境变量。

**后续排查：** Gradle 下载完成后，Windows 中文项目路径触发 AGP 检查，随后构建提示 `SDK location not found`。在 `GuardPet/gradle.properties` 中设置 `android.overridePathCheck=true` 后，本项目通过路径检查。该设置只跳过检查，其他工具若仍不支持中文路径，需要使用纯英文路径。

本机已有 Android SDK，无需重复安装。在被 `.gitignore` 排除的 `GuardPet/local.properties` 中设置 `sdk.dir=C:/Users/ZHY/AppData/Local/Android/Sdk`；用户级 `ANDROID_HOME` 和 `ANDROID_SDK_ROOT` 指向同一目录，用户 `Path` 加入 SDK 的 `platform-tools`。

**验证结果：** JDK 17.0.20.1、Gradle 9.5.0 可运行；`./gradlew.bat :app:assembleDebug --no-daemon` 显示 `BUILD SUCCESSFUL`，79 个任务执行完成。APK 位于 `GuardPet/app/build/outputs/apk/debug/app-debug.apk`。网络依赖通过构建验证，尚未进行 DeepSeek API 调用。

DeepSeek Agent 接入后再次构建通过；当前没有连接真机，因此未在设备上验证悬浮窗动作或真实 API 调用。

---

## 2026-09-12 番茄钟 overlay 启动即崩溃

**症状：** 桌宠菜单里点番茄钟，应用直接没。

**试过：** 复用 Reef `FocusModeService.ACTION_START`。

**原因：**

1. `PetService` 已是 `specialUse` FGS，再拉一条同类型 FGS 在 Android 14+ 易失败。
2. `FocusModeService.updateProgressSegments()` 在 API 36 读 `App.colorScheme`，该字段只在 Reef Compose 主题跑过才赋值。

**办法：** overlay 内 `Handler` 倒计时；`prefs["focus_mode"]=true` 交给 `BlockerService`。`App.colorScheme` 改为默认 `lightColorScheme()`。不要从桌宠 `startForegroundService(FocusModeService)`。

---

## 2026-09-12 大爆炸整页盖住当前应用 / 跳回守伴主页

**症状：** 提取文字后要么悬浮窗挡住一切，要么跳出当前 App 看到守伴主页。

**试过：** 全屏 `TYPE_APPLICATION_OVERLAY`；或普通 `startActivity(BigBangActivity)` 走默认 affinity。

**原因：** 全屏 overlay 吞触摸。同一 affinity 会把守伴 task（含主页）抬到前台。NovaText 用独立 task 的透明 Activity。

**办法：** `Theme.DesktopPet.BigBang` + `taskAffinity="${applicationId}.bigbang"` + `singleInstance` + `NEW_TASK`。抓字时藏桌宠，避免 overlay 盖在词块上。

---

## 2026-09-12 提取文字不能反选、中文逐字拆

**症状：** 拖选会清掉旧选择；中文每个字一块。

**原因：** `selectRange` 先 `selected.clear()`；`TextTokenizer` 按 CJK 单字切。ICU `BreakIterator` 对汉字也常是逐字。

**办法：** 拖选以 DOWN 时的 snapshot 做并集/差集；jieba `SegMode.SEARCH`，失败再用词典 FMM。

---

## 2026-09-12 拉环改时间但外观不动

**症状：** 分钟变了，拉环长度和指针看不出变化。

**原因：** `PullTabView` 高度写死，只改数字；时钟时间写在表盘外 hint 上。

**办法：** 拉环高度随分钟 `updateViewLayout`；时间画在 `AnalogTimerView` 表盘内；`x=0,y=0` 贴左上角。

---

## 2026-09-12 双击功能菜单打不开 / 控制窗打开失败

**症状：** 双击桌宠弹出 Toast「控制窗打开失败」，菜单不出现。

**原因：** 菜单从 `PetService` inflate，没有 Activity 主题。关闭按钮用了 `?attr/selectableItemBackgroundBorderless`，解析属性失败导致 `InflateException`。

**办法：** overlay 布局不用 `?attr/`；关闭改成普通 `TextView`「×」。inflate 用 `ContextThemeWrapper(service, Theme.DesktopPet)`。

---

## 2026-09-12 启动偶发提示失败 / 服务初始化失败后仍运行回调

**症状：** 首次启动或设备较慢时，主页很快提示「启动失败」；服务创建失败后仍可能继续执行时间和行为检查。

**原因：** 主页只等待固定 1.2 秒检查 `PetService.isRunning`；而 `PetService.onCreate()` 的失败分支调用 `stopSelf()` 后没有结束初始化流程。

**办法：** 主页改为最多 8 次、每 500 ms 轮询服务状态；服务初始化失败时清理已排队的 Handler 回调并立即结束 `onCreate()`。
