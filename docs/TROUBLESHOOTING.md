# 守伴排障日志

所有 agent **只往这篇追加**。不要按功能再开新的排障文件。

新条目放在「条目」区**最上方**（时间倒序）。同一问题再次出现时，在原条目下加「复现 / 修订」，不要删旧结论。

完整约定见仓库根目录 `AGENTS.md`。

---

## 条目模板（复制后填，不要改模板本身）

```md
### YYYY-MM-DD — 短标题

- **功能 / 上下文**：`docs/features/...`
- **症状**：命令、日志关键行、用户可见现象
- **尝试过的方法**：
  1. …（结果：失败 / 无效 / 部分有效）
  2. …
- **最终原因**：
- **解决方法**：改了哪些类/资源/文档；如何验证
- **后续**：是否留下已知限制
```

---

## 条目

### 2026-09-13 — 离线编译缺少本地 sherpa-onnx AAR

- **功能 / 上下文**：`docs/features/09-sensevoice-asr.md` / Gradle 离线构建
- **症状**：绕过中文路径检查后，`:app:dataBindingMergeDependencyArtifactsDebug` 报 `libs/sherpa-onnx-1.13.8.aar` 路径不存在。
- **尝试过的方法**：
  1. 使用 `--offline` 直接构建（结果：缺少仓库未跟踪的本地 AAR）
- **最终原因**：`GuardPet/app` 依赖由本地脚本获取的 sherpa-onnx AAR，二进制未提交到 Git。
- **解决方法**：从已有 GuardPet 工作区补入 `GuardPet/libs/sherpa-onnx-1.13.8.aar` 后再构建；该文件受 `.gitignore` 排除，不随分支提交。
- **后续**：其他开发机需先运行 SenseVoice 依赖获取脚本或提供同版本 AAR，才能完成完整离线编译。

### 2026-09-13 — 日程语音策略请求卡住且未申请麦克风

- **功能 / 上下文**：`docs/features/11-day-schedule.md` / `ScheduleOverlay` / `ScheduleLlmClient`
- **症状**：日程卡片按住语音策略后，模型网络不可用时长时间停在解析状态；首次使用日程语音没有弹出麦克风授权，录音无法开始。
- **尝试过的方法**：
  1. 直接调用 `/chat/completions`（结果：网络/DNS 不通时会等到较长的连接或读取超时）
  2. 把日程语音直接复用闪记录音（结果：复用了录音器，但没有复用闪记 overlay 的授权入口）
- **最终原因**：策略解析和后续合理性审查没有在请求前确认模型 HTTP API 可达；日程 overlay 的长按入口也绕过了 `RECORD_AUDIO` 动态授权。
- **解决方法**：`HabitLlmClient.probeModelApi` 在策略解析、日程审查前用带鉴权的 `GET /models` 做 3 秒 HTTP 探测（不用 ICMP）；失败时不发聊天请求，策略按本地应用名规则处理、审查按本地规则放行。`ScheduleOverlay` 缺权限时启动独立 task 的 `MicPermissionActivity.scheduleIntent`；授权后只提示用户重新按住，避免原长按已结束却启动无法停止的录音。
- **后续**：`/models` 是 OpenAI 兼容 API 的健康检查；若自定义服务没有该端点，需提供兼容的 `/models`，或调整探测实现。真机应分别验证授权后再次录音、API 不可达时快速回退。

### 2026-09-12 — 左右切层改 DualOverlayShell 单窗

- **功能 / 上下文**：`docs/features/11-day-schedule.md` / `DualOverlayShell`
- **症状**：双 overlay 用 removeView+addView 抬层，反复出现闪一下、顶层再点丢焦点/无法打字、切层不稳。
- **最终原因**：两条独立 `TYPE_APPLICATION_OVERLAY` 只能靠 remount 改 z-order，与触摸分发、IME 冲突。
- **解决方法**：`PassthroughFrameLayout` 全屏单窗挂左右子面板；切层只用 `elevation`/`bringToFront`；选时间 `setHostVisible(false)`。
- **后续**：真机确认中间区域可点下层 App、重叠区上层可点、打字不丢焦。

### 2026-09-12 — 顶层再点闪一下且无法打字/点击

- **功能 / 上下文**：`docs/features/11-day-schedule.md` / `OverlayLayerCoordinator`
- **症状**：已在最上层的一侧再点会闪；输入框无法打字；该侧控件点不动。
- **最终原因**：`front == side` 时仍 `applyZOrder` 对两侧 `removeView`+`addView`，打断焦点与触摸。
- **解决方法**：已在顶层直接 return；切层只 `raise` 新上层一次；切换中重复请求进 `pending`。
- **后续**：无

### 2026-09-12 — 展开卡片有动作但不切左右层

- **功能 / 上下文**：`docs/features/11-day-schedule.md` / `OverlayLayerCoordinator`
- **症状**：空白处点击能切层；点展开等有业务反馈的控件时动作执行了，左右 z-order 不变。
- **最终原因**：在 `dispatchTouchEvent` 里同步 `removeView`/`addView` 抬层，触摸分发未完成时改 WM 失败或不同步；且只 raise 一侧时易与真实叠层脱节。
- **解决方法**：`noteUserOn` / 根布局回调一律 `post`；切层用「先挂下层再挂上层」`applyZOrder`；切换中请求进 `pending`；点击监听再补一次 `noteInteraction`。
- **后续**：真机点展开确认对侧先收边再换层。

