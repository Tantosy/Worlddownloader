package net.kuudraloremaster.worlddownloader.mixin;

import net.kuudraloremaster.worlddownloader.util.LevelStorageDirAccessor;
import net.minecraft.world.level.storage.LevelStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;

import java.nio.file.Path;

@Mixin(LevelStorage.class)
public class LevelStorageMixin implements LevelStorageDirAccessor {

    @Mutable
    @Shadow
    private Path savesDirectory;

    @Override
    public void worlddownloader$setSavesDirectory(Path path) {
        this.savesDirectory = path;
    }
}
