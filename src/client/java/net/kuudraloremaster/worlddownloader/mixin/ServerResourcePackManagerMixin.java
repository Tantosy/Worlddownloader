package net.kuudraloremaster.worlddownloader.mixin;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.kuudraloremaster.worlddownloader.WorldDownloaderClient;
import net.minecraft.client.resource.server.ServerResourcePackManager;
import net.minecraft.util.Downloader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Map;

/**
 * Hook for saving server resource pack location when downloaded.
 */
@Environment(EnvType.CLIENT)
@Mixin(ServerResourcePackManager.class)
public abstract class ServerResourcePackManagerMixin {

    @Inject(method = "onDownload", at = @At("TAIL"))
    private void onDownload(Collection<?> packs, Downloader.DownloadResult result, CallbackInfo ci) {
        System.out.println(" [WorldDownloader] Resource pack download completed");

        // Get the downloaded packs map (UUID -> Path)
        Map<?, ?> downloaded = result.downloaded();

        if (downloaded != null && !downloaded.isEmpty()) {
            // Get the first downloaded pack path
            for (Map.Entry<?, ?> entry : downloaded.entrySet()) {
                Object value = entry.getValue();
                if (value instanceof Path) {
                    Path packPath = (Path) value;
                    WorldDownloaderClient.resourcePackLocation = packPath;
                    System.out.println(" [WorldDownloader] Resource pack location saved: " + packPath);
                    break; // Only need the first one
                }
            }
        }
    }
}
