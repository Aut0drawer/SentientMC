package com.Autodwr.sentientmc;

import net.minecraftforge.common.ForgeConfigSpec;

public class SentientMCConfig {
    public static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<String> API_KEY;
    public static final ForgeConfigSpec.ConfigValue<String> API_URL;
    public static final ForgeConfigSpec.ConfigValue<String> MODEL_NAME;
    public static final ForgeConfigSpec.ConfigValue<String> SYSTEM_PROMPT;
    public static final ForgeConfigSpec.ConfigValue<String> AI_NAME;
    public static final ForgeConfigSpec.IntValue MEMORY_SIZE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_STATE_INJECTION;
    public static final ForgeConfigSpec.BooleanValue ENABLE_EVENTS;
    public static final ForgeConfigSpec.BooleanValue ENABLE_COMMANDS;

    public static final ForgeConfigSpec.BooleanValue ENABLE_BIOME_SENSE;
    public static final ForgeConfigSpec.BooleanValue ENABLE_ENTITY_SCAN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_INVENTORY_SCAN;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROACTIVE_CHAT;
    public static final ForgeConfigSpec.IntValue PROACTIVE_INTERVAL;
    public static final ForgeConfigSpec.BooleanValue ENABLE_BATCHING;
    public static final ForgeConfigSpec.IntValue BATCH_DELAY;
    public static final ForgeConfigSpec.IntValue MAX_COMMAND_CHAIN;

    static {
        BUILDER.push("AI Configuration");

        API_KEY = BUILDER
                .comment("The API Key for your OpenAI-compatible endpoint.")
                .define("apiKey", "sk-xxxxxxxxxxxxxxxxxxxxxxxx");

        API_URL = BUILDER
                .comment("The URL of your OpenAI-compatible chat completion endpoint.")
                .define("apiUrl", "https://api.openai.com/v1/chat/completions");

        MODEL_NAME = BUILDER
                .comment("The name of the model to use (e.g., gpt-3.5-turbo, gemini-3.0-flash-preview).")
                .define("modelName", "gpt-3.5-turbo");

        SYSTEM_PROMPT = BUILDER
                .comment("The personality or fundamental instructions for the AI.")
                .define("systemPrompt", "你是一个Minecraft服务器上的聊天助手，尽量简短俏皮地回应玩家的对话。");

        AI_NAME = BUILDER
                .comment("The display name of the AI in chat.")
                .define("aiName", "AI助手");

        MEMORY_SIZE = BUILDER
                .comment("How many previous messages the AI should remember per query (0 = no memory).")
                .defineInRange("memorySize", 5, 0, 50);

        ENABLE_STATE_INJECTION = BUILDER
                .comment("Give the AI awareness of time, weather, and player coordinates.")
                .define("enableStateInjection", true);

        ENABLE_EVENTS = BUILDER
                .comment("Allow AI to autonomously respond to server events like deaths and achievements.")
                .define("enableEvents", true);

        ENABLE_COMMANDS = BUILDER
                .comment("SECURITY WARNING: Allow the AI to execute server operator commands.")
                .define("enableCommands", true);

        ENABLE_BIOME_SENSE = BUILDER
                .comment("Allow AI to know the player's current biome.")
                .define("enableBiomeSense", true);

        ENABLE_ENTITY_SCAN = BUILDER
                .comment("Allow AI to scan nearby entities (mobs, animals).")
                .define("enableEntityScan", true);

        ENABLE_INVENTORY_SCAN = BUILDER
                .comment("Allow AI to know what the player is holding and wearing.")
                .define("enableInventoryScan", true);

        ENABLE_PROACTIVE_CHAT = BUILDER
                .comment("Allow AI to initiate conversations when the player is quiet.")
                .define("enableProactiveChat", true);

        PROACTIVE_INTERVAL = BUILDER
                .comment("Minutes of silence before AI can talk proactively.")
                .defineInRange("proactiveIntervalMinutes", 5, 1, 60);

        ENABLE_BATCHING = BUILDER
                .comment("Batch messages together to reduce API calls.")
                .define("enableBatching", true);

        BATCH_DELAY = BUILDER
                .comment("Seconds to wait before sending a batch of messages to AI.")
                .defineInRange("batchDelaySeconds", 3, 1, 30);

        MAX_COMMAND_CHAIN = BUILDER
                .comment("Maximum times the AI can try to correct a failed command in a row.")
                .defineInRange("maxCommandChain", 3, 1, 10);

        BUILDER.pop();
        SPEC = BUILDER.build();
    }
}
