package net.kuudraloremaster.worlddownloader.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtOps;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.WorldChunk;
import net.kuudraloremaster.worlddownloader.config.ModConfig;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class ContainerTracker {

    // syncId -> ContainerData
    private static final Map<Integer, ContainerData> openContainers = new ConcurrentHashMap<>();
    // BlockPos -> saved NBT
    private static final Map<BlockPos, NbtCompound> savedContainerData = new ConcurrentHashMap<>();

    public static void onContainerOpened(int syncId, ScreenHandlerType<?> type) {
        System.out.println(" Container opened at syncId=" + syncId);
        openContainers.put(syncId, new ContainerData(syncId));
    }

    public static void setBlockPos(int syncId, BlockPos pos) {
        ContainerData data = openContainers.get(syncId);
        if (data != null) {
            data.blockPos = pos;
            System.out.println("[WD] Set blockPos for syncId=" + syncId + " to " + pos);
        }
    }

    public static void onContainerClosed(int syncId, ClientWorld world) {
        // Prüfen ob Container-Speichern aktiviert ist
        if (!ModConfig.getInstance().isSaveContainers()) {
            openContainers.remove(syncId);
            System.out.println(" Container closed - not saving (SaveContainers disabled)");
            return;
        }

        ContainerData data = openContainers.remove(syncId);
        if (data == null) return;

        if (world == null || data.blockPos == null) {
            System.out.println(" Container closed at syncId=" + syncId + " (no world/pos)");
            return;
        }

        System.out.println(" Container closed at " + data.blockPos);
        try {
            var blockState = world.getBlockState(data.blockPos);
            Block block = blockState.getBlock();

            if (block instanceof ChestBlock || block instanceof net.minecraft.block.TrappedChestBlock) {
                handleDoubleChest(world, data, blockState);
            } else {
                handleRegularContainer(data);
            }
        } catch (Exception e) {
            System.out.println(" Failed to handle double chest, falling back to regular container: " + e.getMessage());
            handleRegularContainer(data);
        }
    }

    private static void handleDoubleChest(ClientWorld world, ContainerData data,
                                           net.minecraft.block.BlockState blockState) {
        try {
            ChestType chestType = blockState.get(ChestBlock.CHEST_TYPE);
            Direction facing = blockState.get(ChestBlock.FACING);
            Direction adjacentDirection = (chestType == ChestType.LEFT)
                    ? facing.rotateYClockwise()
                    : facing.rotateYCounterclockwise();

            if (chestType == ChestType.SINGLE) {
                handleRegularContainer(data);
                return;
            }

            // RIGHT-Block bekommt immer Slots 0–26, LEFT-Block bekommt Slots 27–53
            BlockPos rightPos, leftPos;
            if (chestType == ChestType.RIGHT) {
                rightPos = data.blockPos;
                leftPos = data.blockPos.offset(adjacentDirection);
            } else {
                leftPos = data.blockPos;
                rightPos = data.blockPos.offset(adjacentDirection);
            }

            List<ItemStack> rightItems = data.getItemsRange(0, 27);
            List<ItemStack> leftItems = data.getItemsRange(27, 54);

            NbtCompound rightNbt = buildChestNbt(rightPos, rightItems, ChestType.RIGHT, facing);
            NbtCompound leftNbt = buildChestNbt(leftPos, leftItems, ChestType.LEFT, facing);

            savedContainerData.put(rightPos, rightNbt);
            savedContainerData.put(leftPos, leftNbt);

            long rightNonEmpty = rightItems.stream().filter(s -> !s.isEmpty()).count();
            long leftNonEmpty = leftItems.stream().filter(s -> !s.isEmpty()).count();
            System.out.println(" Saved double chest:");
            System.out.println("    - Right chest at " + rightPos + " with " + rightNonEmpty + " filled slots");
            System.out.println("    - Left chest at " + leftPos + " with " + leftNonEmpty + " filled slots");

            resaveChunkForPos(rightPos);
            resaveChunkForPos(leftPos);
        } catch (Exception e) {
            System.out.println(" Failed to handle double chest, falling back to regular container: " + e.getMessage());
            handleRegularContainer(data);
        }
    }

    private static void handleRegularContainer(ContainerData data) {
        if (data.blockPos == null) return;
        NbtCompound nbt = data.toNbt();
        if (nbt != null) {
            savedContainerData.put(data.blockPos, nbt);
            long nonEmpty = data.getAllItems().stream().filter(s -> !s.isEmpty()).count();
            System.out.println(" Saved regular container at " + data.blockPos + " with " + nonEmpty + " filled slots");
            resaveChunkForPos(data.blockPos);
        }
    }

    /**
     * Serialisiert den Chunk neu und aktualisiert den ChunkListener-Cache.
     * Nötig weil der Cache beim ersten Chunk-Load befüllt wurde — vor dem Container-Open.
     * ClientChunkSerializer.serialize() ruft enhanceBlockEntityWithContainerData() auf,
     * sodass der neue Cache-Eintrag die Kisten-Items enthält.
     */
    private static void resaveChunkForPos(BlockPos pos) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null) return;
        ChunkPos chunkPos = new ChunkPos(pos);
        WorldChunk chunk = mc.world.getChunk(chunkPos.x, chunkPos.z);
        NbtCompound nbt = ClientChunkSerializer.serialize(mc.world, chunk);
        ChunkListener.addChunkNbt(chunkPos, nbt);
        System.out.println("[WD] Re-serialized chunk " + chunkPos + " after container close");
    }

    private static NbtCompound buildChestNbt(BlockPos pos, List<ItemStack> items,
                                              ChestType chestType, Direction facing) {
        NbtCompound nbt = new NbtCompound();
        var mc = MinecraftClient.getInstance();
        var blockEntity = mc.world != null ? mc.world.getBlockEntity(pos) : null;
        String id = blockEntity != null
                ? net.minecraft.registry.Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()).toString()
                : "minecraft:chest";
        nbt.putString("id", id);
        nbt.putInt("x", pos.getX());
        nbt.putInt("y", pos.getY());
        nbt.putInt("z", pos.getZ());
        nbt.putBoolean("keepPacked", false);

        var lookup = mc.world != null ? mc.world.getRegistryManager() : null;

        NbtList itemsList = new NbtList();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty() && lookup != null) {
                final byte slotByte = (byte) i;
                var result = ItemStack.CODEC.encodeStart(lookup.getOps(NbtOps.INSTANCE), stack).result();
                result.ifPresent(el -> {
                    NbtCompound itemNbt = (NbtCompound) el;
                    itemNbt.putByte("Slot", slotByte);
                    itemsList.add(itemNbt);
                });
            }
        }
        nbt.put("Items", itemsList);
        return nbt;
    }

    public static void onInventoryUpdate(int syncId, List<ItemStack> stacks) {
        ContainerData data = openContainers.get(syncId);
        if (data == null) return;
        data.setInventory(stacks);
        if (data.blockPos != null) {
            NbtCompound nbt = data.toNbt();
            if (nbt != null) savedContainerData.put(data.blockPos, nbt);
        }
    }

    public static void onSlotUpdate(int syncId, int slot, ItemStack stack) {
        ContainerData data = openContainers.get(syncId);
        if (data == null) return;
        data.updateSlot(slot, stack);
    }

    public static boolean hasContainerData(BlockPos pos) {
        return savedContainerData.containsKey(pos);
    }

    public static NbtCompound getContainerData(BlockPos pos) {
        return savedContainerData.get(pos);
    }

    public static NbtCompound enhanceBlockEntityWithContainerData(BlockPos pos, NbtCompound original) {
        NbtCompound saved = savedContainerData.get(pos);
        if (saved == null) return original;

        NbtCompound enhanced = original.copy();
        if (saved.contains("Items")) {
            enhanced.put("Items", saved.get("Items"));
        }
        System.out.println(" Enhanced block entity at " + pos);
        return enhanced;
    }

    public static int getTotalSavedContainers() {
        return savedContainerData.size();
    }

    public static void clear() {
        openContainers.clear();
        savedContainerData.clear();
        System.out.println(" Cleared all container data");
    }

    // -------------------------------------------------------------------------

    public static class ContainerData {
        private final int syncId;
        public BlockPos blockPos;
        private final Map<Integer, ItemStack> containerSlots = new ConcurrentHashMap<>();

        public ContainerData(int syncId) {
            this.syncId = syncId;
        }

        public void setInventory(List<ItemStack> stacks) {
            for (int i = 0; i < stacks.size(); i++) {
                containerSlots.put(i, stacks.get(i).copy());
            }
        }

        public void updateSlot(int slot, ItemStack stack) {
            containerSlots.put(slot, stack.copy());
        }

        public int getSlotCount() {
            return containerSlots.isEmpty() ? 0 : containerSlots.keySet().stream().max(Integer::compare).orElse(0) + 1;
        }

        public List<ItemStack> getAllItems() {
            int max = getSlotCount();
            return java.util.stream.IntStream.range(0, max)
                    .mapToObj(i -> containerSlots.getOrDefault(i, ItemStack.EMPTY))
                    .toList();
        }

        public List<ItemStack> getItemsRange(int from, int to) {
            return java.util.stream.IntStream.range(from, to)
                    .mapToObj(i -> containerSlots.getOrDefault(i, ItemStack.EMPTY))
                    .toList();
        }

        public NbtCompound toNbt() {
            if (blockPos == null) return null;
            var mc = MinecraftClient.getInstance();
            if (mc.world == null) return null;
            var lookup = mc.world.getRegistryManager();

            var blockEntity = mc.world.getBlockEntity(blockPos);
            String id = blockEntity != null
                    ? net.minecraft.registry.Registries.BLOCK_ENTITY_TYPE.getId(blockEntity.getType()).toString()
                    : "minecraft:chest";

            NbtCompound nbt = new NbtCompound();
            nbt.putString("id", id);
            nbt.putInt("x", blockPos.getX());
            nbt.putInt("y", blockPos.getY());
            nbt.putInt("z", blockPos.getZ());
            nbt.putBoolean("keepPacked", false);

            NbtList itemsList = new NbtList();
            containerSlots.forEach((slot, stack) -> {
                if (!stack.isEmpty()) {
                    var result = ItemStack.CODEC.encodeStart(lookup.getOps(NbtOps.INSTANCE), stack).result();
                    result.ifPresent(el -> {
                        NbtCompound itemNbt = (NbtCompound) el;
                        itemNbt.putByte("Slot", (byte) (int) slot);
                        itemsList.add(itemNbt);
                    });
                }
            });
            nbt.put("Items", itemsList);
            return nbt;
        }
    }
}