### 2026-09-12 — 点左右卡片无业务反馈时不抬层

- **功能 / 上下文**：`docs/features/11-day-schedule.md` / `OverlaySideRoot`
- **症状**：点到已展开卡片、空白、无响应控件时，该侧不会抬到上层；只有点出动作才切层。
- **最终原因**：`noteInteraction` 挂在各 ClickListener 里；子 View 吃触摸时根 `OnTouchListener` 收不到。
- **解决方法**：左右根布局改为 `OverlaySideRoot`，在 `dispatchTouchEvent(ACTION_DOWN)` 一律 `noteUserOn`。
- **后续**：无

### 2026-09-12 — 左右悬浮窗互挡 + 左侧无开合动画

- **功能 / 上下文**：`docs/features/11-day-schedule.md` / `OverlayLayerCoordinator`
- **症状**：左日程与右闪记叠在一起挡住对方；左侧无滑入滑出；手动选时间时悬浮窗挡住 TimePicker。
- **尝试过的方法**：
  1. 仅 `raise` / `bringToFront` 调 z-order（结果：切层过硬，下层仍挡视觉）
- **最终原因**：两侧同为 overlay 无协调层；`ScheduleOverlay` 未做左右向 entrance/exit；选时间 Activity 未临时藏窗。
- **解决方法**：`OverlayLayerCoordinator`（点哪边抬哪边；切层先 `retractToEdge` 再 `expandFromEdge`）；左窗镜像右窗动画（负 `translationX` + Overshoot/Accelerate）；`ScheduleTimePickActivity` onCreate/onDestroy 调 `hideForTimePicker` / `restoreAfterTimePicker`。语音补时间不藏窗。
- **后续**：真机确认切层与选时间不挡。

### 2026-09-12 — 日程点选时间崩溃 + 语音后无法入库

- **功能 / 上下文**：`docs/features/11-day-schedule.md` / `ScheduleOverlay`
- **症状**：补时间面板点「点选起止时间」直接崩；语音补时间后日程加不进去。
- **最终原因**：
  1. 悬浮窗 `Context` 上弹 `TimePickerDialog` 无 Activity window token → BadToken / 崩
  2. `commitDrafts` 走审查时把**新建**日程也按「开场前 1 小时锁定」拒绝
  3. 语音对齐后 `renderFillPanel` 在时间齐了时直接 `GONE` 面板，**未调用确认入库**
- **解决方法**：`ScheduleTimePickActivity` 选时间；新建用 `changeKind=create` 跳过锁定；面板保持到点确认 / 语音齐了自动 `confirmFillTimes`。

### 2026-09-12 — Active 拦截无效 + 重新分析极慢（非大爆炸误伤）

- **功能 / 上下文**：`docs/features/05-habit-guardian.md` / `BlockerService` / `AppStoreMetaFetcher`
- **症状**：无障碍已开、微信目录也有 Active，进朋友圈仍不拦；点「重新分析」要等很久。用户怀疑大爆炸修网络时带坏。
- **尝试过的方法**：
  1. 查 `HabitLlmClient`（大爆炸同源）——分析商店走的是 `AppStoreMetaFetcher`，与 LLM DNS 无关
- **最终原因**：
  1. **debounce 只按 package**：同包从 `LauncherUI` 切到朋友圈/视频号时，800ms 内第二次检查被吞 → Active 永远不评估
  2. **`SHELL_MIXED` 先于 Active 命中**：朋友圈页无障碍树仍含「发现/微信」，直接 `watch()`，Active 类名匹配轮不到
  3. **目录被冲掉**：全量启发式把 `FinderChattingUI` 等标成娱乐，真正的 `SnsTimeLineUI` / `FinderHome*` 种子丢失
  4. **工作时段默认 09–18**：晚上测自然不拦
  5. **分析慢**：每个候选包先连 `play.google.com`（国内常 8s 超时）× 最多 40 包；微信再枚举 2000+ Activity
- **解决方法**：
  - `shouldHandleAppCheck`：作息监视包在 **Activity 类名变化** 时强制再评估
  - `evaluateCommunication`：Active 类名命中优先于 `SHELL_MIXED`
  - `forceSeedEntertainmentActives` 强制保留朋友圈/视频号种子；剔除 `*ChattingUI` 误标
  - 默认跳过 Google Play；已知包本地短路；通讯 Active 裁剪 ≤120
- **后续**：测 Active 须在工作时段内（可在作息页改）；勿把慢分析归到大爆炸。装机后可用种子目录验证，勿立刻「重新分析」冲掉种子。

### 2026-09-12 — 桌面被标娱乐 + 微信整包禁 + 该拦不拦

- **功能 / 上下文**：`docs/features/05-habit-guardian.md` / `AppStoreMetaFetcher` / `HabitPolicyStore`
- **症状**：桌面（`com.android.launcher3`）被当成娱乐软件整包禁；微信/QQ 被标 `ENTERTAINMENT+BLOCK`；朋友圈该拦却不拦；用户以为旧功能被 `rm` 掉。
- **尝试过的方法**：
  1. 多次手改 prefs / 重新分析（结果：商店超时慢、且无障碍未开时拦截根本不跑）
