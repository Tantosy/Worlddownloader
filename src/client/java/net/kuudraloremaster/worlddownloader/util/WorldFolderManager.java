package net.kuudraloremaster.worlddownloader.util;

import net.kuudraloremaster.worlddownloader.util.LevelStorageDirAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.level.storage.LevelStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class WorldFolderManager {

    private static boolean downloadedWorldsActive = false;

    public static boolean isDownloadedWorldsActive() {
        return downloadedWorldsActive;
    }

    public static boolean hasDownloadedWorlds(MinecraftClient client) {
        Path dlPath = client.runDirectory.toPath().resolve("downloaded_worlds");
        if (!Files.isDirectory(dlPath)) return false;
        try (Stream<Path> stream = Files.list(dlPath)) {
            return stream.anyMatch(Files::isDirectory);
        } catch (IOException e) {
            return false;
        }
    }

    public static void toggle(MinecraftClient client) {
        LevelStorage levelStorage = client.getLevelStorage();
        if (!downloadedWorldsActive) {
            Path dlPath = client.runDirectory.toPath().resolve("downloaded_worlds");
            try {
                Files.createDirectories(dlPath);
            } catch (IOException ignored) {}
            ((LevelStorageDirAccessor) levelStorage).worlddownloader$setSavesDirectory(dlPath);
            downloadedWorldsActive = true;
        } else {
            Path savesPath = client.runDirectory.toPath().resolve("saves");
            ((LevelStorageDirAccessor) levelStorage).worlddownloader$setSavesDirectory(savesPath);
            downloadedWorldsActive = false;
        }
    }
}
