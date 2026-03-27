package net.kuudraloremaster.worlddownloader.config;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Configuration class for World Downloader settings.
 * Settings are persisted to .minecraft/worlddownloader_config.nbt
 */
public class ModConfig {

    private static final String CONFIG_FILE_NAME = "worlddownloader_config.nbt";

    // General Settings
    private boolean autoExportServer = false;
    private boolean autoExportSingleplayer = true;
    private boolean autoStartServer = false;
    private boolean autoStartSingleplayer = false;

    // Export Settings
    private boolean exportWithTexturePack = true;
    private boolean saveContainers = false;

    private static ModConfig instance;

    private ModConfig() {
        load();
    }

    public static ModConfig getInstance() {
        if (instance == null) {
            instance = new ModConfig();
        }
        return instance;
    }

    // General Settings Getters/Setters
    public boolean isAutoExportServer() {
        return autoExportServer;
    }

    public void setAutoExportServer(boolean autoExportServer) {
        this.autoExportServer = autoExportServer;
        save();
    }

    public boolean isAutoExportSingleplayer() {
        return autoExportSingleplayer;
    }

    public void setAutoExportSingleplayer(boolean autoExportSingleplayer) {
        this.autoExportSingleplayer = autoExportSingleplayer;
        save();
    }

    // Export Settings Getters/Setters
    public boolean isExportWithTexturePack() {
        return exportWithTexturePack;
    }

    public void setExportWithTexturePack(boolean exportWithTexturePack) {
        this.exportWithTexturePack = exportWithTexturePack;
        save();
    }

    public boolean isSaveContainers() {
        return saveContainers;
    }

    public void setSaveContainers(boolean saveContainers) {
        this.saveContainers = saveContainers;
        save();
    }

    // Auto Start Settings Getters/Setters
    public boolean isAutoStartServer() {
        return autoStartServer;
    }

    public void setAutoStartServer(boolean autoStartServer) {
        this.autoStartServer = autoStartServer;
        save();
    }

    public boolean isAutoStartSingleplayer() {
        return autoStartSingleplayer;
    }

    public void setAutoStartSingleplayer(boolean autoStartSingleplayer) {
        this.autoStartSingleplayer = autoStartSingleplayer;
        save();
    }

    private Path getConfigPath() {
        return FabricLoader.getInstance().getGameDir().resolve(CONFIG_FILE_NAME);
    }

    public void save() {
        NbtCompound nbt = new NbtCompound();
        nbt.putBoolean("AutoExportServer", autoExportServer);
        nbt.putBoolean("AutoExportSingleplayer", autoExportSingleplayer);
        nbt.putBoolean("AutoStartServer", autoStartServer);
        nbt.putBoolean("AutoStartSingleplayer", autoStartSingleplayer);
        nbt.putBoolean("ExportWithTexturePack", exportWithTexturePack);
        nbt.putBoolean("SaveContainers", saveContainers);

        try {
            NbtIo.writeCompressed(nbt, getConfigPath());
        } catch (IOException e) {
            System.out.println("[WorldDownloader] Failed to save config: " + e.getMessage());
        }
    }

    public void load() {
        File configFile = getConfigPath().toFile();
        if (!configFile.exists()) {
            return;
        }

        try {
            NbtCompound nbt = NbtIo.readCompressed(configFile.toPath(), net.minecraft.nbt.NbtSizeTracker.ofUnlimitedBytes());
            if (nbt == null) {
                return;
            }

            // Migrate old AutoExport to AutoExportServer
            if (nbt.contains("AutoExport")) {
                autoExportServer = nbt.getBoolean("AutoExport").orElse(false);
            } else if (nbt.contains("AutoExportServer")) {
                autoExportServer = nbt.getBoolean("AutoExportServer").orElse(false);
            }
            if (nbt.contains("AutoExportSingleplayer")) {
                autoExportSingleplayer = nbt.getBoolean("AutoExportSingleplayer").orElse(true);
            }
            if (nbt.contains("ExportWithTexturePack")) {
                exportWithTexturePack = nbt.getBoolean("ExportWithTexturePack").orElse(true);
            }
            if (nbt.contains("SaveContainers")) {
                saveContainers = nbt.getBoolean("SaveContainers").orElse(false);
            }
            if (nbt.contains("AutoStartServer")) {
                autoStartServer = nbt.getBoolean("AutoStartServer").orElse(false);
            }
            if (nbt.contains("AutoStartSingleplayer")) {
                autoStartSingleplayer = nbt.getBoolean("AutoStartSingleplayer").orElse(false);
            }
        } catch (IOException e) {
            System.out.println("[WorldDownloader] Failed to load config: " + e.getMessage());
        }
    }

    public void resetToDefaults() {
        autoExportServer = false;
        autoExportSingleplayer = true;
        autoStartServer = false;
        autoStartSingleplayer = false;
        exportWithTexturePack = true;
        saveContainers = false;
        save();
    }
}