- **最终原因**：
  1. iTunes 按显示名瞎匹配：launcher3→游戏；微信 `Social Networking` 曾被裸 `"social"` 关键字判成娱乐
  2. 微信被误标娱乐后写入空 Active `[]`，`hasCatalog` 仍为 true，永不重扫朋友圈/视频号
  3. 设备上 **守伴无障碍未启用**（仅 NovaText），`HabitHook`/`BlockerService` 不跑 → 任何策略都拦不住
  4. 视频细判曾归档到 `GuardPet/archived/video-content-judge/`（复制归档，不是删除）；已恢复 `VideoGuard` + `SEARCH_ONLY`
- **解决方法**：
  - `StoreCategoryMapper`：`Social Networking`→通讯；去掉裸 `social`；跳过 launcher/系统包商店查询
  - `HabitPolicyStore.sanitizePolicy`：读写时剥桌面规则、强制微信/B站正确 kind
  - `hasCatalog`：通讯/视频空目录视为未分析；通讯分析始终合并 `seedCommActivities`
  - `isAlwaysAllowed`/`isHomeLauncher`：桌面永不拦
  - 设备侧清洗 `habit_policy` + 种子微信 Active；**必须再开一次「守伴守护」无障碍**
- **后续**：商店未修好前勿点「重新分析」；Play 超时会拖很久。拦截依赖无障碍，关了等于整套守护停摆。

### 2026-09-12 — 还原到视频「一刀切」之前（仅搜索 + Active 面过滤）

- **功能 / 上下文**：`docs/features/05-habit-guardian.md` / `archived/video-content-judge/`
- **症状**：用户反馈一刀切后微信 Active 拦截也没了；随后补丁（prefs 手改、种子 Active、pathHeuristic）把状态弄乱。
- **本次还原**：
  1. 按归档恢复 `VideoGuard` + `evaluateVideo` + `VIDEO→SEARCH_ONLY` + `defaultVideoSurfaces` / `classifyVideoActives`（一刀切前行为）
  2. 去掉本轮临时补丁：`pathHeuristicBlock`、`seedCommActivities`、错误 html-escape 写 prefs
  3. 保留：`hasCatalog` 对空 `[]` 视为未分析（否则微信永不重扫）
- **验证**：`./gradlew :app:installDebug`；作息页「重新分析」；工作时段试朋友圈 / B 站搜索

### 2026-09-12 — 音量和弦后音量键卡住 / 弹出音量条

- **功能 / 上下文**：`docs/features/10-volume-chord-flash.md`
- **症状**：同时按音量加减进闪记后音量条仍在；松开硬件键后系统仍像在连按音量。
- **尝试过的方法**：
  1. 仅在两键都按下时 `return true`，单键 `return false`（结果：第一键 DOWN 进系统、和弦后 UP 被消费 → 事件流不完整，系统认为键未抬起）
- **最终原因**：Accessibility `onKeyEvent` 必须对同一按键的 DOWN/UP 对称过滤；半截消费会导致 sticky key。和弦前第一键若已交给系统还会弹出音量条。
- **解决方法**：音量键一律消费；单键在约 160ms 检测窗后用 `AudioManager.adjustSuggestedStreamVolume` 代调并 `FLAG_SHOW_UI`；进和弦则不调音量、不弹条。
- **后续**：单独调音量略有检测延迟；若仍 sticky，先关开一次无障碍清状态。
- **复现 / 修订（2026-09-12）**：修 sticky 后单键只能一格一格调、长按不连跳。因过滤后 `KeyEvent.repeatCount` 常不再到达；改为确认单键后 `Handler` 每 ~85ms 自驱 `adjustSuggestedStreamVolume`，UP / 进和弦时停止。

### 2026-09-12 — 音量和弦闪记需关开无障碍

- **功能 / 上下文**：`docs/features/10-volume-chord-flash.md`
- **症状**：装完新版后同时按音量加减无反应；单独音量正常。
- **尝试过的方法**：
  1. 仅重装 APK（结果：无效，因无障碍配置 XML 增加了 `flagRequestFilterKeyEvents` / `canRequestFilterKeyEvents`）
- **最终原因**：Android 对已启用的 AccessibilityService 不会自动刷新 capability；改键过滤能力后需用户关掉再打开「守伴守护」。
- **解决方法**：提示用户设置里关开一次无障碍；代码侧 `BlockerService.configureService` 与 `blocker_configuration.xml` 均已带 `FLAG_REQUEST_FILTER_KEY_EVENTS`。
- **后续**：单键音量仍不消费；仅两键同时按下时拦截。

### 2026-09-12 — 大爆炸换行乱 + 标点与正文同色

- **功能 / 上下文**：`docs/features/02-bigbang.md` / NovaText 对照
- **症状**：词块挤成一团 wrap，不像原文行；`，。「」` 与正文字块同色难辨。
- **最终原因**：`captureVisibleTextInRect` 未按 Y 聚类且含父子重复节点；`TokenFlowView` 只做宽度自动折行；标点无独立样式；jieba 常把「勇气。」粘成一块。
- **解决方法**：框选结果按屏幕 Y 聚类成行 + 去父节点；`TextTokenizer.LINE_BREAK`；`TokenFlowView` 强制换行；`token_bg_punct` 灰底弱字色；`detachPunctuation`。
- **后续**：无障碍树没有几何行信息的 App 仍可能一行过长，只能靠宽度 wrap。

