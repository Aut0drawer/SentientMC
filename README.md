# SentientMC

SentientMC is a Minecraft Forge mod that integrates Large Language Models (LLMs) into the game, providing a context-aware AI assistant that can interact with players, perceive the game world, and execute commands.

## 📥 Download

[![Download from CurseForge](https://img.shields.io/badge/Download-CurseForge-orange?style=for-the-badge&logo=curseforge)](https://www.curseforge.com/minecraft/mc-mods/sentientmc)


[中文说明 (Chinese Description)](SENTIENTMC_DESCRIPTION.md)

## Features

- **Context-Aware Dialogue**: The AI receives real-time information about the player's environment, including coordinates, dimension, biome, weather, and time.
- **Entity & Inventory Scanning**: The AI can "see" entities within a 16-block radius and knows what the player is currently holding or wearing.
- **Command Execution**: The AI can attempt to execute Minecraft commands based on the conversation. It features a feedback loop for error correction.
- **Dynamic Quest System**: AI can assign structured tasks (Gather, Kill, Build) with progress tracked via a Boss Bar.
- **Proactive Interactions**: Configurable intervals for the AI to start conversations based on player activity or environmental changes.
- **Memory Persistence**: Conversations are saved and reloaded, allowing the AI to maintain long-term context.

## Installation

### For Players
1. Download the latest `.jar` from the [CurseForge](https://www.curseforge.com/minecraft/mc-mods/sentientmc).
2. Place the jar in your Minecraft `mods` folder.
3. Ensure you are running the correct version of **Minecraft Forge** (1.21.1).

### For Developers
1. Clone the repository: `git clone https://github.com/Aut0Drawer/SentientMC.git`
2. Setup the workspace:
   - For IntelliJ: Import the `build.gradle` file.
   - For Eclipse: Run `./gradlew genEclipseRuns`.
3. Build the project: `./gradlew build`

## Configuration

The mod requires an OpenAI-compatible API key and endpoint.

1. **In-game**: Go to the Mod List -> SentientMC -> Config.
2. **Config File**: Edit `config/sentientmc-common.toml`.

| Key | Description |
| --- | --- |
| `API_KEY` | Your API secret key. |
| `API_URL` | The base URL for the API (e.g., `https://api.openai.com/v1`). |
| `MODEL_NAME` | The model ID (e.g., `gpt-4o`, `deepseek-chat`). |
| `AI_NAME` | The display name for the AI in chat. |

## Commands

- `/ai <message>`: Send a private message to the AI.
- `/aiconfig set <key> <value>`: Modify configuration at runtime (OP required).
- `/aipermit chat <add/remove> <player>`: Manage who can talk to the AI.
- `/aipermit command <add/remove> <player>`: Manage who can trigger AI commands.
- `/aiconfig clearmemory`: Wipe the AI's conversation history.

## Credits

Created by **Aut0Drawer**.

---
© 2026 Aut0Drawer. All Rights Reserved.
