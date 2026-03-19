package net.kuudraloremaster.worlddownloader.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

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

    public static void onContainerClosed(int syncId, ClientWorld world) {
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

            if (block instanceof ChestBlock) {
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

            BlockPos leftChestPos = data.blockPos;
            BlockPos rightChestPos = data.blockPos.offset(adjacentDirection);

            int totalSlots = data.getSlotCount();
            int half = totalSlots / 2;
            List<ItemStack> leftChestItems = data.getItemsRange(0, half);
            List<ItemStack> rightChestItems = data.getItemsRange(half, totalSlots);

            NbtCompound leftChestNbt = buildChestNbt(leftChestPos, leftChestItems, chestType, facing);
            NbtCompound rightChestNbt = buildChestNbt(rightChestPos, rightChestItems,
                    chestType == ChestType.LEFT ? ChestType.RIGHT : ChestType.LEFT, facing);

            savedContainerData.put(leftChestPos, leftChestNbt);
            savedContainerData.put(rightChestPos, rightChestNbt);

            long leftNonEmpty = leftChestItems.stream().filter(s -> !s.isEmpty()).count();
            long rightNonEmpty = rightChestItems.stream().filter(s -> !s.isEmpty()).count();
            System.out.println(" Saved double chest:");
            System.out.println("    - Left chest at " + leftChestPos + " with " + leftNonEmpty + " filled slots");
            System.out.println("    - Right chest at " + rightChestPos + " with " + rightNonEmpty + " filled slots");
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
        }
    }

    private static NbtCompound buildChestNbt(BlockPos pos, List<ItemStack> items,
                                              ChestType chestType, Direction facing) {
        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", "minecraft:chest");
        nbt.putInt("x", pos.getX());
        nbt.putInt("y", pos.getY());
        nbt.putInt("z", pos.getZ());
        nbt.putBoolean("keepPacked", false);

        NbtList itemsList = new NbtList();
        for (int i = 0; i < items.size(); i++) {
            ItemStack stack = items.get(i);
            if (!stack.isEmpty()) {
                NbtCompound itemNbt = new NbtCompound();
                itemNbt.putByte("Slot", (byte) i);
                itemNbt.putString("id", net.minecraft.registry.Registries.ITEM
                        .getId(stack.getItem()).toString());
                itemNbt.putByte("Count", (byte) stack.getCount());
                itemsList.add(itemNbt);
            }
        }
        nbt.put("Items", itemsList);
        return nbt;
    }

    public static void onInventoryUpdate(int syncId, List<ItemStack> stacks) {
        ContainerData data = openContainers.get(syncId);
        if (data == null) return;
        data.setInventory(stacks);
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
            NbtCompound nbt = new NbtCompound();
            nbt.putString("id", "minecraft:chest");
            nbt.putInt("x", blockPos.getX());
            nbt.putInt("y", blockPos.getY());
            nbt.putInt("z", blockPos.getZ());
            nbt.putBoolean("keepPacked", false);

            NbtList itemsList = new NbtList();
            containerSlots.forEach((slot, stack) -> {
                if (!stack.isEmpty()) {
                    NbtCompound itemNbt = new NbtCompound();
                    itemNbt.putByte("Slot", (byte) (int) slot);
                    itemNbt.putString("id", net.minecraft.registry.Registries.ITEM
                            .getId(stack.getItem()).toString());
                    itemNbt.putByte("Count", (byte) stack.getCount());
                    itemsList.add(itemNbt);
                }
            });
            nbt.put("Items", itemsList);
            return nbt;
        }
    }
}