### 2026-09-12 — 大爆炸页状态栏「地球划斜线」+ AI 用不了

- **功能 / 上下文**：`docs/features/02-bigbang.md` / `HabitLlmClient`（与作息「应用分类 AI」同一客户端）
- **症状**：打开文字提取页时系统状态栏出现 🌐̷；点「整理成笔记」等失败；logcat：`UDP/DoH socket failed: ECONNREFUSED`、`DnsResolver ENONET`、`ai fail: 无法解析 LLM 域名`。shell `ping`/`curl` 正常。
- **尝试过的方法**：
  1. 给 `HabitLlmClient` 加 DoH / UDP DNS / `Network.openConnection` —— 无效；`bindSocket(Network)` 反而 ECONNREFUSED
  2. 对照作息分类 AI（同 `HabitLlmClient.chatJson`）—— 通路相同，问题在系统限网而非 prompt
- **最终原因**：开发者选项 **Restricted networking mode** 开启（`settings get global restricted_networking_mode` = `1`）。守伴 UID 被标 `REJECT_ALL`，进程内任意建连被拒，状态栏即显示无网络图标。
- **解决方法**：
  1. `adb shell settings put global restricted_networking_mode 0`（或开发者选项里关掉 Restricted networking mode）
  2. `HabitLlmClient` 改为默认系统栈、DNS 回退不 `bindSocket`（与分类 AI 一致）
  3. 自测：`am start … --es debug_bigbang_text '…' --ez debug_auto_ai true` → log `BigBangAi: ai ok`
- **后续**：若再开该开发者选项，所有非白名单 App 都会同样断网；不是 DeepSeek Key 坏了。

### 2026-09-12 — 拖拽摇手机闪退（与框选抢 WM）

- **功能 / 上下文**：`docs/features/02-bigbang.md` / `PetService` `DRAG_PHONE_SHAKE`
- **症状**：拖拽桌宠时摇手机，应用停止运行。
- **最终原因**：传感器回调开框选的同时 `ACTION_MOVE` 仍 `updateViewLayout` 桌宠，与全屏 `TextCropOverlay` 抢 WindowManager。
- **解决方法**：触发后立刻 `stopPhoneShakeListen` + `suppressDragUntilUp`，MOVE 期间跳过 layout 更新；`extractText`/`registerListener` 包 `runCatching`。验证：`auto_extract` → `extractText crop shown`，进程不挂。

### 2026-09-12 — SenseVoice 模型包 / AAR / createPackageContext

- **功能 / 上下文**：`docs/features/09-sensevoice-asr.md`
- **症状**：闪记离线转写不可用；或首次拷贝模型失败；或编译缺 AAR。
- **尝试过的方法**：
  1. 把 onnx 打进 `:app` —— 否决（包体过大）
  2. 纯 curl 下 155MB 模型易中断 —— 改用 `aria2c -c -x 8` 或 `scripts/fetch-sensevoice-pack.sh`（建议走本机代理）
- **最终原因**：模型与运行时分离；主 App 需已安装 `com.geekathon.guardpet.sensevoice` 才能 `createPackageContext`；AAR 在 `GuardPet/libs/` 且 gitignore。
- **解决方法**：`./scripts/fetch-sensevoice-pack.sh` 拉 AAR+模型；`adb install` 主包与 sensevoice-pack；Manifest `<queries>` 声明 pack；`SenseVoiceModelStore.ensureLocalModel` 拷到 `filesDir/sensevoice/`。AAR 已含 onnxruntime jni，无需另加 Maven。
- **后续**：未跑 fetch 时 `:sensevoice-pack` 可编出空资源 APK，真机转写会提示未安装/拷贝失败。

### 2026-09-12 — 提取文字一点就闪退（框选 Overlay）

- **功能 / 上下文**：`docs/features/02-bigbang.md` / `TextCropOverlay`
- **症状**：触发「提取文字」后应用直接停止运行。
- **尝试过的方法**：
  1. 查旧 dropbox（FocusModeService 等）——与本次无关
  2. 对照刚加的框选层：在 `PetService` 上下文直接 `MaterialButton(...)` ——高危
- **最终原因**：`TextCropOverlay` 用无主题的 Service 上下文建 Material 按钮会 Inflate/主题崩溃；横屏时 `frameW = 屏幕短边` 可能大于屏宽，`coerceIn(0, width-frameW)` 下界>上界也会抛。
- **解决方法**：`ContextThemeWrapper(Theme.DesktopPet)` + `AppCompatButton`；框宽高 `coerceAtMost` 屏尺寸；`extractText`/`onTextCropConfirmed` 包 `runCatching`；BigBang 主题改回 `Theme.MaterialComponents` + MaterialComponents 控件样式。自测：`adb am start -n com.geekathon.guardpet/.MainActivity --ez auto_extract true`（需桌宠已有悬浮窗权）。
- **后续**：本机当时无 adb 设备，需真机再点一次确认/取消框选与结果页。

