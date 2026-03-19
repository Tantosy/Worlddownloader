package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kuudraloremaster.worlddownloader.util.ContainerTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(value = ClientPlayNetworkHandler.class, priority = 900)
public class DebugContainerMixin {

    @Inject(method = "onOpenScreen", at = @At("HEAD"))
    private void debugOnOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        String targetInfo = "unknown";
        if (client.crosshairTarget instanceof BlockHitResult blockHit) {
            var blockPos = blockHit.getBlockPos();
            var world = client.world;
            if (world != null) {
                var block = world.getBlockState(blockPos).getBlock();
                targetInfo = block.getName().getString() + " at " + blockPos;
            }
        }
        System.out.println(" DEBUG: OpenScreen packet - syncId: " + packet.getSyncId() + " target: " + targetInfo);
    }

    @Inject(method = "onCloseScreen", at = @At("HEAD"))
    private void debugOnCloseScreen(CloseScreenS2CPacket packet, CallbackInfo ci) {
        System.out.println(" DEBUG: CloseScreen packet - syncId: " + packet.getSyncId());
    }

    @Inject(method = "onInventory", at = @At("HEAD"))
    private void debugOnInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        List<ItemStack> stacks = packet.contents();
        int totalSlots = stacks.size();
        long nonEmpty = stacks.stream().filter(s -> !s.isEmpty()).count();

        MinecraftClient client = MinecraftClient.getInstance();
        int playerSlots = (client.player != null) ? client.player.getInventory().size() : 0;
        int containerSlots = Math.max(0, Math.min(totalSlots, totalSlots - playerSlots));
        boolean playerNonEmpty = stacks.subList(containerSlots, totalSlots)
                .stream().anyMatch(s -> !s.isEmpty());
        boolean containerNonEmpty = containerSlots > 0 && stacks.subList(0, containerSlots)
                .stream().anyMatch(s -> !s.isEmpty());

        System.out.println(" DEBUG: Inventory packet - syncId: " + packet.syncId()
                + "    - Total slots: " + totalSlots
                + "    - Non-empty: " + nonEmpty
                + " (container + " + (playerNonEmpty ? "non-empty" : "empty") + " player)");

        // Print first 10 items in container portion
        System.out.println("      - Container items (first 10):");
        int limit = Math.min(10, containerSlots > 0 ? containerSlots : totalSlots);
        for (int i = 0; i < limit; i++) {
            ItemStack s = stacks.get(i);
            if (!s.isEmpty()) {
                System.out.println("       [" + i + "] " + s.getItem().getName().getString() + " x" + s.getCount());
            }
        }
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("HEAD"))
    private void debugOnSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        ItemStack stack = packet.getStack();
        String itemInfo = stack.isEmpty() ? "empty" : (stack.getItem().getName().getString() + " x" + stack.getCount());
        System.out.println(" DEBUG: SlotUpdate packet - syncId: " + packet.getSyncId() + ", slot: " + packet.getSlot() + ", item: " + itemInfo);
    }
}
