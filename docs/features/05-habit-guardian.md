# 夜间作息守护

## 用户可见行为

夜间按规则禁娱乐应用、催睡锁机、或只放行与今日日程相关的应用。锁机层可打紧急电话。

## 入口

无独立开关页：`BlockerService` 每次切前台走 `HabitHook` → `HabitGuardian`。

## 模块与关键类

- `HabitGuardian`、`HabitHook`
- `SleepLockOverlay`
- 日程来源：`FlashNoteStore` 里带日期的日程

## 权限

用量访问、无障碍

## 不要做的事

不要在 `BlockerService` 里写死守伴规则；用 hook。不要拦拨号盘/输入法/守伴自己。

## 验证步骤

写一条今夜日程后，夜间打开无关娱乐应用应被拦；紧急电话入口可用。
