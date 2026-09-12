# 番茄钟 overlay

## 用户可见行为

当前应用上方：左上角绿色拉环，屏幕中央带阴影的 60 分模拟钟，时间写在表盘内。拉动拉环伸长并改时长；点时钟开始/暂停；长按关闭。拉环和时钟以外的区域可点下层应用。

## 入口

桌宠菜单 / 控制小窗 / 主页「番茄钟」（需桌宠已开）。主页「专注配置」进 Reef 设置，不是这个 overlay。

## 模块与关键类

- `FocusTimerOverlay`、`PullTabView`、`AnalogTimerView`
- 拦截：`prefs["focus_mode"]`，由 `BlockerService` 读取

## 权限

悬浮窗；拦截另需无障碍 + 用量访问

## 不要做的事

不要 `startForegroundService(FocusModeService)`（第二条 specialUse FGS + 未初始化 `App.colorScheme` 会崩）。不要用全屏 overlay。

## 验证步骤

拉环在左上且拉动时绿带变长、指针和表盘数字一起变；开始后分心应用被拦；下层空白处可点。
