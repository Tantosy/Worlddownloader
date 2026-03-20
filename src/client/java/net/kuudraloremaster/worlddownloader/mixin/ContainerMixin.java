package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kuudraloremaster.worlddownloader.util.ContainerTracker;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.CloseScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket;
import net.minecraft.network.packet.s2c.play.OpenScreenS2CPacket;
import net.minecraft.network.packet.s2c.play.ScreenHandlerSlotUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public class ContainerMixin {

    @Inject(method = "onOpenScreen", at = @At("TAIL"))
    private void onOpenScreen(OpenScreenS2CPacket packet, CallbackInfo ci) {
        ContainerTracker.onContainerOpened(packet.getSyncId(), packet.getScreenHandlerType());
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.crosshairTarget instanceof BlockHitResult blockHit) {
            ContainerTracker.setBlockPos(packet.getSyncId(), blockHit.getBlockPos());
        }
    }

    @Inject(method = "onCloseScreen", at = @At("TAIL"))
    private void onCloseScreen(CloseScreenS2CPacket packet, CallbackInfo ci) {
        ClientPlayNetworkHandler handler = (ClientPlayNetworkHandler) (Object) this;
        ContainerTracker.onContainerClosed(packet.getSyncId(), handler.getWorld());
    }

    @Inject(method = "onInventory", at = @At("TAIL"))
    private void onInventory(InventoryS2CPacket packet, CallbackInfo ci) {
        List<ItemStack> stacks = packet.contents();
        ContainerTracker.onInventoryUpdate(packet.syncId(), stacks);
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    private void onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        ContainerTracker.onSlotUpdate(packet.getSyncId(), packet.getSlot(), packet.getStack());
    }
}
