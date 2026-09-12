# 守伴 Agent Guide

给后续 agent 的工作约定。改代码前先读本文件、对应功能文档、以及 `docs/TROUBLESHOOTING.md`。

本仓库是 Geekathon 项目 **守伴**：陪伴守护形 Android 桌宠。  
对外应用名 **守伴**，包名 `com.geekathon.guardpet`，工程目录 `GuardPet/`。

产品主线是「桌宠 overlay + 专注拦截 + 夜间作息 + 大爆炸抓字」。专注/拦截能力来自开源 **Reef**（MIT 副本在 `GuardPet/reef/`）。Reef 只是库，不是产品名。

---

## 硬性规则

1. **不要把 `MIKU-仅参考/` 当成要改的工程。** 那是桌宠骨架对照树，只读。产品代码只写 `GuardPet/`。
2. **可见品牌是「守伴」，不是 Reef、不是 MIKU。** 不得把 Reef 名称或官方图标当应用名/启动图标。设置或关于页必须保留 Reef MIT 署名与仓库链接：`https://github.com/aload0/Reef`。
3. **每做一个功能，必须写一篇功能开发文档**（见下方模板）。文档要让没读过本次对话的 agent 能独立改对模块。
4. **每次卡住、踩坑、误判，必须记入 `docs/TROUBLESHOOTING.md`。** 记症状、试过的方法、最终原因、解决办法。不要只记在聊天里。
5. **不要新开第二条 `specialUse` 前台服务。** `PetService` 已经占用该类型。番茄钟倒计时在 overlay 里跑，拦截只写 `prefs["focus_mode"]`。不要从桌宠再 `startForegroundService(FocusModeService)`。
6. **全应用只有一个无障碍服务：`BlockerService`。** 抓字、拦截、夜间守护共用它。不要再注册第二个 AccessibilityService。
7. **大爆炸不是悬浮窗。** 用独立 `taskAffinity` 的透明 Activity（NovaText 做法），让下层应用画面仍可见。禁止用 `TYPE_APPLICATION_OVERLAY` 做整页选词。
8. **不要提交 git commit，除非用户明确要求。**
9. **不要改** `~/.cursor/plans/` 里的计划文件。功能范围以本文件、思维导图和 `docs/features/` 为准。

---

## 目录与真相源

| 路径 | 角色 |
|------|------|
| `GuardPet/` | **产品工程**（Gradle 工程名 GuardPet） |
| `GuardPet/app/` | 守伴 UI、桌宠、大爆炸、闪记、夜间规则、番茄钟 overlay |
| `GuardPet/reef/` | Reef MIT 副本：拦截、用量、白名单、配置页、权限页 |
| `GuardPet/appintro/` | Reef 引导库，随 `:reef` 编译 |
| `GuardPet/third_party/reef/` | 上游说明与许可证原文，只读 |
| `MIKU-仅参考/` | 桌宠对照工程，**只读** |
| `守伴桌宠-功能思维导图.drawio` | 产品功能图（飞书可导入） |
| `docs/features/` | **每个功能一篇开发文档** |
| `docs/TROUBLESHOOTING.md` | **固定排障日志** |

`GuardPet/app` 的 Application 是 `dev.pranav.reef.App`（manifest `tools:replace`）。守伴自己的初始化走 `GuardInitProvider`（闪记库、`HabitHook`、jieba 目录），不要再强行换 Application 子类，除非同时改 Reef `App` 与清单。

---

## 模块边界（必须）

### `:app`（`com.geekathon.guardpet`）

桌宠、手势、闪记、大爆炸、夜间 `HabitGuardian`、番茄钟 **外观 overlay**。  
入口：`MainActivity`。桌宠：`PetService`。

当前手势（主页「快捷手势」可改绑定；括号内为默认）：

- 拖拽：移动桌宠并记位置（固定，不占用快捷项）
- 单击：换样式
- 双击：功能菜单（提取 / 闪记 / 番茄钟 / 控制小窗）；菜单右上角 X 关闭
- 摇晃：摸摸
- 双击后长按：控制小窗
- 自由行走：`PetSettings.edgeWalkEnabled`，默认开

