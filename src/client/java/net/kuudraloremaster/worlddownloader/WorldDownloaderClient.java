package net.kuudraloremaster.worlddownloader;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.kuudraloremaster.worlddownloader.config.ModConfig;
import net.kuudraloremaster.worlddownloader.util.ChunkListener;
import net.kuudraloremaster.worlddownloader.util.ContainerTracker;
import net.kuudraloremaster.worlddownloader.util.EntityTracker;
import net.kuudraloremaster.worlddownloader.util.Exporter;
import net.kuudraloremaster.worlddownloader.util.WorldExporter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.BlockPos;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class WorldDownloaderClient implements ClientModInitializer {

    private static KeyBinding exportKey;
    private static KeyBinding clearKey;
    private static KeyBinding recordKey;

    /**
     * Pfad zum Server-Resource-Pack (wird gesetzt wenn wir einem Server mit Resource-Pack beitreten)
     */
    public static java.nio.file.Path resourcePackLocation;

    /**
     * Gibt an, ob aktuell aufgezeichnet wird
     */
    private static boolean isRecording = false;

    public static boolean isRecording() {
        return isRecording;
    }

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = new KeyBinding.Category(net.minecraft.util.Identifier.of("worlddownloader", "category"));

        recordKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.worlddownloader.record",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_I,
                category
        ));

        exportKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.worlddownloader.export",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                category
        ));

        clearKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.worlddownloader.clear",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                category
        ));

        // Join Event - Auto-Start Recording wenn aktiviert
        ClientPlayConnectionEvents.JOIN.register((handler, client, sender) -> {
            ModConfig config = ModConfig.getInstance();
            MinecraftClient mc = MinecraftClient.getInstance();
            boolean isSingleplayer = mc.getCurrentServerEntry() == null;

            if (isSingleplayer && config.isAutoStartSingleplayer()) {
                autoStartRecording(mc, "Singleplayer");
            } else if (!isSingleplayer && config.isAutoStartServer()) {
                autoStartRecording(mc, handler.getServerInfo().name);
            }
        });

        // Disconnect Event - Auto-Export wenn aktiviert
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            // Check if this was a singleplayer world (no server entry)
            if (client.getCurrentServerEntry() == null && isRecording) {
                handleSingleplayerDisconnect(client);
            } else if (isRecording) {
                handleServerDisconnect(client);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (recordKey.wasPressed()) {
                toggleRecording(client);
            }

            while (exportKey.wasPressed()) {
                performManualExport(client);
            }

            while (clearKey.wasPressed()) {
                int chunkCount = ChunkListener.getChunkCount();
                int entityCount = EntityTracker.getTotalTrackedEntities();
                int containerCount = ContainerTracker.getTotalSavedContainers();
                ChunkListener.clear();
                EntityTracker.captureAllEntities(client);
                ContainerTracker.clear();
                System.out.println(" Cleared cached data:");
                System.out.println("    - Chunks: " + chunkCount);
                System.out.println("    - Entities: " + entityCount);
                System.out.println("    - Containers: " + containerCount);
            }
        });
    }

    private void toggleRecording(MinecraftClient client) {
        if (isRecording) {
            isRecording = false;
            System.out.println("[WorldDownloader] Recording stopped.");
        } else {
            isRecording = true;
            String serverName = client.getCurrentServerEntry() != null
                ? client.getCurrentServerEntry().name
                : (client.world != null ? client.world.getRegistryKey().getValue().toString() : "Singleplayer");
            System.out.println("[WorldDownloader] Recording started: " + serverName);

            // Capture already loaded chunks from client memory
            if (client.world != null) {
                captureAlreadyLoadedChunks(client);
            }
        }
    }

    /**
     * Startet automatisch das Recording beim Joinen eines Servers/einer Singleplayer-Welt.
     */
    private void autoStartRecording(MinecraftClient client, String serverName) {
        isRecording = true;
        System.out.println("[WorldDownloader] Auto-start recording: " + serverName);

        // Capture already loaded chunks from client memory
        if (client.world != null) {
            captureAlreadyLoadedChunks(client);
        }
    }

    /**
     * Iteriert alle bereits im ClientChunkManager geladenen Chunks und fügt sie dem Cache hinzu.
     * Wird aufgerufen wenn recording gestartet wird, um Chunks zu erfassen die schon im Memory sind.
     */
    private void captureAlreadyLoadedChunks(MinecraftClient client) {
        if (client.world == null || client.player == null) return;

        var chunkManager = client.world.getChunkManager();
        if (chunkManager == null) return;

        // Spieler-Position als Basis für die Chunk-Iteration nehmen
        BlockPos playerPos = client.player.getBlockPos();
        int playerChunkX = playerPos.getX() >> 4;
        int playerChunkZ = playerPos.getZ() >> 4;

        int loadedChunkCount = 0;

        // Iteriere über den Render-Radius um den Spieler herum
        int renderDistance = client.options.getViewDistance().getValue();
        int maxRadius = Math.min(48, renderDistance + 16);

        for (int x = playerChunkX - maxRadius; x <= playerChunkX + maxRadius; x++) {
            for (int z = playerChunkZ - maxRadius; z <= playerChunkZ + maxRadius; z++) {
                var chunk = chunkManager.getChunk(x, z, net.minecraft.world.chunk.ChunkStatus.FULL, false);
                if (chunk != null) {
                    try {
                        var nbt = net.kuudraloremaster.worlddownloader.util.ClientChunkSerializer.serialize(client.world, chunk);
                        var pos = new net.minecraft.util.math.ChunkPos(x, z);
                        ChunkListener.addChunkNbt(pos, nbt);
                        loadedChunkCount++;
                    } catch (Exception e) {
                        System.out.println("[WD] Failed to capture already loaded chunk " + x + "," + z + ": " + e.getMessage());
                    }
                }
            }
        }

        if (loadedChunkCount > 0) {
            System.out.println("[WorldDownloader] Captured " + loadedChunkCount + " already loaded chunks from client memory.");
        }
    }

    private void handleSingleplayerDisconnect(MinecraftClient client) {
        ModConfig config = ModConfig.getInstance();
        isRecording = false;

        if (!config.isAutoExportSingleplayer()) {
            System.out.println("[WorldDownloader] Recording stopped. Auto-Export Singleplayer disabled.");
            ChunkListener.clear();
            EntityTracker.clear();
            ContainerTracker.clear();
            return;
        }

        performAutoExport(client);
    }

    private void handleServerDisconnect(MinecraftClient client) {
        ModConfig config = ModConfig.getInstance();
        isRecording = false;

        if (!config.isAutoExportServer()) {
            System.out.println("[WorldDownloader] Recording stopped. Press J to export manually.");
            return;
        }

        performAutoExport(client);
    }

    private void performManualExport(MinecraftClient client) {
        int chunkCount = ChunkListener.getChunkCount();
        int entityCount = EntityTracker.getTotalTrackedEntities();
        int containerCount = ContainerTracker.getTotalSavedContainers();

        if (chunkCount == 0) {
            System.out.println(" No chunks to export! Walk around to load chunks first.");
            return;
        }

        Path worldFolder = ChunkListener.getWorldFolder();
        if (worldFolder == null) {
            System.out.println(" Export folder nicht gefunden — wurden Chunks aufgezeichnet?");
            return;
        }

        System.out.println(" Starting export of " + chunkCount + " chunks...");

        // Entities auf dem Game-Thread capturen (braucht Zugriff auf client)
        EntityTracker.captureAllEntities(client);
        System.out.println(" Capturing entities...");
        System.out.println(" Found " + entityCount + " entities and " + containerCount + " containers");

        // level.dat / playerdata / session.lock auf dem Game-Thread schreiben (klein + schnell)
        WorldExporter.createLoadableWorld(worldFolder.toFile());

        // MCA-Writes in Hintergrund-Thread — blockiert nicht den Game-Thread
        final Path exportFolder = worldFolder;
        final int finalChunkCount = chunkCount;
        final int finalEntityCount = entityCount;
        final int finalContainerCount = containerCount;
        Thread exportThread = new Thread(() -> {
            try {
                Exporter.exportChunks(exportFolder.toFile());
                Exporter.exportEntities(exportFolder.toFile());
                System.out.println(" World exported! Copy '" + exportFolder + "/' to '.minecraft/saves/'");
                System.out.println(" Export Summary:");
                System.out.println("    - Chunks: " + finalChunkCount);
                System.out.println("    - Entities: " + finalEntityCount);
                System.out.println("    - Containers: " + finalContainerCount);
            } catch (Exception e) {
                System.out.println(" Failed to export world: " + e.getMessage());
                e.printStackTrace();
            } finally {
                // State zurücksetzen — nächster Export bekommt neuen Ordner
                ChunkListener.clear();
                EntityTracker.clear();
                ContainerTracker.clear();
            }
        }, "WD-ExportThread");
        exportThread.setDaemon(true);
        exportThread.start();
    }

    /**
     * Führt einen automatischen Export nach dem Verlassen eines Servers/Singleplayer durch.
     */
    private static void performAutoExport(MinecraftClient client) {
        int chunkCount = ChunkListener.getChunkCount();
        int entityCount = EntityTracker.getTotalTrackedEntities();
        int containerCount = ContainerTracker.getTotalSavedContainers();

        if (chunkCount == 0) {
            System.out.println("[WorldDownloader] No chunks to export!");
            return;
        }

        Path worldFolder = ChunkListener.getWorldFolder();
        if (worldFolder == null) {
            System.out.println("[WorldDownloader] Export folder not found!");
            return;
        }

        System.out.println("[WorldDownloader] Starting auto-export of " + chunkCount + " chunks...");

        // Entities auf dem Game-Thread capturen
        EntityTracker.captureAllEntities(client);
        System.out.println(" Capturing entities...");
        System.out.println(" Found " + entityCount + " entities and " + containerCount + " containers");

        // level.dat / playerdata / session.lock auf dem Game-Thread schreiben
        WorldExporter.createLoadableWorld(worldFolder.toFile());

        // Texture Pack exportieren wenn aktiviert
        ModConfig config = ModConfig.getInstance();
        if (config.isExportWithTexturePack() && resourcePackLocation != null) {
            exportTexturePack(worldFolder);
        }

        // MCA-Writes in Hintergrund-Thread
        final Path exportFolder = worldFolder;
        final int finalChunkCount = chunkCount;
        final int finalEntityCount = entityCount;
        final int finalContainerCount = containerCount;
        Thread exportThread = new Thread(() -> {
            try {
                Exporter.exportChunks(exportFolder.toFile());
                Exporter.exportEntities(exportFolder.toFile());
                System.out.println("[WorldDownloader] World exported! Copy '" + exportFolder + "/' to '.minecraft/saves/'");
                System.out.println(" Export Summary:");
                System.out.println("    - Chunks: " + finalChunkCount);
                System.out.println("    - Entities: " + finalEntityCount);
                System.out.println("    - Containers: " + finalContainerCount);
            } catch (Exception e) {
                System.out.println(" Failed to export world: " + e.getMessage());
                e.printStackTrace();
            } finally {
                ChunkListener.clear();
                EntityTracker.clear();
                ContainerTracker.clear();
            }
        }, "WD-AutoExportThread");
        exportThread.setDaemon(true);
        exportThread.start();
    }

    /**
     * Exportiert das Server-Resource-Pack in den Weltordner.
     */
    private static void exportTexturePack(Path worldFolder) {
        if (resourcePackLocation == null || !resourcePackLocation.toFile().exists()) {
            System.out.println("[WorldDownloader] No texture pack found to export.");
            return;
        }

        try {
            Path resourcePackDir = worldFolder.resolve("resourcepack");
            java.nio.file.Files.createDirectories(resourcePackDir);

            File sourceFile = resourcePackLocation.toFile();
            File targetFile = resourcePackDir.resolve("server.zip").toFile();

            // Datei kopieren
            java.nio.file.Files.copy(resourcePackLocation, targetFile.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);

            // pack.mcmeta erstellen wenn nicht vorhanden
            Path mcmetaPath = resourcePackDir.resolve("pack.mcmeta");
            if (!mcmetaPath.toFile().exists()) {
                String mcmetaContent = """
                {
                  "pack": {
                    "pack_format": 18,
                    "description": "Server Resource Pack"
                  }
                }
                """;
                java.nio.file.Files.writeString(mcmetaPath, mcmetaContent);
            }

            System.out.println("[WorldDownloader] Texture pack exported to: " + resourcePackDir);
        } catch (IOException e) {
            System.out.println("[WorldDownloader] Failed to export texture pack: " + e.getMessage());
        }
    }
}
