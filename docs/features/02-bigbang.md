# 大爆炸提取文字

## 用户可见行为

在当前应用画面上拆词。点选切换，拖动连选，从已选词拖动反选。可复制、搜索、送进闪记。下层应用画面透过透明页可见。

## 入口

桌宠双击菜单 → 提取文字。

## 模块与关键类

- `PetService.extractText()`
- `BlockerService.captureVisibleText()`（唯一无障碍）
- `TextTokenizer`（jieba）
- `BigBangActivity` + `TokenFlowView`
- 主题 `Theme.DesktopPet.BigBang`；独立 `taskAffinity`

## 权限

无障碍「守伴守护」

## 不要做的事

不要用 `TYPE_APPLICATION_OVERLAY` 做整页选词。不要和守伴主页放进同一 task。

## 验证步骤

在微信/浏览器等有字的界面提取：词是词不是逐字；能反选；关掉后回到原应用而不是守伴主页。
