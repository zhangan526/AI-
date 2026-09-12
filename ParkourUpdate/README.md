# 守伴跑酷模块

这个目录包含跑酷功能所需的源码、界面布局、样式、角色素材和字符串资源。

## 文件放置位置

解压后，将 `GuardPet/` 目录里的内容复制到你的 Android 工程对应位置：

- `app/src/main/java/com/geekathon/guardpet/`：4 个 Kotlin 文件
- `app/src/main/res/layout/activity_game.xml`：游戏页面布局
- `app/src/main/res/values/styles_home.xml`：按钮样式
- `app/src/main/res/values/runner_strings.xml`：跑酷相关字符串
- `app/src/main/assets/game/walk_cutout/`：跑酷角色帧素材

## 需要合并的现有代码

`activity_game.xml` 中包含 `@string/game_*` 和项目已有的其他游戏控件；如果目标项目已经有同名文件，请手动合并跑酷按钮 `runnerCrouch` 以及相关布局。

`runner_strings.xml` 里的字符串需要放入项目的 `res/values/`，不能重复定义同名资源。

`GameSurface.kt` 依赖项目中已有的 `TetrisArena.kt`；如果只保留跑酷，需要同时删除俄罗斯方块分发代码，或者继续保留该类。

## 跑酷操作

- 点击游戏画面：跳跃 / 二段跳
- 按住“下蹲 / 快速下落”：躲过悬浮横杆
- 角色在空中时按下蹲：快速落地

## 上传 GitHub

把 `parkour-github` 文件夹上传到仓库，或者将其中的 `GuardPet` 内容合并到原 Android 工程后再提交。建议提交前运行：

```bash
./gradlew :app:assembleDebug
```
