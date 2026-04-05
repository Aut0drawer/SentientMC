package com.Autodwr.sentientmc;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.loading.FMLLoader;

import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraft.commands.Commands;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraftforge.event.TickEvent;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.server.ServerLifecycleHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.EquipmentSlot;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandSource;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.world.BossEvent;
import net.minecraftforge.event.level.BlockEvent;

@SuppressWarnings("null")
@Mod(SentientMCMod.MODID)
public class SentientMCMod {
        public static final String MODID = "sentientmc";
        private static final Logger LOGGER = LogUtils.getLogger();

        // Track the last time a player interacted with AI or the AI talked to them
        private static final Map<String, Long> lastInteractionTimes = new HashMap<>();

        // Persistent Structured Quest Tracking
        private static String questType = ""; // e.g. "ITEM_GATHER", "KILL"
        private static String questTarget = ""; // e.g. "minecraft:apple", "minecraft:zombie"
        private static int questTargetCount = 0; // e.g. 5
        private static int questCurrentCount = 0; // Progress
        private static String questDesc = ""; // e.g. "收集5个苹果"
        private static ServerBossEvent questBossBar = null;

        // Permissions
        private static final Gson GSON = new Gson();
        private static List<String> chatWhitelist = new ArrayList<>();
        private static List<String> commandWhitelist = new ArrayList<>();

        // Runtime config overrides — changed live via /aiconfig without touching the
        // file
        // Initialized from SentientMCConfig on server start
        public static String RT_API_KEY = "";
        public static String RT_API_URL = "";
        public static String RT_MODEL_NAME = "";
        public static String RT_SYSTEM_PROMPT = "";
        public static String RT_AI_NAME = "AI助手";
        public static int RT_MEMORY_SIZE = 5;
        public static int RT_BATCH_DELAY = 3;
        public static int RT_PROACTIVE_INTERVAL = 10;
        public static int RT_MAX_COMMAND_CHAIN = 3;
        public static boolean RT_ENABLE_COMMANDS = false;
        public static boolean RT_ENABLE_EVENTS = true;
        public static boolean RT_ENABLE_STATE_INJECTION = true;
        public static boolean RT_ENABLE_BATCHING = true;
        public static boolean RT_ENABLE_PROACTIVE_CHAT = false;
        public static boolean RT_ENABLE_ENTITY_SCAN = false;
        public static boolean RT_ENABLE_INVENTORY_SCAN = false;
        public static boolean RT_DEBUG_MODE = false;
        public static ServerBossEvent asyncBossBar = null;

        // Tracks the last player name that triggered a chat (for command permission
        // check)
        public static String lastTriggerPlayer = "";

        @SuppressWarnings("removal")
        public SentientMCMod() {
                MinecraftForge.EVENT_BUS.register(this);
                LOGGER.info("[SentientMCMod] Successfully registered on MinecraftForge.EVENT_BUS!");

                // CLIENT 类型在单人和联机时均可正常读取，服务端可通过 /aiconfig 指令在运行时覆盖
                ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, SentientMCConfig.SPEC);

