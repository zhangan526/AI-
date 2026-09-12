# Miku UI 独立源码版本

## 用户可见行为

导入用户提供的 UI 修改版完整源码，包括浅绿色卡片主页、状态徽章、折叠设置、互动按钮和待办小窗。原包应用名称为 miku，实际版本为 1.8.0。

## 入口

在 `codex/ui-miku-modern` 分支，用 Android Studio 单独打开 `MikuUI/`。

## 模块与关键类

`MikuUI/app` 是独立工程，包名为 `com.example.desktoppet`。主页为 `MainActivity`，悬浮宠物为 `PetService`，待办入口为 `PetPanelActivity` / `PetPanelOverlay`。

## 权限

原包声明悬浮窗、前台服务与通知权限。该应用和 `GuardPet` 分开构建、安装。

## 不要做的事

本次是用户明确要求的原样导入，不将此目录并入 `GuardPet/settings.gradle.kts`，不替换 GuardPet 的包名、应用入口、服务和功能模块。MikuUI 不包含守伴的抓字、闪记与 Reef 专注能力。

## 验证步骤

在 `MikuUI/` 执行 `gradlew.bat :app:assembleDebug`。本次已编译通过，详细来源、版本与验证记录见 [分支说明](../miku-ui-branch.md)。真机悬浮窗和待办交互尚未验证。
