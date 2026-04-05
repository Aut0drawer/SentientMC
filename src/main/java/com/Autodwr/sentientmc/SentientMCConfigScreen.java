package com.Autodwr.sentientmc;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.util.Objects;


@SuppressWarnings("null")
public class SentientMCConfigScreen extends Screen {
    private final Screen previousScreen;
    // Page 1
    private EditBox apiUrlBox;
    private EditBox apiKeyBox;
    private EditBox modelNameBox;
    private EditBox systemPromptBox;
    private EditBox aiNameBox;
    // Page 2
    private EditBox memorySizeBox;

    private int currentPage = 1;
    
    private boolean tempStateInjection;
    private boolean tempEvents;
    private boolean tempCommands;
    private boolean tempBiomeSense;
    private boolean tempEntityScan;
    private boolean tempInventoryScan;
    private boolean tempProactiveChat;
    private boolean tempBatching;
    private EditBox proactiveIntervalBox;
    private EditBox batchDelayBox;
    private EditBox maxCommandChainBox;

    public SentientMCConfigScreen(Screen previousScreen) {
        super(Component.translatable("sentientmc.config.title"));
        this.previousScreen = previousScreen;
        this.tempStateInjection = SentientMCConfig.ENABLE_STATE_INJECTION.get();
        this.tempEvents = SentientMCConfig.ENABLE_EVENTS.get();
        this.tempCommands = SentientMCConfig.ENABLE_COMMANDS.get();
        this.tempBiomeSense = SentientMCConfig.ENABLE_BIOME_SENSE.get();
        this.tempEntityScan = SentientMCConfig.ENABLE_ENTITY_SCAN.get();
        this.tempInventoryScan = SentientMCConfig.ENABLE_INVENTORY_SCAN.get();
        this.tempProactiveChat = SentientMCConfig.ENABLE_PROACTIVE_CHAT.get();
        this.tempBatching = SentientMCConfig.ENABLE_BATCHING.get();
    }

    @SuppressWarnings("removal")
    public static void registerConfigScreen() {
        net.minecraftforge.fml.ModLoadingContext.get().registerExtensionPoint(
            net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory.class,
            () -> new net.minecraftforge.client.ConfigScreenHandler.ConfigScreenFactory(
                (mc, screen) -> new SentientMCConfigScreen(screen)
            )
        );
    }

