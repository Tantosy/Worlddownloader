package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kuudraloremaster.worlddownloader.WorldDownloaderClient;
import net.kuudraloremaster.worlddownloader.util.ChunkListener;
import net.kuudraloremaster.worlddownloader.util.ClientChunkSerializer;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Environment(EnvType.CLIENT)
@Mixin(ClientPlayNetworkHandler.class)
public class ChunkDataMixin {

    @Inject(method = "onChunkData", at = @At("TAIL"))
    private void onChunkData(ChunkDataS2CPacket packet, CallbackInfo ci) {
        if (!WorldDownloaderClient.isRecording()) return;

        ClientPlayNetworkHandler handler = (ClientPlayNetworkHandler) (Object) this;
        var world = handler.getWorld();
        if (world == null) return;

        int chunkX = packet.getChunkX();
        int chunkZ = packet.getChunkZ();
        ChunkPos chunkPos = new ChunkPos(chunkX, chunkZ);

        WorldChunk loadedChunk = world.getChunk(chunkX, chunkZ);
        if (loadedChunk == null) return;

        try {
            NbtCompound chunkNbt = ClientChunkSerializer.serialize(world, loadedChunk);
            ChunkListener.addChunkNbt(chunkPos, chunkNbt);
        } catch (Exception e) {
            System.out.println("[WD] Failed to capture chunk " + chunkX + "," + chunkZ + ": " + e.getMessage());
        }
    }
}