### 2026-09-12 — 大爆炸框选 + MD3/AI + 拖拽摇手机

- **功能 / 上下文**：`docs/features/02-bigbang.md`
- **症状**：用户要 NovaText 式布局、框选抓字、AI 整理、拖拽时摇手机触发。
- **解决方法**：`TextCropOverlay` + `captureVisibleTextInRect`；`BigBangActivity` MD3 底栏与 Overshoot 入场；`BigBangAi`；`PetGesture.DRAG_PHONE_SHAKE`。
- **后续**：仍非 OCR；框外纯图文字抓不到；AI 依赖作息页 Key。

### 2026-09-12 — 四类判定改以应用商店公开分类为主

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：美团等被判成工作应用；纯 LLM 易瞎猜；用户希望联网搜索或商店分类。
- **最终原因**：无商店线索时模型/启发式默认 WORK；酷安 API 需私有 Token 不适合。
- **解决方法**：分析时联网拉 Google Play `applicationCategory` / iTunes Search genres → `StoreCategoryMapper`（LIFESTYLE/购物/美食→ENTERTAINMENT）；未命中再 AI；美团等写入启发式兜底。
- **后续**：需「重新分析」；无外网时退回启发式。

### 2026-09-12 — 应用四类改为 AI 优先；小红书/酷安算娱乐

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：小红书、酷安被当成工作；用户质疑为何手写分类而非 AI。
- **最终原因**：未填 Key 或旧策略时走 `AppTierClassifier`，未知应用默认 WORK；名单原先未含 xhs/coolapk。有 Key 时虽调 LLM，但是逐个且失败静默回落。
- **解决方法**：重新分析时 `classifyPackageKindsWithAi` 批量 AI；提示词明确社区刷帖=ENTERTAINMENT；启发式补 `com.xingin.xhs` / `com.coolapk.market`；无 Key 时 Toast 提示。
- **后续**：改完需在作息页「重新分析」刷新旧规则。

### 2026-09-12 — 拦截通知改桌宠气泡

- **功能 / 上下文**：`docs/features/08-pet-block-bubble.md`
- **症状**：拦截时系统通知生硬；用户要桌宠气泡 + 更可爱文案。
- **解决方法**：`HabitHook.blockFeedback` → `PetBlockBubble` → `PetSpeechBubbleOverlay`；无悬浮窗仍回退通知。
- **后续**：用量限额等非习惯拦截仍可能走原 `showBlockedNotification`。

### 2026-09-12 — 暂停视频内容判断，视频改整包禁

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：播放页展开简介 + 标题/标签判别实现成本高，暂无时间继续。
- **解决方法**：`VideoGuard` 与相关逻辑移至 `GuardPet/archived/video-content-judge/`（不删）；`VIDEO` / 旧 `SEARCH_ONLY` 运行时按整包禁；分析与默认 B 站规则改为 `BLOCK`。
- **后续**：恢复步骤见该目录 `README.md`；已有策略里旧 `SEARCH_ONLY` 也会被整包禁。

### 2026-09-12 — B 站简介误读成视频进度/相关推荐

- **功能 / 上下文**：`docs/features/05-habit-guardian.md` / `VideoGuard`
- **症状**：展开点错、判词用了 `id/time`（如 `2025年3月9日 15:20`）、时长或下方推荐长标题，而不是简介。
- **尝试过的方法**：
  1. 点可见文案「展开」精确匹配（结果：失败——B 站无独立「展开」TextView）
  2. 用「简介」后任意长文本当简介（结果：误读相关推荐 / 进度时间）
- **最终原因**：B 站展开是标题行 contentDesc 以「，展开」结尾 + `id/arrow`；正文在展开后的 `id/desc`；标签多在 desc 末行空格分隔。旧逻辑只扫纯文本。
- **解决方法**：`BlockerService.captureVisibleNodes` / `clickByContentDescSuffix("，展开")` / `clickByViewIdSuffix("arrow")`；`VideoGuard` 只取 `id/title`+`id/desc`，过滤 `time`/`duration`；真机 uiautomator 验证过。
- **后续**：其它视频 App 仍走文案兜底；验证前可先关作息以免展开过程被返回键打断。

### 2026-09-12 — B 站等播放页改为展开简介再判

- **功能 / 上下文**：`VideoGuard` / `BlockerService.clickVisibleText`
- **症状**：仅靠标题启发式易漏/误；用户要按简介与标签判断。
- **解决方法**：播放页先点「简介/展开」；汇总标题+简介+标签对照学习/娱乐词；学习放行，娱乐返回；展开中 `watch` 等下一帧。
- **复现 / 修订**：见上条——定位改为 resource-id / contentDescription，不再盲扫长文本。
- **后续**：各 App「展开」文案不一，漏点时退回标题启发式。

### 2026-09-12 — 撤回「打开 Active 探测」，恢复按名分类

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：用户要求不要逐页打开探测，改回先前 Active 名分类。
- **解决方法**：删除 `AppActiveProbe`；通讯类恢复 `classifyCommunicationActives`；保留主壳白名单与 `SHELL_MIXED` 防 QQ/微信主页误拦。
- **后续**：需重新点「重新分析」刷新目录。

