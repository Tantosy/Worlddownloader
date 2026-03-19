package net.kuudraloremaster.worlddownloader.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.storage.RegionFile;
import net.minecraft.world.storage.StorageKey;

import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

@Environment(EnvType.CLIENT)
public class ChunkListener {

    private record ChunkEntry(ChunkPos pos, NbtCompound nbt) {}

    private static final ConcurrentLinkedQueue<ChunkEntry> saveQueue = new ConcurrentLinkedQueue<>();
    private static final Map<Long, RegionFile> openRegionFiles = new HashMap<>();
    private static final AtomicInteger chunkCount = new AtomicInteger(0);
    private static volatile int dataVersion = 4671;
    private static volatile boolean running = false;
    private static Thread saveThread;
    private static Path regionDir;
    private static final Object startLock = new Object();

    public static void addChunkNbt(ChunkPos chunkPos, NbtCompound chunkNbt) {
        var v = chunkNbt.getInt("DataVersion");
        if (v.isPresent() && v.get() != 0) {
            dataVersion = v.get();
        }

        saveQueue.add(new ChunkEntry(chunkPos, chunkNbt));
        chunkCount.incrementAndGet();
        System.out.println("[WorldDownloader] Queued chunk: " + chunkPos.x + "," + chunkPos.z);

        if (!running) {
            synchronized (startLock) {
                if (!running) {
                    startSaveThread();
                }
            }
        }
    }

    private static void startSaveThread() {
        regionDir = Path.of("downloaded_world", "region");
        try {
            Files.createDirectories(regionDir);
        } catch (IOException e) {
            System.out.println("[WD] Failed to create region directory: " + e.getMessage());
        }

        running = true;
        saveThread = new Thread(ChunkListener::saveLoop, "WD-ChunkSaver");
        saveThread.setDaemon(true);
        saveThread.start();
        System.out.println("[WorldDownloader] Save thread started");
    }

    private static void saveLoop() {
        while (running || !saveQueue.isEmpty()) {
            ChunkEntry entry = saveQueue.poll();
            if (entry == null) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    break;
                }
                continue;
            }
            writeChunk(entry.pos, entry.nbt);
        }
        closeAllRegionFiles();
    }

    private static void writeChunk(ChunkPos pos, NbtCompound nbt) {
        try {
            int rx = Math.floorDiv(pos.x, 32);
            int rz = Math.floorDiv(pos.z, 32);
            long regionKey = ((long) rx << 32) | (rz & 0xFFFFFFFFL);

            RegionFile regionFile = openRegionFiles.get(regionKey);
            if (regionFile == null) {
                Path path = regionDir.resolve(String.format("r.%d.%d.mca", rx, rz));
                StorageKey storageKey = new StorageKey("downloaded_world", World.OVERWORLD, "chunk");
                regionFile = new RegionFile(storageKey, path, regionDir, true);
                openRegionFiles.put(regionKey, regionFile);
            }

            try (DataOutputStream out = regionFile.getChunkOutputStream(pos)) {
                NbtIo.write(nbt, out);
            }
        } catch (Exception e) {
            System.out.println("[WD] Failed to save chunk " + pos.x + "," + pos.z + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void flush() {
        if (!running) return;
        while (!saveQueue.isEmpty()) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                break;
            }
        }
    }

    public static void stop() {
        running = false;
        if (saveThread != null) {
            try {
                saveThread.join(5000);
            } catch (InterruptedException e) {
                // ignored
            }
            saveThread = null;
        }
        closeAllRegionFiles();
    }

    private static void closeAllRegionFiles() {
        for (RegionFile rf : openRegionFiles.values()) {
            try {
                rf.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        openRegionFiles.clear();
    }

    public static int getChunkCount() {
        return chunkCount.get();
    }

    public static int getDataVersion() {
        return dataVersion;
    }

    public static void clear() {
        stop();
        saveQueue.clear();
        chunkCount.set(0);
        dataVersion = 4671;
    }
}
