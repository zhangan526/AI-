# 桌宠 overlay 与手势

## 用户可见行为

守伴以 `TYPE_APPLICATION_OVERLAY` 显示在其他应用上层。可拖拽、自由行走、到点睡觉。手势在主页「快捷手势」里绑定功能。

默认：

- 单击 → 换样式
- 双击 → 功能菜单（右上角 X 关闭）
- 摇晃 → 摸摸
- 双击后长按 → 控制小窗

每个手势的下拉项包含上述原功能，以及提取文字、闪记、番茄钟。

## 入口

- 主页「开启桌宠」（需悬浮窗权限）
- 主页「快捷手势」四个 Spinner

## 模块与关键类

- `PetService`：FGS `specialUse`，唯一允许的该类型服务；`PetTouchListener` 识别手势
- `PetAction` / `PetGesture` / `PetSettings.actionFor`
- `PetCanvas` / `PetAssetRepository`
- `PetMenuOverlay`、`PetPanelOverlay`

## 权限

`SYSTEM_ALERT_WINDOW`；Android 13+ 通知权限（FGS 通知）

## 不要做的事

不要再开一条 specialUse FGS。不要改 `MIKU-仅参考/`。不要把拖拽移动做成可替换快捷项（会没法挪位置）。

## 验证步骤

默认：单击换样式、双击菜单能按 X 关掉、摇晃摸摸、双击后长按出控制窗。改绑提取文字后，对应手势应打开大爆炸。
