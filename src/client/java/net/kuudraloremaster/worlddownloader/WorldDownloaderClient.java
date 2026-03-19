package net.kuudraloremaster.worlddownloader;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.kuudraloremaster.worlddownloader.util.ChunkListener;
import net.kuudraloremaster.worlddownloader.util.ContainerTracker;
import net.kuudraloremaster.worlddownloader.util.EntityTracker;
import net.kuudraloremaster.worlddownloader.util.Exporter;
import net.kuudraloremaster.worlddownloader.util.WorldExporter;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

@Environment(EnvType.CLIENT)
public class WorldDownloaderClient implements ClientModInitializer {

    private static KeyBinding exportKey;
    private static KeyBinding clearKey;

    @Override
    public void onInitializeClient() {
        KeyBinding.Category category = new KeyBinding.Category(net.minecraft.util.Identifier.of("worlddownloader", "category"));

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

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (exportKey.wasPressed()) {
                int chunkCount = ChunkListener.getAll().size();
                int entityCount = EntityTracker.getTotalTrackedEntities();
                int containerCount = ContainerTracker.getTotalSavedContainers();

                if (chunkCount == 0) {
                    System.out.println(" No chunks to export! Walk around to load chunks first.");
                    return;
                }

                System.out.println(" Starting export of " + chunkCount + " chunks...");
                System.out.println(" Capturing entities...");
                EntityTracker.captureAllEntities(client);
                System.out.println(" Found " + entityCount + " entities and " + containerCount + " containers");

                try {
                    Path worldFolder = Path.of("downloaded_world");
                    Files.createDirectories(worldFolder);

                    WorldExporter.createLoadableWorld(worldFolder.toFile());
                    Exporter.exportChunks(worldFolder.toFile());
                    Exporter.exportEntities(worldFolder.toFile());

                    System.out.println(" World exported! Copy 'downloaded_world/' to '.minecraft/saves/'");
                    System.out.println(" Export Summary:");
                    System.out.println("    - Chunks: " + chunkCount);
                    System.out.println("    - Entities: " + entityCount);
                    System.out.println("    - Containers: " + containerCount);
                } catch (Exception e) {
                    System.out.println(" Failed to export world: " + e.getMessage());
                    e.printStackTrace();
                }
            }

            while (clearKey.wasPressed()) {
                int chunkCount = ChunkListener.getAll().size();
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
}
