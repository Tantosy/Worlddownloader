package net.kuudraloremaster.worlddownloader.util;

import net.kuudraloremaster.worlddownloader.util.LevelStorageDirAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.world.level.storage.LevelStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class WorldFolderManager {

    private static boolean downloadedWorldsActive = false;

    public static boolean isDownloadedWorldsActive() {
        return downloadedWorldsActive;
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