可绑功能：`PetAction`（换样式、功能菜单、控制小窗、提取文字、闪记、番茄钟、摸摸）。手势检测在 `PetService.PetTouchListener`。

### `:reef`（`dev.pranav.reef`）

拦截、用量、白名单、网站、Reef 配置页、`PermissionsCheckActivity`。  
对外入口保留在守伴主页「专注配置」：`FocusLauncher.openSettings()` → `navigate_to_settings`。

改拦截逻辑时走 `HabitHook`，不要在 `BlockerService` 里写死守伴规则。`GuardInitProvider` 已注册：

```kotlin
HabitHook.evaluator = { host, pkg -> HabitGuardian.evaluate(host, pkg) }
```

无障碍说明文案用守伴口吻（「守伴守护」），不要显示成 Reef 应用。

---

## 关键交互约定（必须）

### 大爆炸（提取文字）

1. `PetService.extractText()` 先藏桌宠，延迟后 `BlockerService.captureVisibleText()`。
2. `TextTokenizer`（jieba，失败则词典 FMM）写入 `TextCaptureHolder`。
3. 启动 `BigBangActivity`：透明主题、独立 `taskAffinity`、`singleInstance`、`excludeFromRecents`。
4. 词块：点选切换；拖连选；从已选词上拖是反选；有「反选」按钮。
5. 结束后 `ACTION_SHOW_PET` 再显示桌宠。

不要改回全屏 overlay 选词页。透明 Activity 必须自己的 task，否则会把守伴主页整页抬到当前应用前面。

### 番茄钟 overlay

两块 `WRAP_CONTENT` 悬浮窗，其余区域把触摸交给下层应用：

- 左上角 `PullTabView`：`gravity=TOP|START`，`x=0,y=0`，拉动伸长绿带并改分钟
- 屏幕中央 `AnalogTimerView`：阴影表盘，时间写在表盘内

倒计时本地 `Handler`。开始时 `focus_mode=true`，结束/关闭时清掉。  
全屏 `MATCH_PARENT` overlay 会吞掉下层触摸，不要用。

### 权限

缺权时弹出 Reef 的 `PermissionsCheckActivity`（已含悬浮窗项）。守伴 `MainActivity.onResume` 会调 `checkAndRequestMissingPermissions()`。新增权限要同时改 `PermissionType`、`checkAllPermissions()`、授权页 `when`。

---

## 新功能标准流程

1. 查重：`docs/features/README.md`、思维导图、现有 `:app` / `:reef` 类
2. 写 `docs/features/<id>-<slug>.md` 草稿
3. 实现到对应模块（桌宠/闪记/大爆炸 → `:app`；拦截/用量/配置 → `:reef` + hook）
4. 补全文档与索引
5. 验证：`./gradlew :app:assembleDebug`（在 `GuardPet/`）
6. 踩坑写入 `docs/TROUBLESHOOTING.md`

功能开发文档模板：

```markdown
# <功能名>

## 用户可见行为
## 入口（手势 / 主页按钮 / 配置页）
## 模块与关键类
## 权限
## 不要做的事
## 验证步骤
```

---

## 日常命令

在 `GuardPet/` 下：

```bash
./gradlew :app:assembleDebug
./gradlew :app:installDebug
```

JDK 17、compileSdk 37、minSdk 26。Gradle 代理写在 `GuardPet/gradle.properties`（`127.0.0.1:7890`）。

真机验证优先：桌宠 overlay、透明大爆炸、番茄钟下层可点，模拟器经常看不到完整悬浮窗行为。

---

## 完成前自检

- [ ] 改的是 `GuardPet/`，没有动 `MIKU-仅参考/`
- [ ] 用户可见名仍是 **守伴**；Reef 署名仍在
- [ ] 没有新增第二条 specialUse FGS，也没有第二个无障碍服务
- [ ] 大爆炸仍是透明独立 task Activity；番茄钟仍是左上拉环 + 中央时钟
- [ ] `docs/features/` 已更新
- [ ] 坑已写入 `docs/TROUBLESHOOTING.md`
- [ ] `./gradlew :app:assembleDebug` 通过
- [ ] 未在未要求时 commit
