# SentientMC (Sentient AI Companion)

**SentientMC** is a Minecraft Forge mod that integrates Large Language Models (LLMs) into the game, providing a context-aware AI assistant that can interact with players, perceive the environment, and execute commands.

**SentientMC** 是一个基于 Forge 的 Minecraft 模组，通过对接大语言模型（LLM）API，为游戏增加了一个具有环境感知能力的 AI 助手。

---

## 🌟 Key Features / 主要功能

### 🧠 Dialogue & Memory / 对话与记忆
- **English**: Supports full natural language chat. The AI remembers past conversations via local file storage. Access private chat via `/ai <message>`.
- **中文**: 支持全自然语言聊天。AI 通过本地文件记录对话历史，拥有长期记忆。使用 `/ai <消息>` 进行私密对话。

### 🌍 Environmental Awareness / 环境感知
The AI receives real-time data about your world:
- **Status**: Coordinates, dimension, weather, and time.
- **Biomes**: Current biome type detection.
- **Entity Scan**: Identifies entities within a 16-block radius.
- **Equipment**: Knows what you are holding and wearing (Armor & Weapons).

AI 在处理消息时会获取实时世界信息：
- **基础状态**：当前坐标、维度、天气、昼夜。
- **生物群系**：玩家当前所处的群系类型。
- **实体扫描**：自动识别周围 16 格范围内的实体及其数量。
- **装备状态**：获取玩家主副手持有物、全身护甲套装。

### 🛠️ Commands & Quests / 指令与任务系统
- **Command Execution**: AI can execute in-game commands based on context.
- **Error Correction**: If a command fails, the system feeds the error back to the AI for self-correction.
- **Dynamic Quests**: AI can assign tasks (Gather, Kill, Build) with progress tracked via a Boss Bar.

- **指令执行**：AI 可以根据对话情境尝试执行游戏内部指令。
- **错误自愈**：如果指令执行失败，系统会将错误日志回传给 AI 以便尝试自我修正。
- **动态任务**：AI 可通过特定格式发布任务，并在 Boss 血条显示进度。

---

## ⚙️ Configuration / 使用说明

### API Setup / 配置 API
The mod is compatible with any OpenAI-standard API (OpenAI, DeepSeek, Claude, etc.).
Configure via `config/sentientmc-common.toml` or the in-game Mod Menu.

模组兼容符合 OpenAI 标准格式（OpenAI, DeepSeek, Claude 等）的接口。
配置路径：`config/sentientmc-common.toml` 或通过游戏内 Mod 配置界面。

### Common Commands / 常用指令
- `/aiconfig set <key> <value>`: Runtime config adjustments. / 运行时动态修改配置。
- `/aipermit chat <add/remove> <player>`: Managed chat whitelist. / 管理对话白名单。
- `/aipermit command <add/remove> <player>`: Manage command whitelist. / 管理指令执行白名单。
- `/aiconfig clearmemory`: Clear AI conversation history. / 清除 AI 对话记忆。

---
© 2026 Aut0Drawer.
