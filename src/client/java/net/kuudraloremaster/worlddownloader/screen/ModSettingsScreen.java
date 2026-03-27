package net.kuudraloremaster.worlddownloader.screen;

import net.kuudraloremaster.worlddownloader.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Settings screen for World Downloader with modern two-column layout.
 * Left: Category list (General, Export)
 * Right: Settings with ON/OFF toggles
 */
public class ModSettingsScreen extends Screen {

    private final Screen parent;
    private Category currentCategory = Category.GENERAL;

    // Category buttons
    private ButtonWidget generalButton;
    private ButtonWidget exportButton;

    // General settings
    private ButtonWidget autoExportServerButton;
    private ButtonWidget autoExportSingleplayerButton;
    private ButtonWidget autoStartServerButton;
    private ButtonWidget autoStartSingleplayerButton;

    // Export settings
    private ButtonWidget exportWithTexturePackButton;
    private ButtonWidget saveContainersButton;

    // Common buttons
    private ButtonWidget resetButton;
    private ButtonWidget doneButton;

    public ModSettingsScreen(Screen parent) {
        super(Text.translatable("worlddownloader.config.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        int leftPanelWidth = 150;
        int leftX = 20;
        int rightX = leftX + leftPanelWidth + 20;
        int rightPanelWidth = width - rightX - 20;
        int buttonHeight = 20;
        int spacing = 8;

        // === LEFT PANEL - Categories ===
        int categoryY = 30;
        generalButton = ButtonWidget.builder(
            Text.translatable("worlddownloader.config.category.general"),
            b -> switchCategory(Category.GENERAL)
        ).dimensions(leftX, categoryY, leftPanelWidth, buttonHeight).build();

        exportButton = ButtonWidget.builder(
            Text.translatable("worlddownloader.config.category.export"),
            b -> switchCategory(Category.EXPORT)
        ).dimensions(leftX, categoryY + buttonHeight + spacing, leftPanelWidth, buttonHeight).build();

        addDrawableChild(generalButton);
        addDrawableChild(exportButton);

        // === RIGHT PANEL - Settings ===
        int settingStartY = 30;
        int settingButtonWidth = rightPanelWidth;

        // General settings
        autoExportServerButton = ButtonWidget.builder(
            getToggleText("worlddownloader.config.autoExportServer", ModConfig.getInstance().isAutoExportServer()),
            b -> toggleAutoExportServer()
        ).dimensions(rightX, settingStartY, settingButtonWidth, buttonHeight).build();

        autoExportSingleplayerButton = ButtonWidget.builder(
            getToggleText("worlddownloader.config.autoExportSingleplayer", ModConfig.getInstance().isAutoExportSingleplayer()),
            b -> toggleAutoExportSingleplayer()
        ).dimensions(rightX, settingStartY + (buttonHeight + spacing) * 1, settingButtonWidth, buttonHeight).build();

        autoStartServerButton = ButtonWidget.builder(
            getToggleText("worlddownloader.config.autoStartServer", ModConfig.getInstance().isAutoStartServer()),
            b -> toggleAutoStartServer()
        ).dimensions(rightX, settingStartY + (buttonHeight + spacing) * 2, settingButtonWidth, buttonHeight).build();

        autoStartSingleplayerButton = ButtonWidget.builder(
            getToggleText("worlddownloader.config.autoStartSingleplayer", ModConfig.getInstance().isAutoStartSingleplayer()),
            b -> toggleAutoStartSingleplayer()
        ).dimensions(rightX, settingStartY + (buttonHeight + spacing) * 3, settingButtonWidth, buttonHeight).build();

        // Export settings
        exportWithTexturePackButton = ButtonWidget.builder(
            getToggleText("worlddownloader.config.exportWithTexturePack", ModConfig.getInstance().isExportWithTexturePack()),
            b -> toggleExportWithTexturePack()
        ).dimensions(rightX, settingStartY, settingButtonWidth, buttonHeight).build();

        saveContainersButton = ButtonWidget.builder(
            getToggleText("worlddownloader.config.saveContainers", ModConfig.getInstance().isSaveContainers()),
            b -> toggleSaveContainers()
        ).dimensions(rightX, settingStartY + (buttonHeight + spacing) * 1, settingButtonWidth, buttonHeight).build();

        // Reset button (bottom left)
        resetButton = ButtonWidget.builder(
            Text.translatable("worlddownloader.config.reset"),
            b -> resetToDefaults()
        ).dimensions(leftX, height - 50, leftPanelWidth, buttonHeight).build();

        // Done button (bottom center)
        doneButton = ButtonWidget.builder(
            Text.translatable("worlddownloader.config.done"),
            b -> close()
        ).dimensions(width / 2 - 100, height - 30, 200, buttonHeight).build();

        addDrawableChild(resetButton);
        addDrawableChild(doneButton);

        updateCategoryVisibility();
        updateCategoryButtons();
    }

    private void switchCategory(Category category) {
        currentCategory = category;
        updateCategoryVisibility();
        updateCategoryButtons();
    }

    private void updateCategoryButtons() {
        // Highlight selected category
        generalButton.setMessage(Text.translatable("worlddownloader.config.category.general")
            .copy().formatted(currentCategory == Category.GENERAL ? Formatting.WHITE : Formatting.GRAY));
        exportButton.setMessage(Text.translatable("worlddownloader.config.category.export")
            .copy().formatted(currentCategory == Category.EXPORT ? Formatting.WHITE : Formatting.GRAY));
    }

    private Text getToggleText(String translationKey, boolean value) {
        Text name = Text.translatable(translationKey);
        Text status = Text.translatable(value ? "worlddownloader.config.on" : "worlddownloader.config.off")
            .formatted(value ? Formatting.GREEN : Formatting.RED);
        return name.copy().append(Text.literal(": ")).append(status);
    }

    private void toggleAutoExportServer() {
        ModConfig config = ModConfig.getInstance();
        config.setAutoExportServer(!config.isAutoExportServer());
        autoExportServerButton.setMessage(getToggleText("worlddownloader.config.autoExportServer", config.isAutoExportServer()));
    }

    private void toggleAutoExportSingleplayer() {
        ModConfig config = ModConfig.getInstance();
        config.setAutoExportSingleplayer(!config.isAutoExportSingleplayer());
        autoExportSingleplayerButton.setMessage(getToggleText("worlddownloader.config.autoExportSingleplayer", config.isAutoExportSingleplayer()));
    }

    private void toggleExportWithTexturePack() {
        ModConfig config = ModConfig.getInstance();
        config.setExportWithTexturePack(!config.isExportWithTexturePack());
        exportWithTexturePackButton.setMessage(getToggleText("worlddownloader.config.exportWithTexturePack", config.isExportWithTexturePack()));
    }

    private void toggleSaveContainers() {
        ModConfig config = ModConfig.getInstance();
        config.setSaveContainers(!config.isSaveContainers());
        saveContainersButton.setMessage(getToggleText("worlddownloader.config.saveContainers", config.isSaveContainers()));
    }

    private void toggleAutoStartServer() {
        ModConfig config = ModConfig.getInstance();
        config.setAutoStartServer(!config.isAutoStartServer());
        autoStartServerButton.setMessage(getToggleText("worlddownloader.config.autoStartServer", config.isAutoStartServer()));
    }

    private void toggleAutoStartSingleplayer() {
        ModConfig config = ModConfig.getInstance();
        config.setAutoStartSingleplayer(!config.isAutoStartSingleplayer());
        autoStartSingleplayerButton.setMessage(getToggleText("worlddownloader.config.autoStartSingleplayer", config.isAutoStartSingleplayer()));
    }

    private void resetToDefaults() {
        ModConfig.getInstance().resetToDefaults();
        autoExportServerButton.setMessage(getToggleText("worlddownloader.config.autoExportServer", ModConfig.getInstance().isAutoExportServer()));
        autoExportSingleplayerButton.setMessage(getToggleText("worlddownloader.config.autoExportSingleplayer", ModConfig.getInstance().isAutoExportSingleplayer()));
        autoStartServerButton.setMessage(getToggleText("worlddownloader.config.autoStartServer", ModConfig.getInstance().isAutoStartServer()));
        autoStartSingleplayerButton.setMessage(getToggleText("worlddownloader.config.autoStartSingleplayer", ModConfig.getInstance().isAutoStartSingleplayer()));
        exportWithTexturePackButton.setMessage(getToggleText("worlddownloader.config.exportWithTexturePack", ModConfig.getInstance().isExportWithTexturePack()));
        saveContainersButton.setMessage(getToggleText("worlddownloader.config.saveContainers", ModConfig.getInstance().isSaveContainers()));
    }

    private void updateCategoryVisibility() {
        // Remove all setting buttons
        remove(autoExportServerButton);
        remove(autoExportSingleplayerButton);
        remove(autoStartServerButton);
        remove(autoStartSingleplayerButton);
        remove(exportWithTexturePackButton);
        remove(saveContainersButton);

        if (currentCategory == Category.GENERAL) {
            addDrawableChild(autoExportServerButton);
            addDrawableChild(autoExportSingleplayerButton);
            addDrawableChild(autoStartServerButton);
            addDrawableChild(autoStartSingleplayerButton);
        } else {
            addDrawableChild(exportWithTexturePackButton);
            addDrawableChild(saveContainersButton);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Render background and buttons
        super.render(context, mouseX, mouseY, delta);

        // Draw title and category label
        Text title = Text.translatable("worlddownloader.config.title");
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 12, 0xFFFFFF);

        Text categoryLabel = currentCategory == Category.GENERAL
            ? Text.translatable("worlddownloader.config.category.general")
            : Text.translatable("worlddownloader.config.category.export");
        context.drawText(textRenderer, categoryLabel, 190, 12, 0xAAAAAA, true);
    }

    @Override
    public void close() {
        client.setScreen(parent);
    }

    private enum Category {
        GENERAL,
        EXPORT
    }
}
