package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kuudraloremaster.worlddownloader.screen.DownloadedWorldsScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(SelectWorldScreen.class)
public abstract class SelectWorldScreenMixin extends Screen {

    protected SelectWorldScreenMixin() {
        super(Text.empty());
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void addDownloadedWorldsButton(CallbackInfo ci) {
        // Vanilla row 1 has two 150px buttons at (width/2 - 154) and (width/2 - 2).
        // We add ours to the right on the same row.
        this.addDrawableChild(
            ButtonWidget.builder(
                Text.translatable("worlddownloader.gui.downloaded_worlds"),
                btn -> this.client.setScreen(new DownloadedWorldsScreen(this))
            ).dimensions(this.width / 2 + 156, this.height - 52, 150, 20).build()
        );
    }
}
