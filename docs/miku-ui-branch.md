# Miku UI 版本

## 来源

本目录 `MikuUI/` 来自用户提供的 `AI桌宠-miku极简高级版-完整源码.zip`，已解压后作为独立 Android 工程上传。

## 内容

- 极简高级版主页 UI：品牌头部、状态徽章、宠物预览、心情/饱食度/食物卡片
- 喂食、摸摸、小憩等快捷操作
- 可折叠的自由行走、宠物大小、行走速度设置
- 待办列表与完成奖励控制窗
- Miku 桌宠素材和本地时间行为

## 与 GuardPet 的关系

此版本使用 `com.example.desktoppet` 包名，是独立的 UI 原型工程，放在 `MikuUI/`，不会覆盖产品工程 `GuardPet/`。`GuardPet` 中的闪记、大爆炸提取文字、番茄钟和 Reef 专注功能仍保持不变。

## 运行

用 Android Studio 打开 `MikuUI/`，使用 JDK 17、Android SDK 35，同步 Gradle 后运行 `:app:assembleDebug`。

## 后续合并注意

若要把这套界面合入 `GuardPet`，需要逐项映射 ViewBinding ID、字符串和 drawable，并保留守伴的权限页、透明大爆炸 Activity、唯一 `PetService` 与 Reef 署名；不要直接替换 `GuardPet/app`。
