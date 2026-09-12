# DeepSeek Agent

## 用户可见行为

主页新增「守伴 Agent」入口。用户首次使用时输入 DeepSeek API Key，Key 使用 Android Keystore 加密保存；之后可以和守伴持续对话。

Agent 会在明确请求时执行受控动作：摸摸、喂食、睡觉，或把内容写入闪记。模型只允许返回预定义动作，应用端再次校验后才执行。

## 入口（手势 / 主页按钮 / 配置页）

主页「守伴 Agent」按钮进入 `AiAgentActivity`。

## 模块与关键类

- `DeepSeekAgent`：调用 `https://api.deepseek.com/chat/completions`，使用 `deepseek-flash`，维护本次页面会话；请求有连接、读取和总时限，失败时不保留半个用户回合。
- `ApiKeyStore`：Android Keystore AES-GCM 加密 API Key。
- `AgentTools`：校验模型返回的动作、分类、日期和内容长度后才允许执行。
- `AiAgentActivity`：聊天 UI、错误提示和动作执行。Agent 入口也位于桌宠双击菜单。

## 权限

使用 `INTERNET` 普通权限，无需运行时申请。

## 不要做的事

- 不要把 API Key 写入源码或提交到 Git。
- Agent 当前只允许摸摸、喂食、睡觉和保存闪记；不要扩大动作集合而跳过应用端校验。
- 模型返回的文字不会被当作指令执行；只有符合预定义 JSON 的动作字段才会生效。
- 网络请求必须在协程后台线程执行。

## 验证步骤

在 `GuardPet/` 执行 `./gradlew :app:assembleDebug`。安装后打开 Agent 页面，保存 Key，发送普通消息；再测试摸摸、喂食、睡觉和保存闪记。测试喂食前确保桌宠已开启且有食物；日程必须给出明确日期。

2026-09-12：`./gradlew :app:assembleDebug --no-daemon` 构建成功。DeepSeek 官方文档确认 OpenAI 兼容端点为 `https://api.deepseek.com/chat/completions`，当前模型名使用 `deepseek-flash`；工具调用由客户端执行，模型本身不会直接操作设备。
