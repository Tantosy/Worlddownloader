package net.kuudraloremaster.worlddownloader.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class ChunkListener {

    private static final Map<ChunkPos, NbtCompound> downloadedChunkNbt = new ConcurrentHashMap<>();

    public static void addChunkNbt(ChunkPos chunkPos, NbtCompound chunkNbt) {
        downloadedChunkNbt.put(chunkPos, chunkNbt);
        System.out.println("[WorldDownloader] Captured raw NBT chunk: " + chunkPos.x + "," + chunkPos.z);
    }

    public static Map<ChunkPos, NbtCompound> getAll() {
        return downloadedChunkNbt;
    }

    public static int getDataVersion() {
        for (NbtCompound chunk : downloadedChunkNbt.values()) {
            var v = chunk.getInt("DataVersion");
            if (v.isPresent() && v.get() != 0) return v.get();
        }
        return 4671; // fallback (1.21.11)
    }

    public static void clear() {
        downloadedChunkNbt.clear();
    }
}
