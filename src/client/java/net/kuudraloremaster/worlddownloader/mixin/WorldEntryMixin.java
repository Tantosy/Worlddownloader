package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kuudraloremaster.worlddownloader.util.WorldFolderManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.screen.world.WorldListWidget;
import net.minecraft.world.level.storage.LevelSummary;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
@Mixin(targets = "net.minecraft.client.gui.screen.world.WorldListWidget$WorldEntry")
public class WorldEntryMixin {

    @Shadow private LevelSummary level;

    @Inject(method = "recreate()V", at = @At("HEAD"), cancellable = true)
    private void interceptRecreateForDownloadedWorld(CallbackInfo ci) {
        if (!WorldFolderManager.isDownloadedWorldsActive()) return;

        // Standard-Re-Create (erzeugt Void-Welt) abbrechen und stattdessen Ordner kopieren
        ci.cancel();

        MinecraftClient client = MinecraftClient.getInstance();
        Path savesDir = WorldFolderManager.getSavesDirectory(client);
        Path source = savesDir.resolve(level.getName());
        String destName = findAvailableName(savesDir, level.getName());
        Path dest = savesDir.resolve(destName);

        try {
            copyDirectory(source, dest);
        } catch (IOException e) {
            return;
        }

        client.setScreen(new SelectWorldScreen(null));
    }

    private static String findAvailableName(Path dir, String base) {
        if (!Files.exists(dir.resolve(base))) return base;
        int i = 2;
        while (Files.exists(dir.resolve(base + "_" + i))) i++;
        return base + "_" + i;
    }

    private static void copyDirectory(Path src, Path dest) throws IOException {
        try (Stream<Path> stream = Files.walk(src)) {
            for (Path path : (Iterable<Path>) stream::iterator) {
                Path target = dest.resolve(src.relativize(path));
                if (Files.isDirectory(path)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
