package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(WorldListWidget.class)
public class WorldListWidgetMixin {

    // WorldListWidget.show(List) ruft CreateWorldScreen.show() auf wenn die Weltliste leer ist.
    // Wenn wir im downloaded_worlds/ Modus sind, soll stattdessen einfach die leere Liste angezeigt werden.
    @Redirect(
        method = "show(Ljava/util/List;)V",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/world/CreateWorldScreen;show(Lnet/minecraft/client/MinecraftClient;Ljava/lang/Runnable;)V")
    )
    private void preventAutoCreateWorldOnEmptyList(MinecraftClient client, Runnable onCancel) {
        // Niemals automatisch CreateWorldScreen öffnen
        // Der Nutzer kann über den "Create New World" Button eine Welt erstellen
    }
}
