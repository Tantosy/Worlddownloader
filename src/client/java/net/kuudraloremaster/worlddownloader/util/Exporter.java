package net.kuudraloremaster.worlddownloader.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtList;
import net.minecraft.util.math.ChunkPos;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class Exporter {

    public static void exportChunks(File worldFolder) throws IOException {
        Map<ChunkPos, NbtCompound> allChunks = ChunkListener.getAll();

        if (allChunks.isEmpty()) {
            System.out.println(" No chunks to export!");
            return;
        }

        File regionDir = new File(worldFolder, "region");
        if (!regionDir.exists() && !regionDir.mkdirs()) {
            throw new IOException("$Failed to create region directory: " + regionDir.getAbsolutePath());
        }

        // Group chunks by region (32x32 chunks per region)
        Map<String, Map<ChunkPos, NbtCompound>> regionFiles = new HashMap<>();
        for (Map.Entry<ChunkPos, NbtCompound> entry : allChunks.entrySet()) {
            ChunkPos pos = entry.getKey();
            int regionX = Math.floorDiv(pos.x, 32);
            int regionZ = Math.floorDiv(pos.z, 32);
            String key = regionX + "," + regionZ;
            regionFiles.computeIfAbsent(key, k -> new HashMap<>()).put(pos, entry.getValue());
        }

        int totalContainers = ContainerTracker.getTotalSavedContainers();
        int totalEntities = EntityTracker.getTotalTrackedEntities();

        System.out.println(" Exported " + allChunks.size() + " chunks with " +
                totalContainers + " containers to " + regionFiles.size() + " region files.");

        for (Map.Entry<String, Map<ChunkPos, NbtCompound>> regionEntry : regionFiles.entrySet()) {
            String[] coords = regionEntry.getKey().split(",");
            int regionX = Integer.parseInt(coords[0]);
            int regionZ = Integer.parseInt(coords[1]);
            File regionFile = new File(regionDir, String.format("r.%d.%d.mca", regionX, regionZ));

            try {
                writeRegionFile(regionFile, regionEntry.getValue(), regionX, regionZ);
                System.out.println(" Successfully wrote region file: " + regionFile.getName());
            } catch (Exception e) {
                System.out.println(" Failed to write region file " + regionFile.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void exportEntities(File worldFolder) throws IOException {
        Map<ChunkPos, List<NbtCompound>> allEntities = EntityTracker.getAll();
        if (allEntities.isEmpty()) return;

        File entitiesDir = new File(worldFolder, "entities");
        entitiesDir.mkdirs();

        int dataVersion = ChunkListener.getDataVersion();

        Map<String, Map<ChunkPos, NbtCompound>> regionFiles = new HashMap<>();
        for (Map.Entry<ChunkPos, List<NbtCompound>> entry : allEntities.entrySet()) {
            ChunkPos pos = entry.getKey();
            int regionX = Math.floorDiv(pos.x, 32);
            int regionZ = Math.floorDiv(pos.z, 32);
            String key = regionX + "," + regionZ;

            NbtCompound chunk = new NbtCompound();
            chunk.putInt("DataVersion", dataVersion);
            chunk.putIntArray("Position", new int[]{pos.x, pos.z});
            NbtList entityList = new NbtList();
            entityList.addAll(entry.getValue());
            chunk.put("Entities", entityList);

            regionFiles.computeIfAbsent(key, k -> new HashMap<>()).put(pos, chunk);
        }

        for (Map.Entry<String, Map<ChunkPos, NbtCompound>> regionEntry : regionFiles.entrySet()) {
            String[] coords = regionEntry.getKey().split(",");
            int regionX = Integer.parseInt(coords[0]);
            int regionZ = Integer.parseInt(coords[1]);
            File regionFile = new File(entitiesDir, String.format("r.%d.%d.mca", regionX, regionZ));
            writeRegionFile(regionFile, regionEntry.getValue(), regionX, regionZ);
        }

        System.out.println(" Entities exported to entities/ folder.");
    }

    private static void writeRegionFile(File regionFile, Map<ChunkPos, NbtCompound> chunks,
                                         int regionX, int regionZ) throws IOException {
        // MCA format: 8192-byte header, then chunk data
        // Header = 4096 bytes location table + 4096 bytes timestamp table
        // Each chunk gets a 4-byte location entry: 3 bytes offset (in 4096-byte sectors), 1 byte sector count

        int SECTOR_SIZE = 4096;
        int HEADER_SIZE = 2 * SECTOR_SIZE; // 8192 bytes

        // First, serialize all chunks to bytes
        Map<ChunkPos, byte[]> chunkBytes = new HashMap<>();
        for (Map.Entry<ChunkPos, NbtCompound> entry : chunks.entrySet()) {
            ChunkPos pos = entry.getKey();
            NbtCompound chunkNbt = entry.getValue();

            // Serialize NBT to bytes using Zlib (compression type 2, matches vanilla)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (java.util.zip.DeflaterOutputStream deflate = new java.util.zip.DeflaterOutputStream(baos)) {
                NbtIo.write(chunkNbt, new DataOutputStream(deflate));
            }
            chunkBytes.put(pos, baos.toByteArray());
        }

        // Compute layout
        int[] locationTable = new int[1024]; // 32*32
        int[] timestampTable = new int[1024];
        int currentSector = 2; // first 2 sectors are header

        Map<ChunkPos, Integer> chunkOffsets = new HashMap<>();
        Map<ChunkPos, Integer> chunkSectors = new HashMap<>();

        for (Map.Entry<ChunkPos, byte[]> entry : chunkBytes.entrySet()) {
            ChunkPos pos = entry.getKey();
            byte[] data = entry.getValue();

            // 5 bytes header per chunk: 4-byte length + 1-byte compression type
            int totalBytes = data.length + 5;
            int sectors = (int) Math.ceil(totalBytes / (double) SECTOR_SIZE);

            int localX = Math.floorMod(pos.x, 32);
            int localZ = Math.floorMod(pos.z, 32);
            int tableIndex = localX + localZ * 32;

            chunkOffsets.put(pos, currentSector);
            chunkSectors.put(pos, sectors);
            locationTable[tableIndex] = (currentSector << 8) | (sectors & 0xFF);
            timestampTable[tableIndex] = (int)(System.currentTimeMillis() / 1000L);

            currentSector += sectors;
        }

        // Write file
        try (RandomAccessFile raf = new RandomAccessFile(regionFile, "rw")) {
            raf.setLength(0);

            // Write location table
            for (int loc : locationTable) raf.writeInt(loc);
            // Write timestamp table
            for (int ts : timestampTable) raf.writeInt(ts);

            // Write chunk data
            for (Map.Entry<ChunkPos, byte[]> entry : chunkBytes.entrySet()) {
                ChunkPos pos = entry.getKey();
                byte[] data = entry.getValue();
                int offset = chunkOffsets.get(pos);

                raf.seek((long) offset * SECTOR_SIZE);
                raf.writeInt(data.length + 1); // length includes compression byte
                raf.writeByte(2); // zlib compression
                raf.write(data);

                // Pad to sector boundary
                int written = 5 + data.length;
                int sectors = chunkSectors.get(pos);
                int padBytes = (sectors * SECTOR_SIZE) - written;
                if (padBytes > 0) {
                    raf.write(new byte[padBytes]);
                }
            }
        }
    }
}
