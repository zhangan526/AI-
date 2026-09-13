# 功能文档索引

每个功能一篇开发文档，供其他 agent 在不读原聊天的情况下改对模块。

- 新功能：复制 [`_TEMPLATE.md`](_TEMPLATE.md) → `<id>-<slug>.md`，填完后在下表加一行。
- 工作流：仓库根目录 [`AGENTS.md`](../../AGENTS.md)
- 排障：[`../TROUBLESHOOTING.md`](../TROUBLESHOOTING.md)

尚未动手的能力不要空造一篇；改到某个功能时再按模板补文档。

## 索引

| id | 文档 | 状态 | 模块 |
|----|------|------|------|
| 01 | [桌宠 overlay 与手势](01-pet-overlay.md) | 已接入 | `:app` `PetService` |
| 02 | [大爆炸提取文字](02-bigbang.md) | 已接入 | 框选 + MD3 词块 + AI；`:app` + `BlockerService` |
| 03 | [番茄钟 overlay](03-focus-timer.md) | 已接入 | `:app` overlay；拦截用 `focus_mode` |
| 04 | [闪记](04-flash-note.md) | 已接入 | `:app` `FlashNoteStore` |
| 05 | [作息与习惯守护（Habit Agent）](05-habit-guardian.md) | 已接入 | `:app` Agent/面过滤 + `:reef` HabitHook；视频 `VideoGuard` 仅搜索 |
| 06 | [Reef 拦截与配置](06-reef-focus.md) | 已接入 | `:reef` |
| 07 | [MikuUI 现代主页 chrome](07-miku-ui.md) | 已接入 | `:app` |
| 08 | [拦截气泡（桌宠说话）](08-pet-block-bubble.md) | 已接入 | `:app` 气泡 + `:reef` HabitHook |
| 09 | [离线 SenseVoice ASR（闪记）](09-sensevoice-asr.md) | 已接入 | `:app` + `:sensevoice-pack` |
| 10 | [音量加减和弦打开闪记](10-volume-chord-flash.md) | 已接入 | `:app` + `BlockerService` `onKeyEvent` |
| 11 | [今日日程（富日程）](11-day-schedule.md) | 已接入 | `:app` 左 overlay + AI；Habit 时段 allow/block |
| 12 | [宠物之家小游戏（跑酷 / 俄罗斯方块）](12-pet-home-games.md) | 已接入 | `:app` 宠物之家、普通 Activity 小游戏 |
| — | [_TEMPLATE.md](_TEMPLATE.md) | 模板 | — |
