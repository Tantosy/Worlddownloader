package net.kuudraloremaster.worlddownloader.screen;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.AlwaysSelectedEntryListWidget;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class DownloadedWorldsScreen extends Screen {

    private final Screen parent;
    private WorldListWidget worldList;
    private ButtonWidget playButton;
    private ButtonWidget deleteButton;

    public DownloadedWorldsScreen(Screen parent) {
        super(Text.translatable("worlddownloader.gui.downloaded_worlds_title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        this.worldList = new WorldListWidget(this.client, this.width, this.height - 88, 32, 36);
        this.addSelectableChild(this.worldList);

        this.playButton = this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("worlddownloader.gui.play_selected"),
                    btn -> this.playSelectedWorld())
                .dimensions(this.width / 2 - 154, this.height - 52, 150, 20)
                .build()
        );
        this.deleteButton = this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("worlddownloader.gui.delete_world"),
                    btn -> this.confirmDelete())
                .dimensions(this.width / 2 - 2, this.height - 52, 72, 20)
                .build()
        );
        this.addDrawableChild(
            ButtonWidget.builder(Text.translatable("worlddownloader.gui.back"),
                    btn -> this.client.setScreen(this.parent))
                .dimensions(this.width / 2 + 76, this.height - 52, 72, 20)
                .build()
        );

        this.updateButtons();
    }

    void updateButtons() {
        boolean selected = this.worldList != null && this.worldList.getSelectedOrNull() != null;
        this.playButton.active = selected;
        this.deleteButton.active = selected;
    }

    private void playSelectedWorld() {
        WorldListWidget.WorldEntry entry = this.worldList.getSelectedOrNull();
        if (entry == null) return;

        try {
            Path worldPath = this.client.runDirectory.toPath()
                .resolve("downloaded_worlds")
                .resolve(entry.worldName);
            Path savesPath = this.client.runDirectory.toPath().resolve("saves");
            Files.createDirectories(savesPath);
            Path linkPath = savesPath.resolve(entry.worldName);

            if (!Files.exists(linkPath)) {
                Files.createSymbolicLink(linkPath, worldPath.toAbsolutePath());
            }

            this.client.createIntegratedServerLoader().start(entry.worldName, () -> this.client.setScreen(this));
        } catch (Exception e) {
            this.client.setScreen(new ConfirmScreen(
                confirmed -> this.client.setScreen(this),
                Text.translatable("worlddownloader.gui.error_loading"),
                Text.literal(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
            ));
        }
    }

    private void confirmDelete() {
        WorldListWidget.WorldEntry entry = this.worldList.getSelectedOrNull();
        if (entry == null) return;

        this.client.setScreen(new ConfirmScreen(
            confirmed -> {
                if (confirmed) {
                    deleteWorld(entry.worldName);
                }
                this.client.setScreen(this);
            },
            Text.translatable("worlddownloader.gui.delete_confirm_title"),
            Text.translatable("worlddownloader.gui.delete_confirm_body", entry.worldName)
        ));
    }

    private void deleteWorld(String worldName) {
        // Remove symlink from saves/ if present
        Path linkPath = this.client.runDirectory.toPath().resolve("saves").resolve(worldName);
        try { Files.deleteIfExists(linkPath); } catch (Exception ignored) {}

        // Delete actual world folder
        File worldDir = this.client.runDirectory.toPath()
            .resolve("downloaded_worlds").resolve(worldName).toFile();
        deleteRecursive(worldDir);

        this.worldList.reload();
        this.updateButtons();
    }

    private static void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) deleteRecursive(child);
            }
        }
        file.delete();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
        this.worldList.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 14, 0xFFFFFF);
        super.render(context, mouseX, mouseY, delta);
    }

    // -------------------------------------------------------------------------

    class WorldListWidget extends AlwaysSelectedEntryListWidget<WorldListWidget.WorldEntry> {

        WorldListWidget(MinecraftClient client, int width, int height, int top, int itemHeight) {
            super(client, width, height, top, itemHeight);
            this.reload();
        }

        void reload() {
            this.clearEntries();
            File downloadsDir = client.runDirectory.toPath()
                .resolve("downloaded_worlds").toFile();
            if (downloadsDir.isDirectory()) {
                File[] worlds = downloadsDir.listFiles(File::isDirectory);
                if (worlds != null) {
                    for (File world : worlds) {
                        this.addEntry(new WorldEntry(world.getName()));
                    }
                }
            }
        }

        class WorldEntry extends AlwaysSelectedEntryListWidget.Entry<WorldEntry> {

            final String worldName;
            private long lastClickTime = 0;

            WorldEntry(String worldName) {
                this.worldName = worldName;
            }

            @Override
            public Text getNarration() {
                return Text.literal(this.worldName);
            }

            @Override
            public void render(DrawContext context, int index, int y, boolean hovered, float tickDelta) {
                int x = WorldListWidget.this.getRowLeft();
                int entryHeight = WorldListWidget.this.itemHeight;
                context.drawText(
                    client.textRenderer,
                    this.worldName,
                    x + 4,
                    y + (entryHeight - 9) / 2,
                    0xFFFFFF,
                    true
                );
            }

            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                WorldListWidget.this.setSelected(this);
                DownloadedWorldsScreen.this.updateButtons();

                long now = System.currentTimeMillis();
                boolean doubleClick = (now - lastClickTime) < 250;
                lastClickTime = now;
                if (button == 0 && doubleClick) {
                    DownloadedWorldsScreen.this.playSelectedWorld();
                }
                return true;
            }
        }
    }
}