                if (FMLLoader.getDist().isClient()) {
                        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                                        () -> new ConfigScreenHandler.ConfigScreenFactory(
                                                        (mc, screen) -> new SentientMCConfigScreen(screen)));
                }

                MinecraftForge.EVENT_BUS.addListener(this::onServerStart);
        }

        public void onServerStart(net.minecraftforge.event.server.ServerStartingEvent event) {
                loadPermissions();
                SentientMCClient.loadMemory();
                SentientMCClient.loadQuestTargets();
                syncConfigWithRuntime();
                LOGGER.info("[SentientMCMod] Server started, runtime config initialized. AI Name: {}", RT_AI_NAME);
        }

        public static void syncConfigWithRuntime() {
                RT_API_KEY = SentientMCConfig.API_KEY.get();
                RT_API_URL = SentientMCConfig.API_URL.get();
                RT_MODEL_NAME = SentientMCConfig.MODEL_NAME.get();
                RT_SYSTEM_PROMPT = SentientMCConfig.SYSTEM_PROMPT.get();
                RT_AI_NAME = SentientMCConfig.AI_NAME.get();
                RT_MEMORY_SIZE = SentientMCConfig.MEMORY_SIZE.get();
                RT_BATCH_DELAY = SentientMCConfig.BATCH_DELAY.get();
                RT_PROACTIVE_INTERVAL = SentientMCConfig.PROACTIVE_INTERVAL.get();
                RT_MAX_COMMAND_CHAIN = SentientMCConfig.MAX_COMMAND_CHAIN.get();
                RT_ENABLE_COMMANDS = SentientMCConfig.ENABLE_COMMANDS.get();
                RT_ENABLE_EVENTS = SentientMCConfig.ENABLE_EVENTS.get();
                RT_ENABLE_STATE_INJECTION = SentientMCConfig.ENABLE_STATE_INJECTION.get();
                RT_ENABLE_BATCHING = SentientMCConfig.ENABLE_BATCHING.get();
                RT_ENABLE_PROACTIVE_CHAT = SentientMCConfig.ENABLE_PROACTIVE_CHAT.get();
                RT_ENABLE_ENTITY_SCAN = SentientMCConfig.ENABLE_ENTITY_SCAN.get();
                RT_ENABLE_INVENTORY_SCAN = SentientMCConfig.ENABLE_INVENTORY_SCAN.get();
                LOGGER.info("[SentientMCMod] Runtime config synchronized from file.");
        }

        @SubscribeEvent
        public void onServerTick(TickEvent.ServerTickEvent event) {
                if (event.phase == TickEvent.Phase.END) {
                        SentientMCClient.checkAndFlushQueue();
                        checkProactiveChat();
                        checkQuestProgress();
                }
        }

        @SuppressWarnings("removal")
        private static net.minecraft.resources.ResourceLocation parseResourceLocation(String id) {
                String[] parts = id.split(":", 2);
                if (parts.length == 2) {
                        return new net.minecraft.resources.ResourceLocation(parts[0], parts[1]);
                } else {
                        return new net.minecraft.resources.ResourceLocation("minecraft", id);
                }
        }

        private static void checkQuestProgress() {
                if (questBossBar == null || !questBossBar.isVisible() || questTargetCount <= 0)
                        return;
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null)
                        return;

                if ("ITEM_GATHER".equals(questType)) {
                        int totalFound = 0;
                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                net.minecraft.world.item.Item item = net.minecraftforge.registries.ForgeRegistries.ITEMS
                                                .getValue(parseResourceLocation(questTarget));
                                if (item != null) {
                                        totalFound += p.getInventory().countItem(item);
                                }
                        }
                        questCurrentCount = totalFound;
                        updateBossBar();
                }
                // BUILD and KILL are event-driven, no polling needed
        }

        private static void updateBossBar() {
                if (questBossBar != null && questTargetCount > 0) {
                        float progress = Math.min(1.0f, (float) questCurrentCount / questTargetCount);
                        questBossBar.setProgress(progress);
                        questBossBar.setName(Component.literal("§e[任务] §f" + questDesc + " (" + questCurrentCount + "/"
                                        + questTargetCount + ")"));

                        if (questCurrentCount >= questTargetCount) {
                                completeQuest();
                        }
                }
        }

        private static void completeQuest() {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server != null) {
                        questBossBar.setVisible(false);
                        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                questBossBar.removePlayer(p);
                        }
                        server.getPlayerList().broadcastSystemMessage(Component.literal("§a[任务完成！] §f" + questDesc),
                                        false);
                }

                SentientMCClient.addMessageToBatch("SYSTEM",
                                "[SYSTEM FEEDBACK] 玩家已完成任务: " + questDesc + "。你可以进行下一步或给予奖励。", null);

                // Reset
                questType = "";
                questTarget = "";
                questTargetCount = 0;
                questCurrentCount = 0;
                questDesc = "";
        }

        public static void loadPermissions() {
                try {
                        File chatFile = new File("config/ai_chat_whitelist.json");
                        if (chatFile.exists()) {
                                Type listType = new TypeToken<List<String>>() {
                                }.getType();
                                chatWhitelist = GSON.fromJson(new FileReader(chatFile), listType);
                        } else {
                                // Default, allow everyone? Or let's just make it empty default
                                // For ease of use out-of-the-box, we'll keep it empty meaning "no whitelist
                                // enforced yet"
                                // unless you want to lock it down strictly. Let's say if the list is empty,
                                // everyone can use it.
                        }

                        File cmdFile = new File("config/ai_command_whitelist.json");
                        if (cmdFile.exists()) {
                                Type listType = new TypeToken<List<String>>() {
                                }.getType();
                                commandWhitelist = GSON.fromJson(new FileReader(cmdFile), listType);
                        }
                } catch (Exception e) {
                        LOGGER.error("[SentientMCMod] Failed to load whitelist permissions", e);
                }
        }

        public static void savePermissions() {
                try {
                        new File("config").mkdirs();
                        try (FileWriter w = new FileWriter("config/ai_chat_whitelist.json")) {
                                GSON.toJson(chatWhitelist, w);
                        }
                        try (FileWriter w = new FileWriter("config/ai_command_whitelist.json")) {
                                GSON.toJson(commandWhitelist, w);
                        }
                } catch (Exception e) {
                        LOGGER.error("[SentientMCMod] Failed to save whitelist permissions", e);
                }
        }

        private void checkProactiveChat() {
                if (!RT_ENABLE_PROACTIVE_CHAT)
                        return;
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null)
                        return;

                long now = System.currentTimeMillis();
                long intervalMs = RT_PROACTIVE_INTERVAL * 60L * 1000L;

                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        String pName = player.getName().getString();
                        long lastTime = lastInteractionTimes.getOrDefault(pName, now);

                        // If we haven't tracked this player yet, or it's been longer than the interval
                        if (now - lastTime >= intervalMs || !lastInteractionTimes.containsKey(pName)) {
                                LOGGER.info("[SentientMCMod] Triggering proactive chat for {}", pName);
                                lastInteractionTimes.put(pName, now); // Reset timer

                                String envState = getEnvironmentState(player);
                                SentientMCClient.addMessageToBatch(pName,
                                                "[SYSTEM] The player has been quiet for a while. If you see something interesting in their environment or inventory, proactively start a conversation. Do not mention that this is a system prompt.",
                                                envState);
                        }
                }
        }

        @SubscribeEvent
        public void onServerChat(ServerChatEvent event) {
                handleUserMessage(event.getPlayer(), event.getRawText());
        }

        @SubscribeEvent
        public void onBlockPlace(BlockEvent.EntityPlaceEvent event) {
                if (!"BUILD".equals(questType))
                        return;
                if (questBossBar == null || !questBossBar.isVisible())
                        return;
                if (!(event.getEntity() instanceof ServerPlayer))
                        return;

                // Check if this block matches the quest target (e.g. "minecraft:stone_bricks")
                net.minecraft.resources.ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.BLOCKS
                                .getKey(event.getPlacedBlock().getBlock());
                String placedBlockId = key != null ? key.toString() : "";
                if (placedBlockId.equals(questTarget)) {
                        questCurrentCount++;
                        updateBossBar();
                        LOGGER.debug("[SentientMCMod] BUILD quest progress: {}/{} ({})", questCurrentCount,
                                        questTargetCount, questTarget);
                }
        }

        @SubscribeEvent
        public void onLivingDeath(LivingDeathEvent event) {
                if (!"KILL".equals(questType))
                        return;
                if (questBossBar == null || !questBossBar.isVisible())
                        return;

                // Check if the killer is a player
                if (!(event.getSource().getEntity() instanceof ServerPlayer))
                        return;

                // Get entity type ID e.g. "minecraft:zombie"
                net.minecraft.resources.ResourceLocation key = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES
                                .getKey(event.getEntity().getType());
                String entityId = key != null ? key.toString() : "";
                if (entityId.equals(questTarget)) {
                        questCurrentCount++;
                        updateBossBar();
                        LOGGER.debug("[SentientMCMod] KILL quest progress: {}/{} ({})", questCurrentCount,
                                        questTargetCount, questTarget);
                }
        }

        @SubscribeEvent
        public void onRegisterCommands(RegisterCommandsEvent event) {
                event.getDispatcher().register(Commands.literal("ai")
                                .then(Commands.argument("message", StringArgumentType.greedyString())
                                                .executes(context -> {
                                                        ServerPlayer player = context.getSource()
                                                                        .getPlayerOrException();
                                                        String message = StringArgumentType.getString(context,
                                                                        "message");
                                                        handleUserMessage(player, "[私聊] " + message);
                                                        return 1;
                                                })));

                // /ai permit chat <add/remove> <player>
                event.getDispatcher().register(Commands.literal("aipermit").requires(s -> s.hasPermission(2))
                                .then(Commands.literal("chat")
                                                .then(Commands.literal("add").then(
                                                                Commands.argument("player", StringArgumentType.string())
                                                                                .executes(c -> {
                                                                                        String p = StringArgumentType
                                                                                                        .getString(c, "player");
                                                                                        if (!chatWhitelist.contains(p))
                                                                                                chatWhitelist.add(p);
                                                                                        savePermissions();
                                                                                        c.getSource().sendSuccess(
                                                                                                        () -> Component.literal(
                                                                                                                        "Added " + p + " to chat whitelist."),
                                                                                                        true);
                                                                                        return 1;
                                                                                })))
                                                .then(Commands.literal("remove").then(
                                                                Commands.argument("player", StringArgumentType.string())
                                                                                .executes(c -> {
                                                                                        String p = StringArgumentType
                                                                                                        .getString(c, "player");
                                                                                        chatWhitelist.remove(p);
                                                                                        savePermissions();
                                                                                        c.getSource().sendSuccess(
                                                                                                        () -> Component.literal(
                                                                                                                        "Removed " + p + " from chat whitelist."),
                                                                                                        true);
                                                                                        return 1;
                                                                                }))))
                                .then(Commands.literal("command")
                                                .then(Commands.literal("add").then(
                                                                Commands.argument("player", StringArgumentType.string())
                                                                                .executes(c -> {
                                                                                        String p = StringArgumentType
                                                                                                        .getString(c, "player");
                                                                                        if (!commandWhitelist
                                                                                                        .contains(p))
                                                                                                commandWhitelist.add(p);
                                                                                        savePermissions();
                                                                                        c.getSource().sendSuccess(
                                                                                                        () -> Component.literal(
                                                                                                                        "Added " + p + " to command whitelist."),
                                                                                                        true);
                                                                                        return 1;
                                                                                })))
                                                .then(Commands.literal("remove").then(
                                                                Commands.argument("player", StringArgumentType.string())
                                                                                .executes(c -> {
                                                                                        String p = StringArgumentType
                                                                                                        .getString(c, "player");
                                                                                        commandWhitelist.remove(p);
                                                                                        savePermissions();
                                                                                        c.getSource().sendSuccess(
                                                                                                        () -> Component.literal(
                                                                                                                        "Removed " + p + " from command whitelist."),
                                                                                                        true);
                                                                                        return 1;
                                                                                })))));

                // /aiconfig set <key> <value> —— 仅 OP 可用，key 有自动补全
                SuggestionProvider<CommandSourceStack> keySuggestions = (ctx, builder) -> {
                        for (String k : new String[] { "apikey", "apiurl", "modelname", "ainame", "systemprompt",
                                        "memorysize", "batchdelay", "proactiveinterval", "maxcommandchain",
                                        "commands", "events", "stateinjection", "batching", "proactivechat",
                                        "entityscan", "inventoryscan", "debug" }) {
                                builder.suggest(k);
                        }
                        return builder.buildFuture();
                };

                event.getDispatcher().register(Commands.literal("aiconfig").requires(s -> s.hasPermission(2))
                                .then(Commands.literal("set")
                                                .then(Commands.argument("key", StringArgumentType.string())
                                                                .suggests(keySuggestions)
                                                                .then(Commands.argument("value",
                                                                                StringArgumentType.greedyString())
                                                                                .executes(c -> {
                                                                                        String key = StringArgumentType
                                                                                                        .getString(c, "key")
                                                                                                        .toLowerCase();
                                                                                        String value = StringArgumentType
                                                                                                        .getString(c, "value");
                                                                                        try {
                                                                                                switch (key) {
                                                                                                        case "apikey":
                                                                                                                RT_API_KEY = value;
                                                                                                                break;
                                                                                                        case "apiurl":
                                                                                                                RT_API_URL = value;
                                                                                                                break;
                                                                                                        case "modelname":
                                                                                                                RT_MODEL_NAME = value;
                                                                                                                break;
                                                                                                        case "systemprompt":
                                                                                                                RT_SYSTEM_PROMPT = value;
                                                                                                                break;
                                                                                                        case "ainame":
                                                                                                                RT_AI_NAME = value;
                                                                                                                break;
                                                                                                        case "memorysize":
                                                                                                                RT_MEMORY_SIZE = Integer
                                                                                                                                .parseInt(value);
                                                                                                                break;
                                                                                                        case "batchdelay":
                                                                                                                RT_BATCH_DELAY = Integer
                                                                                                                                .parseInt(value);
                                                                                                                break;
                                                                                                        case "proactiveinterval":
                                                                                                                RT_PROACTIVE_INTERVAL = Integer
                                                                                                                                .parseInt(value);
                                                                                                                break;
                                                                                                        case "maxcommandchain":
                                                                                                                RT_MAX_COMMAND_CHAIN = Integer
                                                                                                                                .parseInt(value);
                                                                                                                break;
                                                                                                        case "commands":
                                                                                                                RT_ENABLE_COMMANDS = Boolean
                                                                                                                                .parseBoolean(value);
                                                                                                                break;
                                                                                                        case "events":
                                                                                                                RT_ENABLE_EVENTS = Boolean
                                                                                                                                .parseBoolean(value);
                                                                                                                break;
                                                                                                        case "stateinjection":
                                                                                                                RT_ENABLE_STATE_INJECTION = Boolean
                                                                                                                                .parseBoolean(value);
                                                                                                                break;
                                                                                                        case "batching":
                                                                                                                RT_ENABLE_BATCHING = Boolean
                                                                                                                                .parseBoolean(value);
                                                                                                                break;
                                                                                                        case "proactivechat":
                                                                                                                RT_ENABLE_PROACTIVE_CHAT = Boolean
                                                                                                                                .parseBoolean(value);
                                                                                                                break;
                                                                                                        case "entityscan":
                                                                                                                RT_ENABLE_ENTITY_SCAN = Boolean
                                                                                                                                .parseBoolean(value);
                                                                                                                break;
                                                                                                        case "inventoryscan":
                                                                                                                RT_ENABLE_INVENTORY_SCAN = Boolean
                                                                                                                                .parseBoolean(value);
                                                                                                                break;
                                                                                                        case "debug":
                                                                                                                RT_DEBUG_MODE = Boolean
                                                                                                                                .parseBoolean(value);
                                                                                                                break;
                                                                                                        default:
                                                                                                                c.getSource().sendFailure(
                                                                                                                                Component.literal(
                                                                                                                                                "\u00a7cUnknown key '"
                                                                                                                                                                + key
                                                                                                                                                                + "'. Tab to see valid keys."));
                                                                                                                return 0;
                                                                                                }
                                                                                                c.getSource().sendSuccess(
                                                                                                                () -> Component.literal(
                                                                                                                                "\u00a7a[SentientMCMod] "
                                                                                                                                                + key
                                                                                                                                                + " = "
                                                                                                                                                + value),
                                                                                                                true);
                                                                                                return 1;
                                                                                        } catch (NumberFormatException e) {
                                                                                                c.getSource().sendFailure(
                                                                                                                Component.literal(
                                                                                                                                "\u00a7cInvalid number: "
                                                                                                                                                + value));
                                                                                                return 0;
                                                                                        } catch (Exception e) {
                                                                                                c.getSource().sendFailure(
                                                                                                                Component.literal(
                                                                                                                                "\u00a7cError: " + e
                                                                                                                                                .getMessage()));
                                                                                                return 0;
                                                                                        }
                                                                                }))))
                                .then(Commands.literal("clearmemory").requires(s -> s.hasPermission(2))
                                                .executes(c -> {
                                                        SentientMCClient.clearMemory();
                                                        c.getSource().sendSuccess(() -> Component.literal(
                                                                        "\u00a7a[SentientMCMod] AI memory cleared."),
                                                                        true);
                                                        return 1;
                                                })));
        }

        @SubscribeEvent
        public void onPlayerDeath(LivingDeathEvent event) {
                if (!SentientMCConfig.ENABLE_EVENTS.get())
                        return;
                if (event.getEntity() instanceof ServerPlayer) {
                        ServerPlayer player = (ServerPlayer) event.getEntity();
                        String deathMsg = event.getSource().getLocalizedDeathMessage(player).getString();
                        handleEventMessage(player, "系统消息：玩家死亡 - " + deathMsg);
                }
        }

        @SubscribeEvent
        public void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
                if (!SentientMCConfig.ENABLE_EVENTS.get())
                        return;
                if (event.getEntity() instanceof ServerPlayer) {
                        ServerPlayer player = (ServerPlayer) event.getEntity();
                        net.minecraft.advancements.DisplayInfo display = event.getAdvancement().getDisplay();
                        if (display != null && display.shouldAnnounceChat()) {
                                String advName = display.getTitle().getString();
                                handleEventMessage(player, "系统消息：玩家达成了成就 [" + advName + "]");
                        }
                }
        }

        private void handleUserMessage(ServerPlayer player, String message) {
                String playerName = player.getName().getString();

                // Permission Check: if whitelist has entries, player must be in it
                if (!chatWhitelist.isEmpty() && !chatWhitelist.contains(playerName)) {
                        return;
                }

                // Track who triggered this for command permission checks later
                lastTriggerPlayer = playerName;

                LOGGER.info("[SentientMCMod] Intercepted message from {}: {}", playerName, message);
                if (playerName.equalsIgnoreCase(RT_AI_NAME) || playerName.equalsIgnoreCase("AI"))
                        return;

                String envState = getEnvironmentState(player);
                lastInteractionTimes.put(playerName, System.currentTimeMillis());
                SentientMCClient.addMessageToBatch(playerName, message, envState);
        }

        private void handleEventMessage(ServerPlayer player, String eventMessage) {
                String playerName = player.getName().getString();
                LOGGER.info("[SentientMCMod] Intercepted event for {}: {}", playerName, eventMessage);

                String envState = getEnvironmentState(player);
                lastInteractionTimes.put(playerName, System.currentTimeMillis());
                SentientMCClient.addMessageToBatch(playerName, eventMessage, envState);
        }

        private String getEnvironmentState(ServerPlayer player) {
                BlockPos pos = player.blockPosition();
                String time = player.level().isDay() ? "白天" : "夜晚";
                String weather = player.level().isRaining() ? "下雨" : (player.level().isThundering() ? "雷雨" : "晴朗");
                String dimension = player.level().dimension().location().getPath();

                StringBuilder stateBuilder = new StringBuilder();
                stateBuilder.append(String.format("玩家位置: %d, %d, %d | 维度: %s | 时间: %s | 天气: %s",
                                pos.getX(), pos.getY(), pos.getZ(), dimension, time, weather));

                if (SentientMCConfig.ENABLE_BIOME_SENSE.get()) {
                        String biome = player.level().getBiome(pos).unwrapKey().map(k -> k.location().getPath())
                                        .orElse("未知");
                        stateBuilder.append(" | 群系: ").append(biome);
                }

                if (!questType.isEmpty() && questTargetCount > 0) {
                        stateBuilder.append("\n[当前活动任务]: {").append(questType).append("} ").append(questDesc)
                                        .append(" (").append(questCurrentCount).append("/").append(questTargetCount)
                                        .append(") (系统会在玩家完成时自动通知你，无需你判定)");
                }

                if (SentientMCConfig.ENABLE_ENTITY_SCAN.get()) {
                        AABB scanArea = player.getBoundingBox().inflate(16.0D);
                        List<Entity> entities = player.level().getEntities(player, scanArea);
                        if (!entities.isEmpty()) {
                                stateBuilder.append("\n[周围16格实体]: ");
                                java.util.Map<String, Integer> entityCounts = new java.util.HashMap<>();
                                for (Entity e : entities) {
                                        String name = e.getType().getDescription().getString();
                                        entityCounts.put(name, entityCounts.getOrDefault(name, 0) + 1);
                                }
                                for (java.util.Map.Entry<String, Integer> entry : entityCounts.entrySet()) {
                                        stateBuilder.append(entry.getKey()).append("x").append(entry.getValue())
                                                        .append(", ");
                                }
                        }
                }

                if (SentientMCConfig.ENABLE_INVENTORY_SCAN.get()) {
                        stateBuilder.append("\n[玩家装备]: ");

                        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD);
                        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
                        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
                        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
                        ItemStack mainHand = player.getItemBySlot(EquipmentSlot.MAINHAND);
                        ItemStack offHand = player.getItemBySlot(EquipmentSlot.OFFHAND);

                        stateBuilder.append("主手=")
                                        .append(mainHand.isEmpty() ? "空" : mainHand.getHoverName().getString())
                                        .append(", ");
                        stateBuilder.append("副手=").append(offHand.isEmpty() ? "空" : offHand.getHoverName().getString())
                                        .append(", ");
                        stateBuilder.append("头盔=").append(head.isEmpty() ? "无" : head.getHoverName().getString())
                                        .append(", ");
                        stateBuilder.append("胸甲=").append(chest.isEmpty() ? "无" : chest.getHoverName().getString())
                                        .append(", ");
                        stateBuilder.append("护腿=").append(legs.isEmpty() ? "无" : legs.getHoverName().getString())
                                        .append(", ");
                        stateBuilder.append("靴子=").append(feet.isEmpty() ? "无" : feet.getHoverName().getString());
                }

                if (RT_DEBUG_MODE) {
                        player.displayClientMessage(Component.literal("§d[Debug] 语义采集完成: " + stateBuilder.toString()),
                                        false);
                }

                return stateBuilder.toString();
        }

        public static void processAndBroadcastAiResponse(String aiResponse, Runnable onCommandFeedback) {
                MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
                if (server == null)
                        return;

                LOGGER.info("[SentientMCMod] Broadcasting AI response: {}", aiResponse);

                // Initialize BossBar if it doesn't exist
                if (questBossBar == null) {
                        questBossBar = new ServerBossEvent(Component.literal(""), BossEvent.BossBarColor.YELLOW,
                                        BossEvent.BossBarOverlay.PROGRESS);
                        questBossBar.setProgress(1.0f);
                }

                // Parse potential commands
                if (RT_ENABLE_COMMANDS) {
                        boolean commandsFound = false;
                        List<String> commandsToExecute = new ArrayList<>();

                        Pattern cmdPattern = Pattern.compile("\\[COMMAND: (.*?)\\]");
                        Matcher matcher = cmdPattern.matcher(aiResponse);
                        while (matcher.find()) {
                                commandsFound = true;
                                String cmd = matcher.group(1).trim();
                                if (cmd.startsWith("/"))
                                        cmd = cmd.substring(1);
                                commandsToExecute.add(cmd);
                        }
                        aiResponse = matcher.replaceAll("").trim();

                        if (commandsFound) {
                                // COMMAND PERMISSION CHECK
                                // commandWhitelist empty = everyone can trigger commands
                                // commandWhitelist non-empty = only players in the list can trigger commands
                                boolean canExecute = commandWhitelist.isEmpty()
                                                || (!lastTriggerPlayer.isEmpty()
                                                                && commandWhitelist.contains(lastTriggerPlayer));

                                server.execute(() -> {
                                        StringBuilder feedbackBuilder = new StringBuilder();
                                        if (!canExecute) {
                                                feedbackBuilder.append(
                                                                "ERROR: The server has restricted command execution. You do not have permission.");
                                        } else {
                                                for (String finalCmd : commandsToExecute) {
                                                        LOGGER.info("[SentientMCMod] AI executing command: /{}",
                                                                        finalCmd);
                                                        CommandSourceStack source = server.createCommandSourceStack()
                                                                        .withSource(new CommandSource() {
                                                                                @Override
                                                                                public void sendSystemMessage(
                                                                                                Component message) {
                                                                                        feedbackBuilder.append(message
                                                                                                        .getString())
                                                                                                        .append("\n");
                                                                                }

                                                                                @Override
                                                                                public boolean acceptsSuccess() {
                                                                                        return true;
                                                                                }

                                                                                @Override
                                                                                public boolean acceptsFailure() {
                                                                                        return true;
                                                                                }

                                                                                @Override
                                                                                public boolean shouldInformAdmins() {
                                                                                        return false;
                                                                                }
                                                                        });

                                                        try {
                                                                int result = server.getCommands()
                                                                                .performPrefixedCommand(source,
                                                                                                finalCmd);
                                                                if (result > 0) {
                                                                        feedbackBuilder.append("Command /")
                                                                                        .append(finalCmd)
                                                                                        .append(" executed successfully.\n");
                                                                        if (RT_DEBUG_MODE) {
                                                                                server.getPlayerList()
                                                                                                .broadcastSystemMessage(
                                                                                                                Component.literal(
                                                                                                                                "§a[指令成功闭环] -> /"
                                                                                                                                                + finalCmd),
                                                                                                                false);
                                                                        }
                                                                } else {
                                                                        feedbackBuilder.append("Command /")
                                                                                        .append(finalCmd)
                                                                                        .append(" failed to execute. ");
                                                                        // Fallback teaching
                                                                        feedbackBuilder.append(
                                                                                        "\n[Hint: Ensure your command is correct Minecraft syntax. e.g. /give <player> <item> <amount>, /summon <entity> <x> <y> <z>, /time set day]\n");
                                                                        if (RT_DEBUG_MODE) {
                                                                                server.getPlayerList()
                                                                                                .broadcastSystemMessage(
                                                                                                                Component.literal(
                                                                                                                                "§c[系统报错] -> /" + finalCmd
                                                                                                                                                + " (已回传给AI让其自我修正)"),
                                                                                                                false);
                                                                        }
                                                                }
                                                        } catch (Exception e) {
                                                                feedbackBuilder.append("Error executing command /")
                                                                                .append(finalCmd).append(": ")
                                                                                .append(e.getMessage()).append("\n");
                                                                feedbackBuilder.append(
                                                                                "\n[Hint: Ensure your command is correct Minecraft syntax. e.g. /give <player> <item> <amount>, /summon <entity> <x> <y> <z>, /time set day]\n");
                                                                if (RT_DEBUG_MODE) {
                                                                        server.getPlayerList().broadcastSystemMessage(
                                                                                        Component.literal(
                                                                                                        "§c[系统报错] -> /" + finalCmd
                                                                                                                        + " (已回传给AI让其自我修正)"),
                                                                                        false);
                                                                }
                                                        }
                                                }
                                        }

                                        if (onCommandFeedback != null) {
                                                SentientMCClient.addMessageToBatch("SYSTEM",
                                                                "[COMMAND FEEDBACK]\n" + feedbackBuilder.toString(),
                                                                null);
                                                onCommandFeedback.run();
                                        }
                                });
                        }
                }

                // Parse potential quests [QUEST: TYPE:TARGET:COUNT:DESCRIPTION]
                // Example: [QUEST: ITEM_GATHER:minecraft:apple:5:收集5个苹果]
                // [QUEST: KILL:minecraft:zombie:3:击杀3只僵尸]
                Pattern questPattern = Pattern.compile("\\[QUEST: (.*?):(.*?):(\\d+):(.*?)\\]");
                Matcher questMatcher = questPattern.matcher(aiResponse);
                while (questMatcher.find()) {
                        questType = questMatcher.group(1).trim();
                        questTarget = questMatcher.group(2).trim();
                        try {
                                questTargetCount = Integer.parseInt(questMatcher.group(3).trim());
                        } catch (Exception e) {
                                questTargetCount = 1;
                        }
                        questDesc = questMatcher.group(4).trim();
                        questCurrentCount = 0;

                        LOGGER.info("[SentientMCMod] AI issued new structured quest: Type={}, Target={}, Count={}, Desc={}",
                                        questType, questTarget, questTargetCount, questDesc);

                        server.execute(() -> {
                                questBossBar.setVisible(true);
                                updateBossBar();
                                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                        questBossBar.addPlayer(p);
                                }
                        });

                        server.getPlayerList().broadcastSystemMessage(
                                        Component.literal("§e[新任务] §a" + questDesc + " §7(0/" + questTargetCount + ")"),
                                        false);
                }
                aiResponse = questMatcher.replaceAll("").trim();

                // Add newly joined players to the quest bar if there is an active quest
                if (!questType.isEmpty() && questBossBar != null) {
                        server.execute(() -> {
                                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                                        questBossBar.addPlayer(p);
                                }
                        });
                }

                if (!aiResponse.isEmpty()) {
                        server.getPlayerList().broadcastSystemMessage(
                                        Component.literal("§b[" + RT_AI_NAME + "] §f" + aiResponse),
                                        false);
                }
        }
}
