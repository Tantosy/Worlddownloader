package net.kuudraloremaster.worlddownloader.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.ChunkPos;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class Exporter {

    public static void exportChunks(File worldFolder) throws IOException {
        int chunkCount = ChunkListener.getChunkCount();

        if (chunkCount == 0) {
            System.out.println(" No chunks to export!");
            return;
        }

        ChunkListener.flush();

        int totalContainers = ContainerTracker.getTotalSavedContainers();
        int totalEntities = EntityTracker.getTotalTrackedEntities();

        System.out.println(" Exported " + chunkCount + " chunks with " +
                totalContainers + " containers to region files.");
    }

    public static void exportEntities(File worldFolder) throws IOException {
        Map<ChunkPos, List<NbtCompound>> allEntities = EntityTracker.getAll();
        if (allEntities.isEmpty()) return;

        Path entitiesDir = worldFolder.toPath().resolve("entities");
        Files.createDirectories(entitiesDir);

        int dataVersion = ChunkListener.getDataVersion();

        // Nach Region gruppieren
        Map<Long, Map<ChunkPos, NbtCompound>> regionChunks = new HashMap<>();
        for (Map.Entry<ChunkPos, List<NbtCompound>> entry : allEntities.entrySet()) {
            ChunkPos pos = entry.getKey();
            int rx = Math.floorDiv(pos.x, 32);
            int rz = Math.floorDiv(pos.z, 32);
            long regionKey = ((long) rx << 32) | (rz & 0xFFFFFFFFL);

            NbtCompound chunk = new NbtCompound();
            chunk.putInt("DataVersion", dataVersion);
            chunk.putIntArray("Position", new int[]{pos.x, pos.z});
            NbtList entityList = new NbtList();
            entityList.addAll(entry.getValue());
            chunk.put("Entities", entityList);

            regionChunks.computeIfAbsent(regionKey, k -> new HashMap<>()).put(pos, chunk);
        }

        for (Map.Entry<Long, Map<ChunkPos, NbtCompound>> regionEntry : regionChunks.entrySet()) {
            long regionKey = regionEntry.getKey();
            int rx = (int) (regionKey >> 32);
            int rz = (int) regionKey;
            Path regionPath = entitiesDir.resolve(String.format("r.%d.%d.mca", rx, rz));

            try {
                ChunkListener.writeMcaFile(regionPath, regionEntry.getValue());
                System.out.println(" Successfully wrote entity region: " + regionPath.getFileName());
            } catch (IOException e) {
                System.out.println(" Failed to write entity region: " + e.getMessage());
                e.printStackTrace();
            }
        }

        System.out.println(" Entities exported to entities/ folder.");
    }
}
