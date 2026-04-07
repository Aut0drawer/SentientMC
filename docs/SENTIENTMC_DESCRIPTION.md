# SentientMC 模组介绍

**SentientMC** 是一个基于 Forge 的 Minecraft 模组，通过对接大语言模型（LLM）API，为游戏增加了一个具有环境感知能力的 AI 助手。

---

## 主要功能介绍

### 1. 对话与记忆
- 支持与 AI 进行聊天，AI 能够通过本地文件记录并读取与玩家的对话历史。
- 玩家可以使用 `/ai <消息>` 进行私聊，或在公屏直接对话（取决于配置）。

### 2. 环境感知 (Context Injection)
AI 在处理玩家消息时会获取以下实时信息：
- **基础状态**：当前坐标、维度、天气、昼夜。
- **生物群系**：玩家当前所处的群系类型。
- **实体扫描**：自动识别周围 16 格范围内的实体种类及其数量。
- **装备状态**：获取玩家主副手持有物、全身护甲套装。

### 3. 指令驱动与任务系统
- **指令执行**：AI 可以根据对话情境尝试执行游戏内部指令。
- **错误自愈**：如果指令执行失败，系统会将错误日志回传给 AI 以便尝试自我修正。
- **动态任务**：AI 可通过特定 JSON 格式发布任务，包含 Boss 血条显示进度。

---

## 使用说明

### 配置 API
模组兼容符合 OpenAI 标准格式（OpenAI, DeepSeek, Claude 等）的接口。
配置路径：`config/sentientmc-common.toml` 或通过游戏内 Mod 配置界面。

### 常用指令
- `/aiconfig set <key> <value>`：运行时动态修改配置。
- `/aipermit chat <add/remove> <player>`：管理对话白名单。
- `/aipermit command <add/remove> <player>`：管理指令执行白名单。
- `/aiconfig clearmemory`：清除 AI 对话记忆。

---
© 2026 Aut0Drawer.
