'# 跑酷小游戏更新（源码快照）

## 用户可见行为

跑酷模块支持跳跃、二段跳、下蹲/快速下落、障碍碰撞、金币、难度递增和食物奖励；同时保留俄罗斯方块模式的控制入口。

## 入口

当前压缩包只提供游戏页面和素材，未提供主页或桌宠菜单入口。合入 `GuardPet` 时需要在 `MainActivity` 或 `PetMenuOverlay` 增加 `GameActivity` 入口，并在 Manifest 注册 Activity。

## 模块与关键类

- `ParkourUpdate/GuardPet/app/src/main/java/com/geekathon/guardpet/GameActivity.kt`
- `GameSurface.kt`、`RunnerArena.kt`、`WalkPetSprite.kt`
- `assets/game/walk_cutout/` 角色帧

## 权限

游戏本身不新增运行时权限；奖励读写本地设置。若合并到产品工程，沿用现有守伴权限模型。

## 不要做的事

不要直接覆盖 `GuardPet/` 主工程。更新包引用 `TetrisArena`、`PetHomeActivity` 和 `home_panel_bg`，必须先补齐依赖再合并。

## 验证步骤

确认素材与源码逐文件一致；补齐依赖后，在 `GuardPet/` 执行 `gradlew.bat :app:assembleDebug`，再在真机测试跳跃、二段跳、下蹲、碰撞和奖励领取。
'
