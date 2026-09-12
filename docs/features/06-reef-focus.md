# Reef 拦截与配置

## 用户可见行为

用量统计、白名单、网站拦截、例行计划、番茄配置。产品里叫「专注配置」，不要把应用自称成 Reef。

## 入口

守伴主页「专注配置」→ `FocusLauncher.openSettings()`。  
缺权：`PermissionsCheckActivity`（`MainActivity.checkAndRequestMissingPermissions()`）。

## 模块与关键类

- `dev.pranav.reef.MainActivity`（`navigate_to_settings`）
- `BlockerService`、`Whitelist`、`AppLimits`
- `Permissions.kt`

## 权限

无障碍、用量、通知、电池优化、勿扰、悬浮窗

## 不要做的事

改库时保持 MIT 署名。不要用 Reef 图标替换守伴图标。不要从桌宠再拉起 `FocusModeService` 当第二条 FGS。

## 验证步骤

从主页能进配置页；缺权能进授权列表并逐项授予。
