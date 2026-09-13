# 宠物之家小游戏（跑酷 / 俄罗斯方块）

| 项 | 内容 |
|----|------|
| 状态 | 已接入 |
| 日期 | 2026-09-13 |
| 模块 | `:app` |

## 1. 用户能看到什么

主页新增「宠物之家」入口。用户可查看守伴心情、饥饿和清洁度，进行喂食、洗澡、摸摸；从小游戏页进入跑酷或俄罗斯方块，按得分领取食物和心情奖励。

入口：`MainActivity` → `PetHomeActivity` → `GameMenuActivity` → `GameActivity`。

## 2. 模块与关键类

- `PetHomeActivity.kt`：宠物之家状态和互动。
- `GameMenuActivity.kt`、`GameActivity.kt`：游戏选择、对局、奖励和最高分。
- `GameSurface.kt`：游戏画布分发；`RunnerArena.kt` / `TetrisArena.kt`：玩法逻辑。
- `WalkPetSprite.kt` 与 `assets/game/walk_cutout/*.png`：角色帧。
- `PetHomeBackdrop.kt`、`activity_pet_home.xml`、`activity_game_menu.xml`、`activity_game.xml`：页面视觉。
- `pet_home`、`game_rewards`：SharedPreferences；食物和心情复用 `PetSettings`。

## 3. 权限与边界

无新增权限。游戏使用普通 Activity，不使用悬浮窗、前台服务或无障碍服务；桌宠运行时仅通过已有 `PetService.ACTION_SET_STATE` 联动状态。

## 4. 验证

在 `GuardPet/` 执行 `./gradlew :app:assembleDebug`。真机依次打开主页「宠物之家」、照顾守伴、进入两种游戏，确认操作、结算、领取奖励和最高分均正常。

## 5. 搜索关键词

`PetHomeActivity`、`GameMenuActivity`、`GameActivity`、`GameSurface`、`RunnerArena`、`TetrisArena`、`WalkPetSprite`、`game_rewards`

## 6. 相关排障

跑酷下蹲按钮松手后状态残留 / 高密度屏幕比例异常（见 `docs/TROUBLESHOOTING.md`）。
