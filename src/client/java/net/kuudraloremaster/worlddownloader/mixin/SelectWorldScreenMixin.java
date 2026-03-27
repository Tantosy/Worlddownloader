package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kuudraloremaster.worlddownloader.util.WorldFolderManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {

    protected SelectWorldScreenMixin() {
        super(null, null, Text.empty());
    }

    @Inject(method = "init", at = @At("HEAD"))
    private void onInitHead(CallbackInfo ci) {
        this.title = WorldFolderManager.isDownloadedWorldsActive()
                ? Text.literal("Downloaded Worlds")
                : Text.literal("Normal Worlds");
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addDownloadedWorldsButton(CallbackInfo ci) {
        boolean active = WorldFolderManager.isDownloadedWorldsActive();
        boolean hasDownloaded = WorldFolderManager.hasDownloadedWorlds(this.client);

        Text label = active
                ? Text.translatable("worlddownloader.gui.normal_worlds")
                : Text.translatable("worlddownloader.gui.downloaded_worlds");

        ButtonWidget button = ButtonWidget.builder(label, btn -> {
            WorldFolderManager.toggle(this.client);
            this.client.setScreen(new SelectWorldScreen(null));
        }).dimensions(this.width / 2 + 156, this.height - 52, 150, 20).build();

        if (!active && !hasDownloaded) {
            button.active = false;
        }

        this.addDrawableChild(button);
    }
}
