package com.Autodwr.sentientmc;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import com.google.gson.JsonParser;

@SuppressWarnings("null")
public class SentientMCClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    private static final Gson GSON = new Gson();

    // === Context Memory Storage ===
    private static final java.util.LinkedList<JsonObject> MEMORY = new java.util.LinkedList<>();
    private static final Path MEMORY_FILE = Paths.get("sentientmc_memory.json");
    private static final Path QUEST_TARGETS_FILE = Paths.get("config/sentientmc_quest_targets.json");

    // === Message Batching Queue ===
    private static final List<JsonObject> MESSAGE_QUEUE = new ArrayList<>();
    private static long lastMessageTime = 0;

    // === Quest Targets (loaded from JSON, injected into system prompt) ===
    private static String questTargetsPrompt = ""; // cached prompt snippet

    public static void loadMemory() {
        try {
            if (Files.exists(MEMORY_FILE)) {
                String content = Files.readString(MEMORY_FILE);
                JsonArray array = JsonParser.parseString(content).getAsJsonArray();
                synchronized (MEMORY) {
                    MEMORY.clear();
                    array.forEach(e -> MEMORY.add(e.getAsJsonObject()));
                }
                LOGGER.info("[SentientMCClient] Loaded {} memory items from disk.", MEMORY.size());
            }
        } catch (Exception e) {
            LOGGER.error("[SentientMCClient] Failed to load memory from disk", e);
        }
    }

    /**
     * Load quest targets from config/sentientmc_quest_targets.json.
     * If the file doesn't exist, copies the default from resources.
     * Builds a compact prompt snippet listing all valid IDs per quest type.
     */
    public static void loadQuestTargets() {
        try {
            // If user hasn't created the file yet, write out the default from resources
            if (!Files.exists(QUEST_TARGETS_FILE)) {
                java.io.InputStream defaultStream = SentientMCClient.class
                        .getResourceAsStream("/assets/sentientmc/quest_targets_default.json");
                if (defaultStream != null) {
                    Files.createDirectories(QUEST_TARGETS_FILE.getParent());
                    Files.copy(defaultStream, QUEST_TARGETS_FILE);
                    LOGGER.info("[SentientMCClient] Copied default quest_targets.json to config/");
                } else {
                    LOGGER.warn("[SentientMCClient] Default quest_targets.json not found in resources.");
                    return;
                }
            }

            String content = Files.readString(QUEST_TARGETS_FILE);
            JsonObject root = JsonParser.parseString(content).getAsJsonObject();

            StringBuilder sb = new StringBuilder();
            sb.append("Valid quest targets (ONLY use these IDs):\n");
            for (String type : new String[] { "ITEM_GATHER", "KILL", "BUILD" }) {
                if (!root.has(type))
                    continue;
                sb.append("  ").append(type).append(": ");
                JsonArray arr = root.getAsJsonArray(type);
                for (int i = 0; i < arr.size(); i++) {
                    JsonObject entry = arr.get(i).getAsJsonObject();
                    sb.append(entry.get("id").getAsString())
                            .append("(").append(entry.get("label").getAsString()).append(")");
                    if (i < arr.size() - 1)
                        sb.append(", ");
                }
                sb.append("\n");
            }
            questTargetsPrompt = sb.toString();
            LOGGER.info("[SentientMCClient] Loaded quest targets from {}", QUEST_TARGETS_FILE);
        } catch (Exception e) {
            LOGGER.error("[SentientMCClient] Failed to load quest targets", e);
        }
    }

    public static void saveMemory() {
        try {
            JsonArray array = new JsonArray();
            synchronized (MEMORY) {
                MEMORY.forEach(array::add);
            }
            Files.writeString(MEMORY_FILE, GSON.toJson(array));
            LOGGER.debug("[SentientMCClient] Saved memory to disk.");
        } catch (Exception e) {
            LOGGER.error("[SentientMCClient] Failed to save memory to disk", e);
        }
    }

    public static void clearMemory() {
        synchronized (MEMORY) {
            MEMORY.clear();
        }
        try {
            Files.deleteIfExists(MEMORY_FILE);
            LOGGER.info("[SentientMCClient] Conversation memory cleared and file deleted.");
        } catch (Exception e) {
            LOGGER.error("[SentientMCClient] Failed to delete memory file", e);
        }
    }

    public static void addMessageToBatch(String playerName, String message, String environmentState) {
        synchronized (MESSAGE_QUEUE) {
            JsonObject msg = new JsonObject();
            msg.addProperty("playerName", playerName);
            msg.addProperty("message", message);
            msg.addProperty("environmentState", environmentState);
            MESSAGE_QUEUE.add(msg);
            lastMessageTime = System.currentTimeMillis();
            if (SentientMCMod.RT_DEBUG_MODE) {
                int qSize = MESSAGE_QUEUE.size();
                net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks
                        .getCurrentServer();
                if (server != null) {
                    server.getPlayerList()
                            .broadcastSystemMessage(net.minecraft.network.chat.Component.literal(
                                    "§e[批处理队列] 当前待处理消息: " + qSize + " (等待" + SentientMCMod.RT_BATCH_DELAY + "秒)"),
                                    true);
                }
            }
            LOGGER.debug("[SentientMCClient] Queued message from {}. Queue size: {}", playerName, MESSAGE_QUEUE.size());
        }
    }

    public static void checkAndFlushQueue() {
        if (!SentientMCMod.RT_ENABLE_BATCHING) {
            flushQueue();
            return;
        }

        synchronized (MESSAGE_QUEUE) {
            if (MESSAGE_QUEUE.isEmpty())
                return;

            long delayMs = SentientMCMod.RT_BATCH_DELAY * 1000L;
            if (System.currentTimeMillis() - lastMessageTime >= delayMs) {
                flushQueue();
            }
        }
    }

    public static void flushQueue() {
        List<JsonObject> batch;
        synchronized (MESSAGE_QUEUE) {
            if (MESSAGE_QUEUE.isEmpty())
                return;
            batch = new ArrayList<>(MESSAGE_QUEUE);
            MESSAGE_QUEUE.clear();
        }

        StringBuilder combinedMessages = new StringBuilder();
        String lastEnvState = "";

        for (JsonObject msg : batch) {
            combinedMessages.append(msg.get("playerName").getAsString()).append(" 说: ")
                    .append(msg.get("message").getAsString()).append("\n");
            // Use the environment state from the last message as the most up-to-date one
            if (msg.has("environmentState") && !msg.get("environmentState").isJsonNull()
                    && !msg.get("environmentState").getAsString().isEmpty()) {
                lastEnvState = msg.get("environmentState").getAsString();
            }
        }

        LOGGER.info("[SentientMCClient] Flushing batch of {} messages.", batch.size());

        final String finalMsgs = combinedMessages.toString().trim();
        final String finalEnv = lastEnvState;

        // Execute API call asynchronously so it doesn't block the server main thread
        CompletableFuture.runAsync(() -> {
            net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks
                    .getCurrentServer();
            if (SentientMCMod.RT_DEBUG_MODE && server != null) {
                server.execute(() -> {
                    if (SentientMCMod.asyncBossBar == null) {
                        SentientMCMod.asyncBossBar = new net.minecraft.server.level.ServerBossEvent(
                                net.minecraft.network.chat.Component.literal("§d[SentientMC] AI 正在思考并接入网络 (Async)..."),
                                net.minecraft.world.BossEvent.BossBarColor.PINK,
                                net.minecraft.world.BossEvent.BossBarOverlay.NOTCHED_10);
                        SentientMCMod.asyncBossBar.setProgress(1.0f);
                    }
                    SentientMCMod.asyncBossBar.setVisible(true);
                    for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
                        SentientMCMod.asyncBossBar.addPlayer(p);
                    }
                });
            }
            try {
                // Initiate flush with depth 0
                flushQueueInternal(finalMsgs, finalEnv, 0);
            } catch (Exception e) {
                LOGGER.error("[SentientMCClient] Error processing batch request to AI", e);
            } finally {
                if (SentientMCMod.RT_DEBUG_MODE && server != null) {
                    server.execute(() -> {
                        if (SentientMCMod.asyncBossBar != null) {
                            SentientMCMod.asyncBossBar.setVisible(false);
                            SentientMCMod.asyncBossBar.removeAllPlayers();
                        }
                    });
                }
            }
        });
    }

    private static void flushQueueInternal(String combinedUserMessages, String environmentState, int commandDepth)
            throws Exception {
        String response = requestAiInner(combinedUserMessages, environmentState);
        if (response != null && !response.isEmpty() && !response.startsWith("API Error")) {
            SentientMCMod.processAndBroadcastAiResponse(response, () -> {
                if (commandDepth + 1 >= SentientMCMod.RT_MAX_COMMAND_CHAIN) {
                    LOGGER.warn("[SentientMCClient] Reached max command chain limit ({}). Stopping.",
                            SentientMCMod.RT_MAX_COMMAND_CHAIN);
                    addMessageToBatch("SYSTEM",
                            "[COMMAND FEEDBACK] Max command chain reached. Do not output any more commands this turn.",
                            null);
                } else {
                    flushQueueSync(commandDepth + 1);
                }
            });
        } else if (response != null && response.startsWith("API Error")) {
            // Broadcast error to all players so they can troubleshoot
            broadcastError(response);
        }
    }

    private static void flushQueueSync(int newDepth) {
        List<JsonObject> batch;
        synchronized (MESSAGE_QUEUE) {
            if (MESSAGE_QUEUE.isEmpty())
                return;
            batch = new ArrayList<>(MESSAGE_QUEUE);
            MESSAGE_QUEUE.clear();
        }

        StringBuilder combinedMessages = new StringBuilder();
        String lastEnvState = "";

        for (JsonObject msg : batch) {
            combinedMessages.append(msg.get("playerName").getAsString()).append(" 说: ")
                    .append(msg.get("message").getAsString()).append("\n");
            if (msg.has("environmentState") && !msg.get("environmentState").isJsonNull()
                    && !msg.get("environmentState").getAsString().isEmpty()) {
                lastEnvState = msg.get("environmentState").getAsString();
            }
        }

        try {
            flushQueueInternal(combinedMessages.toString().trim(), lastEnvState, newDepth);
        } catch (Exception e) {
            LOGGER.error("[SentientMCClient] Error processing feedback batch request", e);
        }
    }

    private static void broadcastError(String errorMessage) {
        net.minecraft.server.MinecraftServer server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> {
                net.minecraft.network.chat.Component msg = net.minecraft.network.chat.Component.literal(
                        "§c[SentientMC] " + errorMessage);
                server.getPlayerList().broadcastSystemMessage(msg, false);
            });
        }
    }

    private static String requestAiInner(String combinedUserMessages, String environmentState) throws Exception {
        String apiUrl = SentientMCMod.RT_API_URL;
        String apiKey = SentientMCMod.RT_API_KEY;
        String modelName = SentientMCMod.RT_MODEL_NAME;
        String sysPrompt = SentientMCMod.RT_SYSTEM_PROMPT;
        int maxMemory = SentientMCMod.RT_MEMORY_SIZE;

        if (apiUrl == null || apiUrl.isEmpty() || apiKey == null || apiKey.isEmpty()) {
            LOGGER.warn("[SentientMCClient] API URL or Key is empty. Skipping request.");
            return null;
        }

        LOGGER.info("[SentientMCClient] Sending batch request to AI. Content: {}", combinedUserMessages);

        // 1. Build the System Prompt with optional Environmental injection
        JsonObject systemMessage = new JsonObject();
        systemMessage.addProperty("role", "system");
        String finalSysPrompt = sysPrompt;
        if (SentientMCMod.RT_ENABLE_STATE_INJECTION && environmentState != null && !environmentState.isEmpty()) {
            finalSysPrompt += "\n[Current Environment State]:\n" + environmentState;
        }
        if (SentientMCMod.RT_ENABLE_COMMANDS) {
            finalSysPrompt += "\n[COMMAND EXECUTION]: You can execute server commands by outputting exactly `[COMMAND: /your_command]`. You can output multiple commands. After they execute, the system will silently reply with the output. You should use this to fix your mistakes or give players items.";
        }
        finalSysPrompt += "\n[PRIVATE REPLIES]: If a player talks to you using `[私聊]` or you need to reply privately so others don't see, output exactly `[PRIVATE: PlayerName: Your Message]`. You can mix this with regular chat. Only the target player will see this part.";
        finalSysPrompt += "\n[PLAYER QUERY]: If you need to know another player's location, inventory, or state (e.g. if someone asks you about them), output exactly `[QUERY_PLAYER: PlayerName]`. You will receive their status (as if you were looking at them) in a follow-up system message.";
        // Always inject quest system instructions + valid target list so AI picks
        // correct IDs
        finalSysPrompt += "\n[QUEST SYSTEM]: You can assign individual quests to players using ONLY the following exact format (do not deviate):\n"
                + "  `[QUEST: PlayerName:TYPE:target_id:count:Description in Chinese]`\n"
                + "Supported quest types:\n"
                + "  ITEM_GATHER  — collect items into inventory.\n"
                + "    Example: [QUEST: Notchy:ITEM_GATHER:minecraft:apple:5:收集5个苹果]\n"
                + "  KILL         — kill specific mobs.\n"
                + "    Example: [QUEST: Player123:KILL:minecraft:zombie:3:杀死3只僵尸]\n"
                + "  BUILD        — place specific blocks.\n"
                + "    Example: [QUEST: Steve:BUILD:minecraft:stone_bricks:20:放置20个石砖]\n"
                + (questTargetsPrompt.isEmpty() ? "" : questTargetsPrompt + "\n")
                + "Rules:\n"
                + "- You MUST specify the exact target PlayerName for who the quest is assigned to.\n"
                + "- You MUST only use target IDs from the list above. Do NOT invent IDs.\n"
                + "- Always use full Minecraft namespace IDs (e.g. `minecraft:stone_bricks`).\n"
                + "- Only one active quest per player at a time. When the system reports a player completed their quest, you may issue them the next one.\n"
                + "- The quest tag can appear anywhere in your message. The rest of your message is shown to players normally.";
        systemMessage.addProperty("content", finalSysPrompt);

        // 2. Build the User Message
        JsonObject userMessage = new JsonObject();
        userMessage.addProperty("role", "user");
        userMessage.addProperty("content", combinedUserMessages);

        // 3. Construct Context Array (Memory)
        JsonArray messagesArray = new JsonArray();
        messagesArray.add(systemMessage);

        // Add historical memory
        synchronized (MEMORY) {
            for (JsonObject pastMsg : MEMORY) {
                messagesArray.add(pastMsg);
            }
        }

        messagesArray.add(userMessage);

        // 4. Build Final Payload
        JsonObject payloadObj = new JsonObject();
        payloadObj.addProperty("model", modelName);
        payloadObj.add("messages", messagesArray);

        String payload = GSON.toJson(payloadObj);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
        LOGGER.info("[SentientMCClient] Received response. Status: {}", response.statusCode());

        if (response.statusCode() >= 400) {
            String errorBody = response.body();
            String errorDetail = "";
            try {
                JsonObject errJson = GSON.fromJson(errorBody, JsonObject.class);
                if (errJson != null && errJson.has("error")) {
                    JsonObject errObj = errJson.getAsJsonObject("error");
                    if (errObj.has("message")) {
                        errorDetail = errObj.get("message").getAsString();
                    } else {
                        errorDetail = errObj.toString();
                    }
                } else if (errJson != null && errJson.has("message")) {
                    errorDetail = errJson.get("message").getAsString();
                } else {
                    errorDetail = errorBody.length() > 200 ? errorBody.substring(0, 200) + "..." : errorBody;
                }
            } catch (Exception e) {
                errorDetail = errorBody.length() > 200 ? errorBody.substring(0, 200) + "..." : errorBody;
            }
            LOGGER.error("[SentientMCClient] API request failed with status {}. Detail: {}", response.statusCode(), errorDetail);
            return "API Error: HTTP " + response.statusCode() + " - " + errorDetail;
        }

        JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
        if (json == null || !json.has("choices")) {
            return "Unexpected JSON response";
        }
        var choices = json.getAsJsonArray("choices");
        if (choices.size() == 0)
            return "No response from AI";

        String aiReply = choices.get(0).getAsJsonObject().getAsJsonObject("message").get("content").getAsString();

        // 5. Update Memory Context
        if (maxMemory > 0) {
            synchronized (MEMORY) {
                MEMORY.add(userMessage);
                JsonObject aiMsgObj = new JsonObject();
                aiMsgObj.addProperty("role", "assistant");
                aiMsgObj.addProperty("content", aiReply);
                MEMORY.add(aiMsgObj);
                // Keep at most maxMemory*2 entries (user+assistant pairs)
                while (MEMORY.size() > maxMemory * 2) {
                    MEMORY.removeFirst();
                }
            }
            saveMemory();
        } else {
            MEMORY.clear();
            saveMemory();
        }

        return aiReply;
    }
}
