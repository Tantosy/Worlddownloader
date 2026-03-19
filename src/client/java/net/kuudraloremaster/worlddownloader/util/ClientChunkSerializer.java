package net.kuudraloremaster.worlddownloader.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public class ClientChunkSerializer {

    public static NbtCompound serialize(ClientWorld world, WorldChunk chunk) {
        NbtCompound chunkNbt = new NbtCompound();
        ChunkPos chunkPos = chunk.getPos();

        chunkNbt.putInt("DataVersion", 4671);
        chunkNbt.putInt("xPos", chunkPos.x);
        chunkNbt.putInt("zPos", chunkPos.z);
        chunkNbt.putInt("yPos", chunk.getBottomSectionCoord());
        chunkNbt.putString("Status", "minecraft:full");
        chunkNbt.putLong("LastUpdate", System.currentTimeMillis());
        chunkNbt.putLong("InhabitedTime", 0L);

        // Sections
        NbtList sectionsNbt = new NbtList();
        ChunkSection[] sections = chunk.getSectionArray();
        int minY = chunk.getBottomSectionCoord();

        for (int i = 0; i < sections.length; i++) {
            ChunkSection section = sections[i];
            NbtCompound sectionNbt = new NbtCompound();
            sectionNbt.putByte("Y", (byte)(minY + i));

            if (section != null && !section.isEmpty()) {
                sectionNbt.put("block_states", serializeBlockStates(section));
                sectionNbt.put("biomes", serializeBiomes(section));
            }
            sectionsNbt.add(sectionNbt);
        }
        chunkNbt.put("sections", sectionsNbt);

        // Heightmaps
        NbtCompound heightmapsNbt = new NbtCompound();
        for (Map.Entry<Heightmap.Type, Heightmap> entry : chunk.getHeightmaps()) {
            if (entry.getKey().isStoredServerSide()) {
                heightmapsNbt.putLongArray(entry.getKey().asString(), entry.getValue().asLongArray());
            }
        }
        chunkNbt.put("Heightmaps", heightmapsNbt);

        // Block entities
        NbtList blockEntitiesNbt = new NbtList();
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            BlockPos pos = entry.getKey();
            BlockEntity be = entry.getValue();
            try {
                NbtCompound beNbt = be.createNbt(world.getRegistryManager());
                beNbt = ContainerTracker.enhanceBlockEntityWithContainerData(pos, beNbt);
                beNbt.putBoolean("keepPacked", false);
                blockEntitiesNbt.add(beNbt);
            } catch (Exception e) {
                System.out.println("[WD] Failed to serialize block entity at " + pos + ": " + e.getMessage());
            }
        }
        chunkNbt.put("block_entities", blockEntitiesNbt);

        // Empty structures
        NbtCompound structuresNbt = new NbtCompound();
        structuresNbt.put("starts", new NbtCompound());
        structuresNbt.put("References", new NbtCompound());
        chunkNbt.put("structures", structuresNbt);

        return chunkNbt;
    }

    private static NbtCompound serializeBlockStates(ChunkSection section) {
        NbtCompound blockStatesNbt = new NbtCompound();
        var container = section.getBlockStateContainer();

        List<BlockState> palette = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        for (int y = 0; y < 16; y++) {
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    BlockState state = container.get(x, y, z);
                    int idx = palette.indexOf(state);
                    if (idx == -1) { idx = palette.size(); palette.add(state); }
                    indices.add(idx);
                }
            }
        }

        NbtList paletteNbt = new NbtList();
        for (BlockState state : palette) {
            NbtCompound stateNbt = new NbtCompound();
            stateNbt.putString("Name", Registries.BLOCK.getId(state.getBlock()).toString());
            if (!state.getEntries().isEmpty()) {
                NbtCompound props = new NbtCompound();
                state.getEntries().forEach((prop, val) ->
                    props.putString(prop.getName(), val.toString()));
                stateNbt.put("Properties", props);
            }
            paletteNbt.add(stateNbt);
        }
        blockStatesNbt.put("palette", paletteNbt);

        if (palette.size() > 1) {
            blockStatesNbt.putLongArray("data", pack(indices, palette.size()));
        }
        return blockStatesNbt;
    }

    private static NbtCompound serializeBiomes(ChunkSection section) {
        NbtCompound biomesNbt = new NbtCompound();
        var biomeContainer = section.getBiomeContainer();

        List<String> biomeIds = new ArrayList<>();
        List<Integer> indices = new ArrayList<>();

        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 4; z++) {
                for (int x = 0; x < 4; x++) {
                    var entry = biomeContainer.get(x, y, z);
                    String id = entry.getKey()
                        .map(k -> k.getValue().toString())
                        .orElse("minecraft:plains");
                    int idx = biomeIds.indexOf(id);
                    if (idx == -1) { idx = biomeIds.size(); biomeIds.add(id); }
                    indices.add(idx);
                }
            }
        }

        NbtList palNbt = new NbtList();
        for (String id : biomeIds) palNbt.add(NbtString.of(id));
        biomesNbt.put("palette", palNbt);
        if (biomeIds.size() > 1) biomesNbt.putLongArray("data", pack(indices, biomeIds.size(), 1));
        return biomesNbt;
    }

    private static long[] pack(List<Integer> values, int paletteSize) {
        return pack(values, paletteSize, 4);
    }

    private static long[] pack(List<Integer> values, int paletteSize, int minBits) {
        int bits = Math.max(minBits, 32 - Integer.numberOfLeadingZeros(paletteSize - 1));
        int perLong = 64 / bits;
        long[] data = new long[(values.size() + perLong - 1) / perLong];
        for (int i = 0; i < values.size(); i++) {
            data[i / perLong] |= ((long) values.get(i)) << ((i % perLong) * bits);
        }
        return data;
    }
}
