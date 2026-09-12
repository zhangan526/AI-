# 排障日志

按时间追加。格式：症状 → 试过什么 → 原因 → 办法。

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

---

## 2026-09-12 UI 压缩包与 GuardPet 工程不同

**症状：** 用户提供的完整源码包使用 `com.example.desktoppet`，仅包含桌宠与待办；直接覆盖会丢失 GuardPet 的抓字、闪记和专注功能。原 README 的版本号也与构建配置不一致。

**核对与原因：** 比较包名、Gradle 模块和页面绑定后，确认这是独立 Miku UI 版本，实际版本为 1.8.0。

**办法：** 用户确认原样上传后，在新分支的 `MikuUI/` 目录导入 113 个原包文件，用独立工程完成编译验证；产品工程保持原状。

## 2026-09-12 新分支提交身份与网络问题

**症状：** 新克隆没有 Git 提交身份；推送和 GitHub API 请求出现 TLS 握手或 EOF 错误。

**排查：** 读取原工作区的仓库级提交身份；分别检查系统代理和直连 HTTPS。原工作区身份未随克隆迁移，网络握手存在间歇性失败。

**处理：** 仅给新克隆配置原工作区已有的 Git 身份；网络请求使用有限超时，提交成功后须核验远端分支 SHA。凭据只从用户指定的 github.ini 在进程内读取，不写入源码或提交。
