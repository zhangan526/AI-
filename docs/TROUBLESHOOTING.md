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

## 2026-09-13 跑酷更新包为增量源码快照

**症状：** 压缩包包含跑酷和俄罗斯方块页面代码，但引用 `TetrisArena`、`PetHomeActivity`、`home_panel_bg`；当前 `main` 中没有这些依赖。

**核对：** 解压得到 19 个文件，包含 4 个 Kotlin 类、11 帧角色素材和 3 个资源文件。静态检索确认上述依赖不在当前主工程。

**办法：** 在新分支 `codex/guardpet-parkour-20260913` 下以 `ParkourUpdate/` 保存原样快照，并在导入说明中标注依赖和合并步骤；不直接覆盖 `GuardPet/`，待依赖补齐后再合入。
