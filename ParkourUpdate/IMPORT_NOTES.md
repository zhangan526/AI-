# ParkourUpdate 导入说明

- 分支：codex/guardpet-parkour-20260913
- 来源：parkour-github-20260913.zip
- SHA-256：$(Get-FileHash -LiteralPath 'D:\AI-SB\parkour-github-20260913.zip').Hash
- 该目录是增量源码快照，原包文件已原样保留。
- 当前 main 缺少 TetrisArena、PetHomeActivity、home_panel_bg；本次不覆盖主工程，避免直接合入后编译失败。
- 合并时按原包 README 的路径说明复制，并补齐依赖、Manifest Activity 注册、主页/桌宠入口。