### 2026-09-12 — 通讯筛查改为「打开 Active + 整页元素」

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：仅按 Active 名易误伤主壳（如 QQ SplashActivity）；用户要求换思路。
- **解决方法**：（已撤回，见上条）曾用 `AppActiveProbe` 打开 exported Active 抓树。
- **后续**：已改回按名分类。

### 2026-09-12 — QQ 主页 SplashActivity 被整页拦截

- **功能 / 上下文**：`AppActiveCatalog` / `CommActiveLists` / `SurfaceMatcher`
- **症状**：工作时段打开 QQ 主页即被返回/拦截。
- **尝试过的方法**：
  1. 读真机 catalog（结果：`SplashActivity` 被标 ENTERTAINMENT，patterns 含「动态/更多/搜索」；面规则还有单字「空间」）
- **最终原因**：QQ 主壳就是 `SplashActivity`（公开资料一致）；LLM/面词把主页壳层入口当成娱乐面；单字「空间」命中动态页。
- **解决方法**：主壳白名单永不 `blockInWork`；QQ 主 chrome（消息+联系人+动态）不因入口字拦截；去掉「空间」单字；参考 gist 精简脚本做 QQ 娱乐路径增强。网上无完整官方 Active 对照表，只有启动页/逆向/精简脚本碎片。
- **后续**：进 QQ 空间/看点真实页仍应拦；主页应可用。

### 2026-09-12 — 娱乐 Active 收太狠后又放回（优先 sns/finder）

- **功能 / 上下文**：`AppActiveCatalog`
- **症状**：修「应用名当 pattern」后娱乐面过少；微信 SnsTimeLine/视频号主路径未进 block 集。
- **尝试过的方法**：
  1. 真机 install + `HABIT_ANALYZE` 拉目录（结果：上限 60 误裁 sns/finder）
- **最终原因**：`sanitize` 用布尔排序截断；弱词 `feed` 误伤飞书；无 Key 时 analyze 直接拒绝。
- **解决方法**：强信号（sns/finder/qzone 等）全保留；弱信号限额；路径映射中文入口词；无 Key 也可本地启发式分析；补微信/QQ canonical 面词。真机启发式结果：微信 block≈448（sns74+finder343），QQ≈60，飞书≈16（Moments/直播）。
- **后续**：本机微信已无 `SnsTimeLineUI` 类名，主时间线为 `ImproveSnsTimelineUI` 等，已覆盖。

### 2026-09-12 — 娱乐 Active 几乎标成整包（pattern=应用名）

- **功能 / 上下文**：`docs/features/05-habit-guardian.md` / `AppActiveCatalog`
- **症状**：真机目录里微信 2072 Active 中 409 个 `blockInWork`，面规则 pattern 全是「微信」；飞书/QQ 同理。等于工作时段整包误拦。
- **尝试过的方法**：
  1. 读 `shared_prefs/app_active_catalog.xml` + `habit_policy.xml`（结果：确认）
- **最终原因**：
  1. `loadLabel` 常等于应用名，LLM/启发式把大量 Activity 标成娱乐后用应用名当 KEYWORD。
  2. `matchBlockingActive` 对 pattern 做 `contains`。
  3. `upsertAgentKind(WORK)` 在 surfaces 为空时保留旧面规则。
- **解决方法**：标签回退为短类名；禁止应用名作 pattern；娱乐标记需强信号并上限；文本匹配改为 equals；工作/整包禁清空旧 surfaces。需在设置页「重新分析」刷新旧脏数据。
- **后续**：已分析过的脏目录不会自动清，用户需点重新分析或删规则。

### 2026-09-12 — 工作时段改为四类应用筛选

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：原逻辑偏「按 Active 名一刀切」，未区分工作/娱乐/通讯/视频产品策略。
- **尝试过的方法**：
  1. 仅按 Active 名禁用（结果：不符合「通讯筛娱乐、视频仅搜索」）
- **最终原因**：缺少应用层四类（`AppGuardKind`）与视频 `SEARCH_ONLY` 路径。
- **解决方法**：先 `AppTierClassifier`/`LLM` 定类；WORK 不限；ENTERTAINMENT 整包禁；COMMUNICATION 面过滤；VIDEO 仅搜索 + 遮挡/返回 + 娱乐内容返回。新增 `HabitRestrictOverlay`。
- **后续**：搜索页识别依赖 Activity 名与可见「搜索」文案，OEM 皮肤差异需用 a11y-inspect 校准。

### 2026-09-12 — 规则可删除并按 Active 名重分析；不确定面改抓页

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：应用规则页无法删除已确定规则；「重新分析」未按 Active 名走 DeepSeek；不确定项主要靠确认弹窗。
- **尝试过的方法**：
  1. 仅 HabitAgent 摘要刷新（结果：不覆盖 Active 分类流程）
- **最终原因**：缺 `removeRule`/`resetAndReanalyze` 与名分析主路径；页判未接上 evaluate。
- **解决方法**：规则行「删除」→ `AppActiveCatalog.resetAndReanalyze`；「重新分析」→ `analyzeAllByNamesAsync`；`HabitGuardian` 对 UNCERTAIN 进页后 `resolveUncertainOnPage`（启发式 + 可选 LLM）。
- **后续**：Activity 名不等于全部 UI 面；页判依赖 a11y 可见文字。

