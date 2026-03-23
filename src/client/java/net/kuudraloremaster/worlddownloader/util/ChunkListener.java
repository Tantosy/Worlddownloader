package net.kuudraloremaster.worlddownloader.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.ChunkPos;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.DeflaterOutputStream;

@Environment(EnvType.CLIENT)
public class ChunkListener {

    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm");

    // In-memory store — alle Chunks bis zum Export im RAM halten
    private static final ConcurrentHashMap<ChunkPos, NbtCompound> chunkNbtCache = new ConcurrentHashMap<>();
    private static final AtomicInteger chunkCount = new AtomicInteger(0);
    private static volatile int dataVersion = 4671;
    private static volatile Path worldFolder;
    private static volatile Path regionDir;
    private static final Object initLock = new Object();

    public static void addChunkNbt(ChunkPos chunkPos, NbtCompound chunkNbt) {
        var v = chunkNbt.getInt("DataVersion");
        if (v.isPresent() && v.get() != 0) {
            dataVersion = v.get();
        }

        boolean isNew = chunkNbtCache.put(chunkPos, chunkNbt) == null;
        if (isNew) {
            chunkCount.incrementAndGet();
        }
        System.out.println("[WorldDownloader] Cached chunk: " + chunkPos.x + "," + chunkPos.z
                + " (total: " + chunkCount.get() + ")");

        // Export-Ordner beim ersten Chunk anlegen
        if (worldFolder == null) {
            synchronized (initLock) {
                if (worldFolder == null) {
                    String exportName = "Export_" + LocalDateTime.now().format(TIMESTAMP);
                    worldFolder = MinecraftClient.getInstance().runDirectory.toPath()
                            .resolve("downloaded_worlds")
                            .resolve(exportName);
                    regionDir = worldFolder.resolve("region");
                    try {
                        Files.createDirectories(regionDir);
                    } catch (IOException e) {
                        System.out.println("[WD] Failed to create region directory: " + e.getMessage());
                    }
                    System.out.println("[WorldDownloader] Export folder: " + worldFolder);
                }
            }
        }
    }

    /**
     * Schreibt alle gecachten Chunks als MCA-Dateien in den Region-Ordner.
     */
    public static void flush() throws IOException {
        if (chunkNbtCache.isEmpty() || regionDir == null) return;

        // Nach Region gruppieren
        Map<Long, Map<ChunkPos, NbtCompound>> regions = new HashMap<>();
        for (Map.Entry<ChunkPos, NbtCompound> entry : chunkNbtCache.entrySet()) {
            ChunkPos pos = entry.getKey();
            int rx = Math.floorDiv(pos.x, 32);
            int rz = Math.floorDiv(pos.z, 32);
            long key = ((long) rx << 32) | (rz & 0xFFFFFFFFL);
            regions.computeIfAbsent(key, k -> new HashMap<>()).put(pos, entry.getValue());
        }

        for (Map.Entry<Long, Map<ChunkPos, NbtCompound>> regionEntry : regions.entrySet()) {
            long key = regionEntry.getKey();
            int rx = (int) (key >> 32);
            int rz = (int) key;
            Path path = regionDir.resolve(String.format("r.%d.%d.mca", rx, rz));
            writeMcaFile(path, regionEntry.getValue());
        }

        System.out.println("[WorldDownloader] Flushed " + chunkCount.get() + " chunks to "
                + regions.size() + " region file(s).");
    }

    /**
     * Schreibt eine einzelne MCA-Datei (Anvil-Format) mit dem angegebenen Chunk-Map.
     * Kann auch von Exporter für entity-Regions genutzt werden.
     */
    public static void writeMcaFile(Path path, Map<ChunkPos, NbtCompound> chunks) throws IOException {
        // Alle Chunks mit Zlib komprimieren
        record CompressedChunk(int localX, int localZ, byte[] data) {}
        List<CompressedChunk> compressed = new ArrayList<>();

        for (Map.Entry<ChunkPos, NbtCompound> entry : chunks.entrySet()) {
            ChunkPos pos = entry.getKey();
            int localX = Math.floorMod(pos.x, 32);
            int localZ = Math.floorMod(pos.z, 32);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (DeflaterOutputStream deflate = new DeflaterOutputStream(baos)) {
                NbtIo.write(entry.getValue(), new DataOutputStream(deflate));
            }
            compressed.add(new CompressedChunk(localX, localZ, baos.toByteArray()));
        }

        // Sektoren zuweisen — Sektor 0 + 1 = 8192-Byte-Header
        int[] locationTable = new int[1024]; // (offset << 8) | count
        record SectorInfo(int offset, byte[] data) {}
        Map<Integer, SectorInfo> sectorMap = new HashMap<>();

        int currentSector = 2;
        for (CompressedChunk cc : compressed) {
            // 4 Bytes Länge + 1 Byte Compression-Type + Daten
            int totalBytes = 5 + cc.data().length;
            int sectors = (totalBytes + 4095) / 4096;

            int index = cc.localZ() * 32 + cc.localX();
            locationTable[index] = (currentSector << 8) | Math.min(sectors, 255);
            sectorMap.put(index, new SectorInfo(currentSector, cc.data()));
            currentSector += sectors;
        }

        // MCA-Datei schreiben
        try (RandomAccessFile raf = new RandomAccessFile(path.toFile(), "rw")) {
            raf.setLength(0);

            // Location-Tabelle (4096 Bytes)
            for (int i = 0; i < 1024; i++) {
                raf.writeInt(locationTable[i]);
            }
            // Timestamp-Tabelle (4096 Bytes, alle 0)
            for (int i = 0; i < 1024; i++) {
                raf.writeInt(0);
            }

            // Chunk-Daten sektor-aligned schreiben
            for (Map.Entry<Integer, SectorInfo> entry : sectorMap.entrySet()) {
                SectorInfo si = entry.getValue();
                raf.seek((long) si.offset() * 4096);
                raf.writeInt(si.data().length + 1); // Länge inkl. Compression-Byte
                raf.writeByte(2);                   // Zlib
                raf.write(si.data());

                // Auf Sektorgrenze auffüllen
                int written = 5 + si.data().length;
                int pad = (4096 - (written % 4096)) % 4096;
                if (pad > 0) {
                    raf.write(new byte[pad]);
                }
            }
        }
    }

    public static int getChunkCount() {
        return chunkCount.get();
    }

    public static int getDataVersion() {
        return dataVersion;
    }

    public static Path getWorldFolder() {
        return worldFolder;
    }

    public static NbtCompound getCachedChunkNbt(ChunkPos pos) {
        return chunkNbtCache.get(pos);
    }

    public static void clear() {
        chunkNbtCache.clear();
        chunkCount.set(0);
        dataVersion = 4671;
        worldFolder = null;
        regionDir = null;
    }
}
