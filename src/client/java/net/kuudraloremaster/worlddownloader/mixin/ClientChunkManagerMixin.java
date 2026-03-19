package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kuudraloremaster.worlddownloader.util.ChunkListener;
import net.kuudraloremaster.worlddownloader.util.ClientChunkSerializer;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Fängt loadChunkFromPacket ab — wird sowohl in Singleplayer als auch
 * Multiplayer aufgerufen wenn ein Chunk in die ClientWorld geladen wird.
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientChunkManager.class)
public class ClientChunkManagerMixin {

    @Inject(method = "loadChunkFromPacket", at = @At("RETURN"))
    private void onLoadChunkFromPacket(int x, int z, PacketByteBuf buf,
            Map<Heightmap.Type, long[]> heightmaps, Consumer<?> consumer,
            CallbackInfoReturnable<WorldChunk> cir) {
        WorldChunk chunk = cir.getReturnValue();
        if (chunk == null) return;

        ClientWorld world = (ClientWorld) chunk.getWorld();
        if (world == null) return;

        ChunkPos pos = new ChunkPos(x, z);
        try {
            NbtCompound nbt = ClientChunkSerializer.serialize(world, chunk);
            ChunkListener.addChunkNbt(pos, nbt);
        } catch (Exception e) {
            System.out.println("[WD] Failed to capture chunk " + x + "," + z + ": " + e.getMessage());
        }
    }
}