### 2026-09-12 — 面级禁用改返回键；新应用 Active 交 DeepSeek

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：面禁用若按 Home，用户无法继续使用同一 App（如微信聊天）；且缺少按 Active 分类能力。
- **尝试过的方法**：
  1. 一律 HOME（结果：不可用）
- **最终原因**：面级拦截与整包拦截动作应区分。
- **解决方法**：`HabitEval.pressBack`；工作时段 Active/面禁用 → `GLOBAL_ACTION_BACK`；催睡/整包仍 HOME。新包 `AppActiveCatalog` 枚举 Activity → LLM/启发式；不确定项进页抓元素再判（修订：不再以确认弹窗为主）。
- **后续**：Activity 清单不等于全部 UI 面，仍依赖关键词/页抓补全。

### 2026-09-12 — 界面检查改为电脑端 adb 网页工具

- **功能 / 上下文**：`tools/a11y-inspect/README.md`、`docs/features/05-habit-guardian.md`
- **症状**：曾考虑在手机内嵌 HTTP 检查页；用户只要电脑端，用网页连 adb。
- **尝试过的方法**：
  1. 手机 `HabitInspectStore` / a11y dump API（结果：按需求废弃）
- **最终原因**：产品只需本机调试工具，不必增加手机攻击面与 FGS/网络权限。
- **解决方法**：`tools/a11y-inspect/server.py`（Python + adb + uiautomator dump）；删除手机端检查残留。
- **后续**：用该工具记录真实「朋友圈」节点特征后再收紧种子规则。

### 2026-09-12 — 作息守护导致「一打开 App 就回桌面」

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：启用作息守护后，打开微信等应用立刻被踢回桌面；或夜间触发催睡后，白天几乎所有 App 也无法打开。
- **尝试过的方法**：
  1. 核对是否「只拦娱乐面」设计（结果：实现偏严）
- **最终原因**：
  1. `SurfaceMatcher` 对整屏文字 `contains("朋友圈"/"视频号")`，微信聊天页 a11y 树里残留入口字样即误拦。
  2. `sleepLockActive` 黏住后对**所有**非紧急包 HOME；无障碍进程常驻时白天未清锁。
  3. 夜窗 `SCHEDULE_WHITELIST` 曾把非白名单应用一律踢回（过宽）。
- **解决方法**：KEYWORD 改为节点 equals 匹配；催睡锁仅在睡眠窗内生效、触发后禁全部非紧急 App，出窗清锁；夜窗有日程时只限娱乐；另提供电脑端 `tools/a11y-inspect` 用 adb 校准节点。
- **后续**：真正进入朋友圈/视频号页仍应能拦；若漏拦再用检查工具记录节点特征后补规则。设计上白天面过滤只作用于策略包的具体界面。

### 2026-09-12 — Habit 标记采样需先退出设置页

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：在「作息守护」页直接点「标记娱乐面」时，前台包是守伴自己，采到的是设置页文字。
- **尝试过的方法**：
  1. 用当前 `BlockerService` 前台包即时采样（结果：失败）
- **最终原因**：标记 Activity 自身占据前台。
- **解决方法**：`HabitGuardianActivity` 改为 `moveTaskToBack` 后延迟约 2.8s 再 `captureVisibleText`；需用户先切到目标界面。
- **后续**：启发式标题拦截仍可能漏拦/误拦，依赖测试模式与手动标记校准。

### 2026-09-12 — 午夜后 isPastBedtime 原逻辑不成立

- **功能 / 上下文**：`docs/features/05-habit-guardian.md`
- **症状**：旧代码 `isPastBedtime = now >= 23:00`，00:00–07:00 不会进 SLEEP_LOCK，只可能 BAN。
- **尝试过的方法**：
  1. 阅读 `HabitGuardian` / `TimeBehaviorConfig`（结果：确认缺陷）
- **最终原因**：跨午夜比较用错。
- **解决方法**：`TimeBehaviorConfig.isPastBedtime` 改为与 `isSleepTime` 一致（整段夜窗）；睡眠起止改由 `HabitPolicyStore` prefs 可配。
- **后续**：无。

### 2026-09-12 — 工作区 Git 元数据不可用

- **功能 / 上下文**：项目审阅与变更前检查
- **症状**：在 `/home/rong/Geekathon` 及 `GuardPet/` 执行 `git status`、`git log` 时均提示 `fatal: not a git repository`；仓库根目录的 `.git/` 目录为空，无法读取提交历史或确认工作区差异。
- **尝试过的方法**：
  1. 在项目根目录 `/home/rong/Geekathon` 执行 Git 查询（结果：失败）
  2. 在产品工程目录 `/home/rong/Geekathon/GuardPet` 执行 Git 查询（结果：失败）
- **最终原因**：当前工作区只提供了空的 `.git/` 目录，没有有效的 Git 元数据。
- **解决方法**：本次审阅改用文件、功能文档、排障记录和现有构建产物进行静态检查；未执行提交、重置或其他 Git 写操作。
- **后续**：若要查看历史、差异或安全回滚，需要恢复有效的 Git 元数据后再操作。

### 2026-09-12 — 闪记关闭动画末帧闪一下完整悬浮窗

