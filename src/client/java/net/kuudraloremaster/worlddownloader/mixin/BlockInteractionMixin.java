package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kuudraloremaster.worlddownloader.util.ChunkListener;
import net.kuudraloremaster.worlddownloader.util.ClientChunkSerializer;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Serialisiert den Chunk neu wenn der Spieler einen Block abbaut oder platziert.
 * Für interactBlock: nur wenn sich die Nachbar-Position wirklich verändert hat
 * (= echter Block-Platzierungsfall). Türen, Hebel, Kisten etc. werden herausgefiltert.
 */
@Environment(EnvType.CLIENT)
@Mixin(ClientPlayerInteractionManager.class)
public class BlockInteractionMixin {

    @Unique
    private BlockState wdAdjacentStateBefore;

    // Block abbauen — immer re-serialisieren
    @Inject(method = "breakBlock", at = @At("TAIL"))
    private void onBreakBlock(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        resaveChunk(pos);
    }

    // Block-State an der Platzierungs-Position VOR der Interaktion merken
    @Inject(method = "interactBlock", at = @At("HEAD"))
    private void captureAdjacentBefore(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) {
            wdAdjacentStateBefore = null;
            return;
        }
        BlockPos adjacentPos = hitResult.getBlockPos().offset(hitResult.getSide());
        wdAdjacentStateBefore = client.world.getBlockState(adjacentPos);
    }

    // Nach der Interaktion: nur re-serialisieren wenn sich die Nachbar-Position geändert hat
    @Inject(method = "interactBlock", at = @At("TAIL"))
    private void onInteractBlock(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult,
            CallbackInfoReturnable<ActionResult> cir) {
        if (wdAdjacentStateBefore == null) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        BlockPos adjacentPos = hitResult.getBlockPos().offset(hitResult.getSide());
        BlockState adjacentAfter = client.world.getBlockState(adjacentPos);

        // Nur bei echtem Block-Platzierungsfall (Nachbar-State hat sich geändert)
        if (adjacentAfter != wdAdjacentStateBefore) {
            resaveChunk(adjacentPos);
        }
        wdAdjacentStateBefore = null;
    }

    private static void resaveChunk(BlockPos blockPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;

        ChunkPos chunkPos = new ChunkPos(blockPos);
        WorldChunk chunk = client.world.getChunk(chunkPos.x, chunkPos.z);
        if (chunk == null) return;

        try {
            NbtCompound nbt = ClientChunkSerializer.serialize(client.world, chunk);
            ChunkListener.addChunkNbt(chunkPos, nbt);
        } catch (Exception e) {
            System.out.println("[WD] Failed to resave chunk after block placement at " + blockPos + ": " + e.getMessage());
        }
    }
}