    @Override
    protected void init() {
        this.clearWidgets();
        int centerX = this.width / 2;

        if (currentPage == 1) {
            // PAGE 1: API Settings
            this.apiUrlBox = new EditBox(this.font, centerX - 100, 40, 200, 20, Component.translatable("sentientmc.config.apiUrl"));
            this.apiUrlBox.setMaxLength(256);
            this.apiUrlBox.setValue(Objects.requireNonNullElse(SentientMCConfig.API_URL.get(), ""));

            this.addRenderableWidget(this.apiUrlBox);

            this.apiKeyBox = new EditBox(this.font, centerX - 100, 70, 200, 20, Component.translatable("sentientmc.config.apiKey"));
            this.apiKeyBox.setMaxLength(256);
            this.apiKeyBox.setValue(Objects.requireNonNullElse(SentientMCConfig.API_KEY.get(), ""));

            this.addRenderableWidget(this.apiKeyBox);

            this.modelNameBox = new EditBox(this.font, centerX - 100, 100, 200, 20, Component.translatable("sentientmc.config.modelName"));
            this.modelNameBox.setMaxLength(64);
            this.modelNameBox.setValue(Objects.requireNonNullElse(SentientMCConfig.MODEL_NAME.get(), ""));

            this.addRenderableWidget(this.modelNameBox);

            this.aiNameBox = new EditBox(this.font, centerX - 100, 130, 200, 20, Component.translatable("sentientmc.config.aiName"));
            this.aiNameBox.setMaxLength(64);
            this.aiNameBox.setValue(Objects.requireNonNullElse(SentientMCConfig.AI_NAME.get(), ""));

            this.addRenderableWidget(this.aiNameBox);

            this.systemPromptBox = new EditBox(this.font, centerX - 100, 160, 200, 20, Component.translatable("sentientmc.config.systemPrompt"));
            this.systemPromptBox.setMaxLength(1024);
            this.systemPromptBox.setValue(Objects.requireNonNullElse(SentientMCConfig.SYSTEM_PROMPT.get(), ""));

            this.addRenderableWidget(this.systemPromptBox);

            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.nextPage"), button -> {
                savePage1();
                currentPage = 2;
                this.init();
            }).bounds(centerX - 50, 195, 100, 20).build());

        } else if (currentPage == 2) {
            // PAGE 2: Advanced Settings
            this.memorySizeBox = new EditBox(this.font, centerX - 150, 40, 90, 20, Component.translatable("sentientmc.config.memorySize"));
            this.memorySizeBox.setValue(String.valueOf(SentientMCConfig.MEMORY_SIZE.get()));
            this.addRenderableWidget(this.memorySizeBox);

            this.proactiveIntervalBox = new EditBox(this.font, centerX - 45, 40, 90, 20, Component.translatable("sentientmc.config.proactiveInterval"));
            this.proactiveIntervalBox.setValue(String.valueOf(SentientMCConfig.PROACTIVE_INTERVAL.get()));
            this.addRenderableWidget(this.proactiveIntervalBox);

            this.batchDelayBox = new EditBox(this.font, centerX + 60, 40, 90, 20, Component.translatable("sentientmc.config.batchDelay"));
            this.batchDelayBox.setValue(String.valueOf(SentientMCConfig.BATCH_DELAY.get()));
            this.addRenderableWidget(this.batchDelayBox);

            this.maxCommandChainBox = new EditBox(this.font, centerX - 100, 70, 200, 20, Component.translatable("sentientmc.config.maxCommandChain"));
            this.maxCommandChainBox.setValue(String.valueOf(SentientMCConfig.MAX_COMMAND_CHAIN.get()));
            this.addRenderableWidget(this.maxCommandChainBox);

            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.stateInjection").append(": ").append(Component.translatable(tempStateInjection ? "sentientmc.config.on" : "sentientmc.config.off")), button -> {
                tempStateInjection = !tempStateInjection;
                button.setMessage(Component.translatable("sentientmc.config.stateInjection").append(": ").append(Component.translatable(tempStateInjection ? "sentientmc.config.on" : "sentientmc.config.off")));
            }).bounds(centerX - 150, 100, 90, 20).build());

            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.events").append(": ").append(Component.translatable(tempEvents ? "sentientmc.config.on" : "sentientmc.config.off")), button -> {
                tempEvents = !tempEvents;
                button.setMessage(Component.translatable("sentientmc.config.events").append(": ").append(Component.translatable(tempEvents ? "sentientmc.config.on" : "sentientmc.config.off")));
            }).bounds(centerX - 50, 100, 90, 20).build());

            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.commands").append(": ").append(Component.translatable(tempCommands ? "sentientmc.config.on" : "sentientmc.config.off")), button -> {
                tempCommands = !tempCommands;
                button.setMessage(Component.translatable("sentientmc.config.commands").append(": ").append(Component.translatable(tempCommands ? "sentientmc.config.on" : "sentientmc.config.off")));
            }).bounds(centerX + 50, 100, 90, 20).build());

            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.biomeScan").append(": ").append(Component.translatable(tempBiomeSense ? "sentientmc.config.on" : "sentientmc.config.off")), button -> {
                tempBiomeSense = !tempBiomeSense;
                button.setMessage(Component.translatable("sentientmc.config.biomeScan").append(": ").append(Component.translatable(tempBiomeSense ? "sentientmc.config.on" : "sentientmc.config.off")));
            }).bounds(centerX - 150, 130, 90, 20).build());

            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.entityScan").append(": ").append(Component.translatable(tempEntityScan ? "sentientmc.config.on" : "sentientmc.config.off")), button -> {
                tempEntityScan = !tempEntityScan;
                button.setMessage(Component.translatable("sentientmc.config.entityScan").append(": ").append(Component.translatable(tempEntityScan ? "sentientmc.config.on" : "sentientmc.config.off")));
            }).bounds(centerX - 50, 130, 90, 20).build());

            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.invScan").append(": ").append(Component.translatable(tempInventoryScan ? "sentientmc.config.on" : "sentientmc.config.off")), button -> {
                tempInventoryScan = !tempInventoryScan;
                button.setMessage(Component.translatable("sentientmc.config.invScan").append(": ").append(Component.translatable(tempInventoryScan ? "sentientmc.config.on" : "sentientmc.config.off")));
            }).bounds(centerX + 50, 130, 90, 20).build());

            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.proactive").append(": ").append(Component.translatable(tempProactiveChat ? "sentientmc.config.on" : "sentientmc.config.off")), button -> {
                tempProactiveChat = !tempProactiveChat;
                button.setMessage(Component.translatable("sentientmc.config.proactive").append(": ").append(Component.translatable(tempProactiveChat ? "sentientmc.config.on" : "sentientmc.config.off")));
            }).bounds(centerX - 100, 160, 90, 20).build());

            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.batching").append(": ").append(Component.translatable(tempBatching ? "sentientmc.config.on" : "sentientmc.config.off")), button -> {
                tempBatching = !tempBatching;
                button.setMessage(Component.translatable("sentientmc.config.batching").append(": ").append(Component.translatable(tempBatching ? "sentientmc.config.on" : "sentientmc.config.off")));
            }).bounds(centerX + 10, 160, 90, 20).build());


            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.prevPage"), button -> {
                savePage2();
                currentPage = 1;
                this.init();
            }).bounds(centerX - 50, 195, 100, 20).build());

            this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.clearMemory"), button -> {
                SentientMCClient.clearMemory();
                button.active = false;
                button.setMessage(Component.literal("√"));
            }).bounds(centerX + 60, 195, 100, 20).build());
        }

        // Global Bottom Buttons
        this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.saveAll"), button -> {
            if (currentPage == 1) savePage1();
            if (currentPage == 2) savePage2();
            var mc = this.minecraft;
            if (mc != null) mc.setScreen(this.previousScreen);
        }).bounds(centerX - 105, this.height - 30, 100, 20).build());

        this.addRenderableWidget(Button.builder(Component.translatable("sentientmc.config.cancel"), button -> {
            var mc = this.minecraft;
            if (mc != null) mc.setScreen(this.previousScreen);
        }).bounds(centerX + 5, this.height - 30, 100, 20).build());
    }

    private void savePage1() {
        if (apiUrlBox != null) SentientMCConfig.API_URL.set(apiUrlBox.getValue());
        if (apiKeyBox != null) SentientMCConfig.API_KEY.set(apiKeyBox.getValue());
        if (modelNameBox != null) SentientMCConfig.MODEL_NAME.set(modelNameBox.getValue());
        if (aiNameBox != null) SentientMCConfig.AI_NAME.set(aiNameBox.getValue());
        if (systemPromptBox != null) SentientMCConfig.SYSTEM_PROMPT.set(systemPromptBox.getValue());
        SentientMCMod.syncConfigWithRuntime();
    }

    private void savePage2() {
        try { if (memorySizeBox != null) SentientMCConfig.MEMORY_SIZE.set(Integer.parseInt(memorySizeBox.getValue())); } catch (Exception ignored) {}
        try { if (proactiveIntervalBox != null) SentientMCConfig.PROACTIVE_INTERVAL.set(Integer.parseInt(proactiveIntervalBox.getValue())); } catch (Exception ignored) {}
        try { if (batchDelayBox != null) SentientMCConfig.BATCH_DELAY.set(Integer.parseInt(batchDelayBox.getValue())); } catch (Exception ignored) {}
        try { if (maxCommandChainBox != null) SentientMCConfig.MAX_COMMAND_CHAIN.set(Integer.parseInt(maxCommandChainBox.getValue())); } catch (Exception ignored) {}

        SentientMCConfig.ENABLE_STATE_INJECTION.set(tempStateInjection);
        SentientMCConfig.ENABLE_EVENTS.set(tempEvents);
        SentientMCConfig.ENABLE_COMMANDS.set(tempCommands);
        SentientMCConfig.ENABLE_BIOME_SENSE.set(tempBiomeSense);
        SentientMCConfig.ENABLE_ENTITY_SCAN.set(tempEntityScan);
        SentientMCConfig.ENABLE_INVENTORY_SCAN.set(tempInventoryScan);
        SentientMCConfig.ENABLE_PROACTIVE_CHAT.set(tempProactiveChat);
        SentientMCConfig.ENABLE_BATCHING.set(tempBatching);
        SentientMCMod.syncConfigWithRuntime();
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(guiGraphics);
        guiGraphics.drawCenteredString(this.font, Component.translatable("sentientmc.config.title_page", this.title, currentPage), this.width / 2, 10, 16777215);
        
        if (currentPage == 1) {
            guiGraphics.drawString(this.font, Component.translatable("sentientmc.config.apiUrl"), this.width / 2 - 100, 30, 10526880);
            guiGraphics.drawString(this.font, Component.translatable("sentientmc.config.apiKey"), this.width / 2 - 100, 60, 10526880);
            guiGraphics.drawString(this.font, Component.translatable("sentientmc.config.modelName"), this.width / 2 - 100, 90, 10526880);
            guiGraphics.drawString(this.font, Component.translatable("sentientmc.config.aiName"), this.width / 2 - 100, 120, 10526880);
            guiGraphics.drawString(this.font, Component.translatable("sentientmc.config.systemPrompt"), this.width / 2 - 100, 150, 10526880);
        } else if (currentPage == 2) {
            guiGraphics.drawString(this.font, Component.translatable("sentientmc.config.labels2a"), this.width / 2 - 150, 30, 10526880);
            guiGraphics.drawString(this.font, Component.translatable("sentientmc.config.labels2b"), this.width / 2 - 100, 61, 10526880);
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }
}