- **功能 / 上下文**：`docs/features/04-flash-note.md`
- **症状**：关闭侧边闪记时，滑出淡出播完后会短暂闪回未关闭的完整面板，再消失
- **最终原因**：`dismissImmediate()` 里 `cancelAllOverlayAnimations()` 把各子 View 的 `alpha`/`translationX` 重置成可见态，卸窗前多画了一帧
- **解决方法**：卸窗路径取消动画时不重置可见属性；根 View 先 `GONE`/`alpha=0` 再 `removeView`

### 2026-09-12 — 其他 App 上层点闪记播放仍无声

- **功能 / 上下文**：`docs/features/04-flash-note.md`
- **症状**：守伴主页在前台时播放有声；切到微信再点闪记卡片播放，AudioTrack 在跑但听不到。日志：`AudioHardening background playback would be muted`
- **尝试过的方法**：
  1. 给 `PetService` 加 `mediaPlayback`；AudioTrack 用 `USAGE_MEDIA` / `USAGE_ASSISTANCE_SONIFICATION` + `FLAG_AUDIBILITY_ENFORCED` + 内置扬声器（结果：主页有声，其他 App 上层仍无声）
  2. 对照声物记：整窗 `TYPE_ACCESSIBILITY_OVERLAY` + `audioplayers`（结果：守伴不能再注册第二条 AccessibilityService，也不把闪记 HUD 整窗迁过去）
- **最终原因**：Android 16（本机为后刷原生 AOSP，不是 ColorOS）AudioHardening 按 uid 看有没有 resumed Activity。只有 FGS / overlay 窗口不够。`write==0` 时直接 break 还会提前停轨。
- **解决方法**：overlay 点击后由已有 `BlockerService`（没有则 `PetService`）拉起 1px 透明 `FlashNotePlayActivity`（独立 `taskAffinity`）。真正的 `AudioTrack` 只在这个 Activity 里建。`write==0` 时等待重试。播完 `finish`。
- **后续**：真机已确认其他 App 上层可播。不要把该限制写成 ColorOS 专有。

### 2026-09-12 — 番茄钟 overlay 启动即崩溃

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

## 2026-09-12 闪记要悬浮窗写作，但 IME 需要焦点

**症状：** 纯 `FLAG_NOT_FOCUSABLE` overlay 里的 `EditText` 弹不出键盘。

**试过：** 继续用 `FlashNoteActivity`（能输入，但不是悬浮卡片）。

**原因：** 无焦点 overlay 不把按键和 IME 交给自己的窗口。

**办法：** 列表浏览时保留 `FLAG_NOT_FOCUSABLE`，点「在悬浮窗写作」后去掉该 flag，只留 `FLAG_NOT_TOUCH_MODAL`，窗口仍是右侧 `WRAP_CONTENT`。麦克风权限用独立 `taskAffinity` 的透明 `MicPermissionActivity`，避免把守伴主页抬上来。

---

## 2026-09-12 语音识别没有录音文件可回放

**症状：** `SpeechRecognizer` 只能出字，卡片无法「重放录音」。

**原因：** 系统听写一般不把音频交给应用；`MediaRecorder` 和 `SpeechRecognizer` 也不能同时占麦克风。

**办法：** 「录音」走 `MediaRecorder` 存 `m4a`，「语音输入」仍用听写填字。有 `audio_path` 的卡片才显示重放 / 暂停 / 继续。盖在别的 App 上录音时，给已有 `PetService` 临时加上 `microphone` FGS 类型（不是第二条服务），停录后改回 `specialUse`。

---

## 2026-09-12 闪记录音能保存但点重放没声音

**症状：** 展开卡片能看到「重放录音」，点了没有声音，按钮也不变成暂停。

**原因：**

1. `MediaRecorder.stop()` 之后又 `reset()`，部分机型上 m4a 文件头不完整。
2. `MediaPlayer.setDataSource(path)` 读应用私有目录不稳定；默认音频流也不一定是媒体音量。
3. 播放失败被 `runCatching` 吃掉，界面看起来像没反应。

**办法：** Android 16 AudioHardening 会静音没有前台 Activity 的后台 `STREAM_MUSIC`（本机是后刷原生 AOSP，不是 ColorOS）。录音 WAV 文件本身是有波形的。播放时给已有 `PetService` 临时加上 `mediaPlayback`，AudioTrack 用 `USAGE_ASSISTANCE_SONIFICATION` + `FLAG_AUDIBILITY_ENFORCED`。仅 FGS 仍不够，见上一条 `FlashNotePlayActivity`。

- **复现 / 修订**：曾误写成 ColorOS 专有限制。真机为后刷原生 Android 16。
## 2026-09-13：Windows 中文路径导致 Gradle 启动前失败

- 症状：在 `C:\Users\ZHY\Desktop\AI桌宠\...` 下执行 `:app:assembleDebug --offline` 时，Android Gradle Plugin 报 `project path contains non-ASCII characters`，尚未开始 Kotlin/资源编译。
- 原因：AGP 在 Windows 默认拒绝包含非 ASCII 字符的工程路径。
- 解决：在 `GuardPet/gradle.properties` 增加 `android.overridePathCheck=true`，允许当前本地路径继续构建。发布/协作环境仍建议使用纯英文路径。
