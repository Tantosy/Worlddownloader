package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kuudraloremaster.worlddownloader.util.WorldFolderManager;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {

    @Final
    @Shadow
    protected Screen parent;

    protected SelectWorldScreenMixin() {
        super(Text.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addDownloadedWorldsButton(CallbackInfo ci) {
        boolean active = WorldFolderManager.isDownloadedWorldsActive();

        // Fall 2: Letzte Welt gelöscht → automatisch zurück zu saves/
        if (active && !WorldFolderManager.hasDownloadedWorlds(this.client)) {
            WorldFolderManager.toggle(this.client);
            this.client.setScreen(new SelectWorldScreen(this.parent));
            return;
        }

        // Fall 1: Button verstecken wenn downloaded_worlds/ leer und noch nicht aktiv
        if (!active && !WorldFolderManager.hasDownloadedWorlds(this.client)) {
            return;
        }

        Text label = active
                ? Text.translatable("worlddownloader.gui.normal_worlds")
                : Text.translatable("worlddownloader.gui.downloaded_worlds");

        this.addDrawableChild(
                ButtonWidget.builder(label, btn -> {
                    WorldFolderManager.toggle(this.client);
                    this.client.setScreen(new SelectWorldScreen(this.parent));
                }).dimensions(this.width / 2 + 156, this.height - 52, 150, 20).build()
        );
    }
}
