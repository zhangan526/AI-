# 守伴 · DeepSeek Agent 接口版（2026-09-12）

## 版本与来源

- Git 分支：`codex/guardpet-deepseek-agent-20260912`。
- 基线：`main` 的 `7bf62be2b35d2b6affbdee2cec7ea9b1678b0f4b`，包含此前的启动修复。
- 更新包：`守伴-DeepSeek接口版-更新文件-20260912-121844.zip`，19 个文件，属于增量包。
- 压缩包 SHA-256：`D954052AF508AFC8036CC9B94199636D0710EB32EB875AC534910AB945572B90`。
- 导入时与原包逐文件核对；19 个文件内容一致。额外整理内容位于仓库首页、`GuardPet/README.md` 和本说明，未改动包内的 Agent 逻辑。
- 应用包名仍为 `com.geekathon.guardpet`，原包 `versionName=1.0.0`、`versionCode=1` 未变；本次通过分支名、提交和文档区分版本。

完整工程由基线文件加上这 19 个更新文件组成，直接打开本分支的 `GuardPet/` 即可。与 `codex/ui-miku-modern` 中独立的 `MikuUI/` 工程分开，此分支不是 Miku 新 UI 的合并版本。

## 19 个更新文件的作用

下表路径以仓库根目录为起点；类名指向 `GuardPet/app/src/main/java/com/geekathon/guardpet/`，布局和字符串位于 `GuardPet/app/src/main/res/`。

| 分组 | 文件 | 作用 |
| --- | --- | --- |
| 新增 Agent 代码（4） | `AiAgentActivity.kt`、`DeepSeekAgent.kt`、`ApiKeyStore.kt`、`AgentTools.kt` | 聊天页面、HTTPS 请求、AES-GCM/Android Keystore 密钥保存、动作解析与校验 |
| 入口连接（3） | `MainActivity.kt`、`PetMenuOverlay.kt`、`PetService.kt` | 主页与桌宠菜单打开 Agent；保留现有启动逻辑 |
| 界面资源（4） | `layout/activity_ai_agent.xml`、`layout/activity_main.xml`、`layout/overlay_pet_menu.xml`、`values/strings.xml` | 新增聊天界面、按钮和中文提示 |
| 构建与权限（4） | `GuardPet/app/src/main/AndroidManifest.xml`、`GuardPet/app/build.gradle.kts`、`GuardPet/gradle/libs.versions.toml`、`GuardPet/gradle.properties` | 网络权限、Activity 注册、OkHttp 4.12.0、协程 Android 1.10.2；代理改为可选、启用中文路径检查绕过 |
| 功能与排障记录（4） | `docs/features/07-ai-network-dependencies.md`、`docs/features/08-deepseek-agent.md`、`docs/features/README.md`、`docs/TROUBLESHOOTING.md` | 原包提供的依赖说明、Agent 说明、索引及环境记录 |

## 当前接口与执行流程

当前代码固定请求 `https://api.deepseek.com/chat/completions`，模型字符串为 `deepseek-flash`，`stream=false`。这是对源码配置的记录；本次没有验证该模型对实际账号是否可用。

`AiAgentActivity` 收集消息 → `DeepSeekAgent` 在后台线程请求模型 → 读取回复中的 JSON → `AgentTools` 解析动作 → Activity 执行并显示本地执行结果。

| action | 本地行为 | 现有条件 |
| --- | --- | --- |
| `none` | 显示回复 | 不执行动作 |
| `pet` | 切换为摸摸状态 | 桌宠已开启 |
| `feed` | 扣除食物、增加饱食度与心情，发送喂食状态 | 桌宠已开启且食物大于 0 |
| `sleep` | 发送睡觉状态 | 桌宠已开启 |
| `flash_note` | 写入现有闪记数据库，来源为 `agent` | 内容非空且不超过 4000 字；日程须为有效 `YYYY-MM-DD` 日期，非日程不允许带日期 |

每次消息最多 4000 字。请求配置连接超时 20 秒、读取超时 60 秒、总超时 90 秒，并关闭重定向与自动连接重试。Key 在手机上输入、加密保存，清空输入并保存可移除已保存的 Key。

## 使用与构建

1. 获取 `codex/guardpet-deepseek-agent-20260912` 分支，用 Android Studio 打开 `GuardPet/`。
2. 使用 JDK 17 或兼容 JDK、Android SDK 37 与工程自带的 Gradle 9.5.0。通过本地 SDK 设置或环境变量配置 SDK 路径。
3. Windows 执行 `gradlew.bat :app:assembleDebug`；macOS/Linux 可执行 `sh gradlew :app:assembleDebug`。
4. 安装后在主页「守伴 Agent」或桌宠双击菜单进入。输入自己的 DeepSeek Key，点「保存 Key」后发送消息。
5. 普通聊天不需要开启桌宠。测试摸摸、喂食和睡觉前先开启桌宠，喂食还需要食物；测试保存日程时提供明确日期。

## 验证记录

- 本次已检查原包的 19 个文件和基线差异，确认此前启动修复未被回退。
- 合并后的 `:app` 30 个 XML 文件解析通过。
- 2026-09-12 在本分支 `GuardPet/` 使用 JBR 21.0.11、Android SDK 37、Gradle 9.5.0 执行 `gradlew.bat :app:assembleDebug --offline --console=plain`：`BUILD SUCCESSFUL in 45s`，79 个任务执行成功；Java/Kotlin 编译目标仍为 17。
- 产物位于 `GuardPet/app/build/outputs/apk/debug/app-debug.apk`（构建输出，不提交到源码分支）。编译包含既有 API 弃用、Manifest 合并、中文路径实验选项和原生库符号提示，未阻止 APK 生成。
- 没有使用真实 DeepSeek Key 发起请求，没有进行手机安装、授权、悬浮窗或动作执行测试。原包文档中的历史构建和官方接口说明属于原作者记录，不等同于本次联调结果。

## 当前边界与代码检查记录

- 对话仅保存在当前 `DeepSeekAgent` 实例内；关闭或重建页面会丢失，没有持久聊天记录、长期记忆检索或上下文长度裁剪。
- 动作为回复文本中的 JSON 协议，当前没有原生 `tools`/`tool_calls` 多轮执行，也没有将本地动作结果回传模型。
- 动作类型与日程日期有应用端校验；未知分类由 `FlashNoteCategory.fromKey()` 回落到“其他”，不是严格拒绝未知分类。当前也没有独立的动作确认层。
- 若模型返回非法动作，代码先追加 assistant 消息、后解析；异常分支只移除最后一条，可能留下本轮 user 消息。该分支保留更新包行为，后续应针对失败回合回滚补充修复与测试。
- 闪记写入仍在页面协程的主线程执行，且未检查 SQLite `insert()` 的失败返回值；“已保存”提示还需要通过写入失败场景验证。
- Key 保存与页面关闭时的网络取消仍需真机检查。本次整理导入不表示已完成所有异常路径修复。

后续建议先完成真实模型与四类动作联调，再处理失败回合、写入结果和会话持久化；完整功能约定见 [Agent 文档](../features/08-deepseek-agent.md)。
