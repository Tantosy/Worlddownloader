package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.nbt.NbtCompound;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

// Stub — structures are handled separately in 1.21.4
@Environment(EnvType.CLIENT)
@Mixin(targets = "net.minecraft.world.ChunkSerializer")
public interface SerializedChunkInvoker {
}
