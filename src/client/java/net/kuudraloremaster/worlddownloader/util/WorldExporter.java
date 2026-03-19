package net.kuudraloremaster.worlddownloader.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.*;
import net.minecraft.registry.Registries;
import net.minecraft.util.Uuids;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;

import java.io.*;
import java.nio.file.Files;

@Environment(EnvType.CLIENT)
public class WorldExporter {

    public static void createLoadableWorld(File worldFolder) {
        try {
            MinecraftClient client = MinecraftClient.getInstance();

            new File(worldFolder, "region").mkdirs();
            new File(worldFolder, "entities").mkdirs();
            new File(worldFolder, "playerdata").mkdirs();
            new File(worldFolder, "stats").mkdirs();
            new File(worldFolder, "advancements").mkdirs();
            new File(worldFolder, "data").mkdirs();
            new File(worldFolder, "poi").mkdirs();

            // session.lock
            try (DataOutputStream dos = new DataOutputStream(new FileOutputStream(new File(worldFolder, "session.lock")))) {
                dos.writeLong(System.currentTimeMillis());
            }

            writeLevelDat(worldFolder, client);
            writePlayerData(worldFolder, client);
            writeEmptyAdvancements(worldFolder, client);
            writeEmptyStats(worldFolder, client);

            System.out.println(" World structure created at: " + worldFolder.getAbsolutePath());
        } catch (Exception e) {
            System.out.println(" Failed to create world structure: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void writeLevelDat(File worldFolder, MinecraftClient client) throws IOException {
        int dataVersion = ChunkListener.getDataVersion();
        String versionName = "1.21.11";

        NbtCompound levelDat = new NbtCompound();
        levelDat.putInt("DataVersion", dataVersion);
        levelDat.putString("LevelName", "Downloaded World");
        levelDat.putLong("LastPlayed", System.currentTimeMillis());
        levelDat.putLong("Time", client.world != null ? client.world.getTime() : 0L);
        levelDat.putLong("DayTime", client.world != null ? client.world.getTimeOfDay() : 6000L);
        levelDat.putInt("GameType", 1); // Creative
        levelDat.putBoolean("hardcore", false);
        levelDat.putBoolean("allowCommands", false);
        levelDat.putByte("Difficulty", (byte) 2);
        levelDat.putBoolean("DifficultyLocked", false);
        levelDat.putBoolean("initialized", true);
        levelDat.putInt("version", 19133);

        // Spawn (1.21.11 format) — find valid position: feet+head=air, block below=solid
        int spawnX = 0, spawnY = 64, spawnZ = 0;
        if (client.player != null && client.world != null) {
            spawnX = MathHelper.floor(client.player.getX());
            spawnZ = MathHelper.floor(client.player.getZ());
            spawnY = findSafeSpawnY(client.world, spawnX, spawnZ);
        }
        NbtCompound spawn = new NbtCompound();
        spawn.putIntArray("pos", new int[]{spawnX, spawnY, spawnZ});
        spawn.putFloat("pitch", 0f);
        spawn.putFloat("yaw", client.player != null ? client.player.getYaw() : 0f);
        spawn.putString("dimension", "minecraft:overworld");
        levelDat.put("spawn", spawn);

        // Version
        NbtCompound version = new NbtCompound();
        version.putInt("Id", dataVersion);
        version.putString("Name", versionName);
        version.putString("Series", "main");
        version.putBoolean("Snapshot", false);
        levelDat.put("Version", version);
        levelDat.putBoolean("WasModded", true);

        // WorldGenSettings — 1:1 Vanilla-Format aus echter 1.21.11 Welt
        NbtCompound worldGenSettings = new NbtCompound();
        worldGenSettings.putLong("seed", 0L);
        worldGenSettings.putBoolean("generate_features", true);
        worldGenSettings.putBoolean("bonus_chest", false);
        NbtCompound dimensions = new NbtCompound();

        NbtCompound overworld = new NbtCompound();
        NbtCompound overworldGen = new NbtCompound();
        overworldGen.putString("type", "minecraft:noise");
        overworldGen.putString("settings", "minecraft:overworld");
        NbtCompound overworldBiomeSource = new NbtCompound();
        overworldBiomeSource.putString("type", "minecraft:multi_noise");
        overworldBiomeSource.putString("preset", "minecraft:overworld");
        overworldGen.put("biome_source", overworldBiomeSource);
        overworld.put("generator", overworldGen);
        overworld.putString("type", "minecraft:overworld");
        dimensions.put("minecraft:overworld", overworld);

        NbtCompound nether = new NbtCompound();
        NbtCompound netherGen = new NbtCompound();
        netherGen.putString("type", "minecraft:noise");
        netherGen.putString("settings", "minecraft:nether");
        NbtCompound netherBiomeSource = new NbtCompound();
        netherBiomeSource.putString("type", "minecraft:multi_noise");
        netherBiomeSource.putString("preset", "minecraft:nether");
        netherGen.put("biome_source", netherBiomeSource);
        nether.put("generator", netherGen);
        nether.putString("type", "minecraft:the_nether");
        dimensions.put("minecraft:the_nether", nether);

        NbtCompound end = new NbtCompound();
        NbtCompound endGen = new NbtCompound();
        endGen.putString("type", "minecraft:noise");
        endGen.putString("settings", "minecraft:end");
        NbtCompound endBiomeSource = new NbtCompound();
        endBiomeSource.putString("type", "minecraft:the_end");
        endGen.put("biome_source", endBiomeSource);
        end.put("generator", endGen);
        end.putString("type", "minecraft:the_end");
        dimensions.put("minecraft:the_end", end);

        worldGenSettings.put("dimensions", dimensions);
        levelDat.put("WorldGenSettings", worldGenSettings);

        // Game rules (1.21.11 format: byte values with minecraft: prefix)
        NbtCompound gameRules = new NbtCompound();
        gameRules.putByte("minecraft:block_drops", (byte) 1);
        gameRules.putByte("minecraft:advance_time", (byte) 1);
        gameRules.putByte("minecraft:advance_weather", (byte) 1);
        gameRules.putByte("minecraft:command_blocks_work", (byte) 1);
        gameRules.putByte("minecraft:command_block_output", (byte) 1);
        gameRules.putByte("minecraft:drowning_damage", (byte) 1);
        gameRules.putByte("minecraft:entity_drops", (byte) 1);
        gameRules.putByte("minecraft:fall_damage", (byte) 1);
        gameRules.putByte("minecraft:fire_damage", (byte) 1);
        gameRules.putInt("minecraft:fire_spread_radius_around_player", 128);
        gameRules.putByte("minecraft:forgive_dead_players", (byte) 1);
        gameRules.putByte("minecraft:freeze_damage", (byte) 1);
        gameRules.putByte("minecraft:global_sound_events", (byte) 1);
        gameRules.putByte("minecraft:immediate_respawn", (byte) 0);
        gameRules.putByte("minecraft:keep_inventory", (byte) 0);
        gameRules.putByte("minecraft:lava_source_conversion", (byte) 0);
        gameRules.putByte("minecraft:limited_crafting", (byte) 0);
        gameRules.putByte("minecraft:locator_bar", (byte) 1);
        gameRules.putByte("minecraft:log_admin_commands", (byte) 1);
        gameRules.putInt("minecraft:max_block_modifications", 32768);
        gameRules.putInt("minecraft:max_command_forks", 65536);
        gameRules.putInt("minecraft:max_command_sequence_length", 65536);
        gameRules.putInt("minecraft:max_entity_cramming", 24);
        gameRules.putInt("minecraft:max_snow_accumulation_height", 1);
        gameRules.putByte("minecraft:mob_drops", (byte) 1);
        gameRules.putByte("minecraft:mob_explosion_drop_decay", (byte) 1);
        gameRules.putByte("minecraft:block_explosion_drop_decay", (byte) 1);
        gameRules.putByte("minecraft:tnt_explosion_drop_decay", (byte) 0);
        gameRules.putByte("minecraft:mob_griefing", (byte) 1);
        gameRules.putByte("minecraft:natural_health_regeneration", (byte) 1);
        gameRules.putByte("minecraft:player_movement_check", (byte) 1);
        gameRules.putByte("minecraft:elytra_movement_check", (byte) 1);
        gameRules.putInt("minecraft:players_nether_portal_creative_delay", 0);
        gameRules.putInt("minecraft:players_nether_portal_default_delay", 80);
        gameRules.putInt("minecraft:players_sleeping_percentage", 100);
        gameRules.putByte("minecraft:projectiles_can_break_blocks", (byte) 1);
        gameRules.putByte("minecraft:pvp", (byte) 1);
        gameRules.putByte("minecraft:raids", (byte) 1);
        gameRules.putInt("minecraft:random_tick_speed", 3);
        gameRules.putByte("minecraft:reduced_debug_info", (byte) 0);
        gameRules.putInt("minecraft:respawn_radius", 10);
        gameRules.putByte("minecraft:send_command_feedback", (byte) 1);
        gameRules.putByte("minecraft:show_advancement_messages", (byte) 1);
        gameRules.putByte("minecraft:show_death_messages", (byte) 1);
        gameRules.putByte("minecraft:spawn_mobs", (byte) 1);
        gameRules.putByte("minecraft:spawn_monsters", (byte) 1);
        gameRules.putByte("minecraft:spawn_patrols", (byte) 1);
        gameRules.putByte("minecraft:spawn_phantoms", (byte) 1);
        gameRules.putByte("minecraft:spawn_wandering_traders", (byte) 1);
        gameRules.putByte("minecraft:spawn_wardens", (byte) 1);
        gameRules.putByte("minecraft:spawner_blocks_work", (byte) 1);
        gameRules.putByte("minecraft:spectators_generate_chunks", (byte) 1);
        gameRules.putByte("minecraft:spread_vines", (byte) 1);
        gameRules.putByte("minecraft:tnt_explodes", (byte) 1);
        gameRules.putByte("minecraft:universal_anger", (byte) 0);
        gameRules.putByte("minecraft:water_source_conversion", (byte) 1);
        gameRules.putByte("minecraft:allow_entering_nether_using_portals", (byte) 1);
        gameRules.putByte("minecraft:ender_pearls_vanish_on_death", (byte) 1);
        levelDat.put("game_rules", gameRules);

        // DataPacks
        NbtCompound dataPacks = new NbtCompound();
        NbtList enabled = new NbtList();
        enabled.add(NbtString.of("vanilla"));
        dataPacks.put("Enabled", enabled);
        dataPacks.put("Disabled", new NbtList());
        levelDat.put("DataPacks", dataPacks);

        // Weather & misc
        levelDat.putByte("raining", (byte) 0);
        levelDat.putByte("thundering", (byte) 0);
        levelDat.putInt("rainTime", 0);
        levelDat.putInt("thunderTime", 0);
        levelDat.putInt("clearWeatherTime", 0);

        NbtCompound dataWrapper = new NbtCompound();
        dataWrapper.put("Data", levelDat);

        NbtIo.writeCompressed(dataWrapper, worldFolder.toPath().resolve("level.dat"));
        System.out.println(" level.dat written (DataVersion=" + dataVersion + ", MC=" + versionName + ")");
    }

    private static void writePlayerData(File worldFolder, MinecraftClient client) throws IOException {
        if (client.player == null || client.world == null) return;

        int dataVersion = ChunkListener.getDataVersion();
        String uuid = client.player.getUuid().toString();

        NbtCompound player = new NbtCompound();
        player.putInt("DataVersion", dataVersion);
        player.putInt("playerGameType", 1); // Creative
        player.putFloat("Health", client.player.getHealth());
        player.putInt("foodLevel", client.player.getHungerManager().getFoodLevel());
        player.putFloat("foodSaturationLevel", client.player.getHungerManager().getSaturationLevel());
        player.putFloat("XpP", client.player.experienceProgress);
        player.putInt("XpLevel", client.player.experienceLevel);
        player.putInt("XpTotal", client.player.totalExperience);
        player.putShort("Fire", (short) 0);
        player.putShort("Air", (short) client.player.getAir());
        player.putBoolean("OnGround", true);
        player.putFloat("FallDistance", 0.0f);
        player.putInt("HurtResistantTime", 60); // 3 seconds of damage immunity after spawn
        player.putBoolean("Invulnerable", client.player.isInvulnerable());
        player.putIntArray("UUID", Uuids.toIntArray(client.player.getUuid()));

        // Find a valid spawn position: feet=air, head=air, ground below=solid
        int safeY = findSafeSpawnY(client.world, MathHelper.floor(client.player.getX()),
                MathHelper.floor(client.player.getZ()));
        NbtList pos = new NbtList();
        pos.add(NbtDouble.of(client.player.getX()));
        pos.add(NbtDouble.of(safeY));
        pos.add(NbtDouble.of(client.player.getZ()));
        player.put("Pos", pos);

        NbtList motion = new NbtList();
        motion.add(NbtDouble.of(0));
        motion.add(NbtDouble.of(0));
        motion.add(NbtDouble.of(0));
        player.put("Motion", motion);

        NbtList rotation = new NbtList();
        rotation.add(NbtFloat.of(client.player.getYaw()));
        rotation.add(NbtFloat.of(client.player.getPitch()));
        player.put("Rotation", rotation);

        NbtList inventory = new NbtList();
        for (int i = 0; i < client.player.getInventory().size(); i++) {
            ItemStack stack = client.player.getInventory().getStack(i);
            if (!stack.isEmpty()) {
                NbtCompound itemNbt = new NbtCompound();
                itemNbt.putByte("Slot", (byte) i);
                itemNbt.putString("id", Registries.ITEM.getId(stack.getItem()).toString());
                itemNbt.putInt("count", stack.getCount());
                inventory.add(itemNbt);
            }
        }
        player.put("Inventory", inventory);
        player.put("EnderItems", new NbtList());
        player.putString("Dimension", "minecraft:overworld");

        NbtCompound abilities = new NbtCompound();
        abilities.putFloat("walkSpeed", 0.1f);
        abilities.putFloat("flySpeed", 0.05f);
        abilities.putBoolean("mayfly", false);
        abilities.putBoolean("flying", false);
        abilities.putBoolean("invulnerable", false);
        abilities.putBoolean("mayBuild", true);
        abilities.putBoolean("instabuild", false);
        player.put("abilities", abilities);

        NbtIo.writeCompressed(player, new File(new File(worldFolder, "playerdata"), uuid + ".dat").toPath());
        System.out.println(" playerdata written for " + uuid);
    }

    private static void writeEmptyAdvancements(File worldFolder, MinecraftClient client) throws IOException {
        if (client.player == null) return;
        String uuid = client.player.getUuid().toString();
        Files.writeString(new File(new File(worldFolder, "advancements"), uuid + ".json").toPath(), "{}");
    }

    private static void writeEmptyStats(File worldFolder, MinecraftClient client) throws IOException {
        if (client.player == null) return;
        int dataVersion = ChunkListener.getDataVersion();
        String uuid = client.player.getUuid().toString();
        Files.writeString(
                new File(new File(worldFolder, "stats"), uuid + ".json").toPath(),
                "{\"stats\":{},\"DataVersion\":" + dataVersion + "}"
        );
    }

    /**
     * Findet einen sicheren Spawn-Y-Wert für den Spieler.
     * Priorität: WORLD_SURFACE Heightmap → Section-Scan → ClientWorld-Scan → Fallback 64
     */
    private static int findSafeSpawnY(ClientWorld world, int x, int z) {
        ChunkPos chunkPos = new ChunkPos(x >> 4, z >> 4);
        NbtCompound chunkNbt = ChunkListener.getCachedChunkNbt(chunkPos);

        if (chunkNbt != null) {
            int localX = Math.floorMod(x, 16);
            int localZ = Math.floorMod(z, 16);

            // Versuch 1: WORLD_SURFACE Heightmap aus dem Chunk-NBT
            int heightY = readWorldSurface(chunkNbt, localX, localZ);
            if (heightY != Integer.MIN_VALUE) {
                System.out.println("[WD] SpawnY aus Heightmap: " + heightY);
                return heightY;
            }

            // Versuch 2: Höchste Section mit nicht-Luft Blöcken
            int sectionY = highestNonAirSectionTop(chunkNbt);
            if (sectionY != Integer.MIN_VALUE) {
                System.out.println("[WD] SpawnY aus Section-Scan: " + sectionY);
                return sectionY;
            }
        }

        // Versuch 3: ClientWorld-Scan (Fallback, falls Chunk nicht gecacht)
        if (world != null) {
            for (int y = 318; y > world.getBottomY() + 1; y--) {
                BlockPos feet   = new BlockPos(x, y,     z);
                BlockPos head   = new BlockPos(x, y + 1, z);
                BlockPos ground = new BlockPos(x, y - 1, z);

                boolean feetClear   = world.getBlockState(feet).getCollisionShape(world, feet).isEmpty();
                boolean headClear   = world.getBlockState(head).getCollisionShape(world, head).isEmpty();
                boolean groundSolid = !world.getBlockState(ground).getCollisionShape(world, ground).isEmpty();

                if (feetClear && headClear && groundSolid) {
                    return y;
                }
            }
        }

        System.out.println("[WD] SpawnY Fallback: 64");
        return 64;
    }

    /**
     * Liest den WORLD_SURFACE Heightmap-Wert aus dem Chunk-NBT.
     * Gibt spawn-Y zurück (= Y wo der Spieler steht), oder Integer.MIN_VALUE falls nicht verfügbar.
     * Overworld-spezifisch: bottomY = -64, 9 Bits pro Eintrag.
     */
    private static int readWorldSurface(NbtCompound chunkNbt, int localX, int localZ) {
        if (!chunkNbt.contains("Heightmaps")) return Integer.MIN_VALUE;
        var hmOpt = chunkNbt.getCompound("Heightmaps");
        if (hmOpt.isEmpty()) return Integer.MIN_VALUE;
        NbtCompound hm = hmOpt.get();

        if (!hm.contains("WORLD_SURFACE")) return Integer.MIN_VALUE;
        var dataOpt = hm.getLongArray("WORLD_SURFACE");
        if (dataOpt.isEmpty()) return Integer.MIN_VALUE;
        long[] data = dataOpt.get();
        if (data.length == 0) return Integer.MIN_VALUE;

        // 9 Bits pro Eintrag, 7 Einträge pro long (non-compact)
        int index = localZ * 16 + localX;
        int perLong = 7;
        int longIndex = index / perLong;
        int bitOffset = (index % perLong) * 9;
        if (longIndex >= data.length) return Integer.MIN_VALUE;

        int packed = (int) ((data[longIndex] >> bitOffset) & 0x1FF);
        if (packed == 0) return Integer.MIN_VALUE; // kein Oberflächen-Block gefunden

        // packed = (Y_oberflächenblock + 1) - bottomY = Y + 65
        // spawnY (= Y wo Spieler steht) = packed + bottomY = packed - 64
        return packed - 64;
    }

    /**
     * Sucht die höchste Section mit nicht-Luft Blöcken im Chunk-NBT.
     * Gibt (sectionY + 1) * 16 zurück (konservative obere Grenze), oder Integer.MIN_VALUE.
     */
    private static int highestNonAirSectionTop(NbtCompound chunkNbt) {
        if (!(chunkNbt.get("sections") instanceof NbtList sections)) return Integer.MIN_VALUE;

        int highest = Integer.MIN_VALUE;
        for (int i = 0; i < sections.size(); i++) {
            if (!(sections.get(i) instanceof NbtCompound section)) continue;

            if (!(section.get("block_states") instanceof NbtCompound blockStates)) continue;
            if (!(blockStates.get("palette") instanceof NbtList palette)) continue;

            boolean hasNonAir = false;
            for (int j = 0; j < palette.size(); j++) {
                if (!(palette.get(j) instanceof NbtCompound entry)) continue;
                String name = entry.getString("Name").orElse("minecraft:air");
                if (!name.equals("minecraft:air")) {
                    hasNonAir = true;
                    break;
                }
            }
            if (!hasNonAir) continue;

            int sectionY = section.getByte("Y").orElse((byte) 0);
            int topY = (sectionY + 1) * 16;
            if (topY > highest) highest = topY;
        }
        return highest;
    }
}
