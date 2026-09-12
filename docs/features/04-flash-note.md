# 闪记

## 用户可见行为

语音或手动记下一条。必须选分类：日程/灵感/日记/待办/其他。日程必须带日期，供夜间守护读取。

## 入口

桌宠菜单、主页、大爆炸「闪记」。IME 需要 Activity，所以闪记是 `FlashNoteActivity`，不是 overlay。

## 模块与关键类

- `FlashNoteActivity`、`FlashNoteStore`（SQLite）
- `GuardInitProvider` 里 `FlashNoteStore.init`

## 权限

语音需要麦克风

## 不要做的事

不要做成无输入法的纯 overlay。

## 验证步骤

保存各分类；日程无日期不能存；主页列表能删。
