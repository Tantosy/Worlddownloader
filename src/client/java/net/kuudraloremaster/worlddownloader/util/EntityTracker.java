package net.kuudraloremaster.worlddownloader.util;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtFloat;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public class EntityTracker {

    private static final Map<ChunkPos, List<NbtCompound>> entities = new ConcurrentHashMap<>();

    public static void captureAllEntities(MinecraftClient client) {
        if (client.world == null) {
            System.out.println(" ClientWorld is null, cannot capture entities");
            return;
        }

        entities.clear();
        int totalEntities = 0;

        System.out.println(" Starting entity capture...");
        for (Entity entity : client.world.getEntities()) {
            try {
                NbtCompound entityNbt = serializeEntity(entity, client);
                if (entityNbt == null) continue;

                ChunkPos chunkPos = new ChunkPos(
                        MathHelper.floor(entity.getX()) >> 4,
                        MathHelper.floor(entity.getZ()) >> 4
                );

                entities.computeIfAbsent(chunkPos, k -> new ArrayList<>()).add(entityNbt);
                totalEntities++;
            } catch (Exception e) {
                System.out.println(" Failed to serialize entity " + entity + ": " + e.getMessage());
            }
        }

        System.out.println(" Captured " + totalEntities + " entities across " + entities.size() + " chunks");
    }

    private static NbtCompound serializeEntity(Entity entity, MinecraftClient client) {
        var entityTypeId = Registries.ENTITY_TYPE.getId(entity.getType());
        if (entityTypeId == null) return null;

        NbtCompound nbt = new NbtCompound();
        nbt.putString("id", entityTypeId.toString());

        // Position
        NbtList posNbt = new NbtList();
        posNbt.add(NbtDouble.of(entity.getX()));
        posNbt.add(NbtDouble.of(entity.getY()));
        posNbt.add(NbtDouble.of(entity.getZ()));
        nbt.put("Pos", posNbt);

        // Motion
        NbtList motionNbt = new NbtList();
        motionNbt.add(NbtDouble.of(entity.getVelocity().x));
        motionNbt.add(NbtDouble.of(entity.getVelocity().y));
        motionNbt.add(NbtDouble.of(entity.getVelocity().z));
        nbt.put("Motion", motionNbt);

        // Rotation
        NbtList rotationNbt = new NbtList();
        rotationNbt.add(NbtFloat.of(entity.getYaw()));
        rotationNbt.add(NbtFloat.of(entity.getPitch()));
        nbt.put("Rotation", rotationNbt);

        // UUID
        nbt.putIntArray("UUID", new int[]{
                (int)(entity.getUuid().getMostSignificantBits() >> 32),
                (int) entity.getUuid().getMostSignificantBits(),
                (int)(entity.getUuid().getLeastSignificantBits() >> 32),
                (int) entity.getUuid().getLeastSignificantBits()
        });

        // Common flags
        nbt.putBoolean("OnGround", entity.isOnGround());
        nbt.putBoolean("Invulnerable", entity.isInvulnerable());
        nbt.putInt("PortalCooldown", entity.getPortalCooldown());
        nbt.putBoolean("NoGravity", entity.hasNoGravity());
        nbt.putBoolean("Glowing", entity.isGlowing());
        nbt.putBoolean("Silent", entity.isSilent());

        // Custom name
        if (entity.hasCustomName() && entity.getCustomName() != null) {
            String customNameJson = net.minecraft.text.TextCodecs.CODEC
                    .encodeStart(
                            net.minecraft.registry.RegistryOps.of(
                                    com.mojang.serialization.JsonOps.INSTANCE,
                                    client.world.getRegistryManager()),
                            entity.getCustomName())
                    .result()
                    .map(e -> e.toString())
                    .orElse("\"\"");
            nbt.putString("CustomName", customNameJson);
            nbt.putBoolean("CustomNameVisible", entity.isCustomNameVisible());
        }

        // Living entity data
        if (entity instanceof LivingEntity living) {
            addLivingEntityData(nbt, living, client);
        }

        // ItemEntity data
        if (entity instanceof ItemEntity itemEntity) {
            addItemEntityData(nbt, itemEntity, client);
        }

        return nbt;
    }

    private static void addLivingEntityData(NbtCompound nbt, LivingEntity living,
                                             MinecraftClient client) {
        nbt.putFloat("Health", living.getHealth());
        nbt.putFloat("AbsorptionAmount", living.getAbsorptionAmount());
        nbt.putShort("HurtTime", (short) living.hurtTime);
        nbt.putShort("DeathTime", (short) living.deathTime);
        nbt.putFloat("HurtByTimestamp", 0);
        nbt.putBoolean("Invulnerable", living.isInvulnerable());

        // Hand and armor items
        NbtList handItems = new NbtList();
        for (var stack : List.of(living.getMainHandStack(), living.getOffHandStack())) {
            NbtCompound itemNbt = new NbtCompound();
            itemNbt.putString("id", Registries.ITEM.getId(stack.getItem()).toString());
            itemNbt.putByte("Count", (byte) stack.getCount());
            handItems.add(itemNbt);
        }
        nbt.put("HandItems", handItems);

        NbtList armorItems = new NbtList();
        for (var slot : List.of(net.minecraft.entity.EquipmentSlot.FEET, net.minecraft.entity.EquipmentSlot.LEGS,
                net.minecraft.entity.EquipmentSlot.CHEST, net.minecraft.entity.EquipmentSlot.HEAD)) {
            var stack = living.getEquippedStack(slot);
            NbtCompound itemNbt = new NbtCompound();
            itemNbt.putString("id", Registries.ITEM.getId(stack.getItem()).toString());
            itemNbt.putByte("Count", (byte) stack.getCount());
            armorItems.add(itemNbt);
        }
        nbt.put("ArmorItems", armorItems);

        NbtList handDropChances = new NbtList();
        handDropChances.add(NbtFloat.of(0.085f));
        handDropChances.add(NbtFloat.of(0.085f));
        nbt.put("HandDropChances", handDropChances);

        NbtList armorDropChances = new NbtList();
        for (int i = 0; i < 4; i++) armorDropChances.add(NbtFloat.of(0.085f));
        nbt.put("ArmorDropChances", armorDropChances);

        // Mob data
        if (living instanceof MobEntity mob) {
            nbt.putBoolean("PersistenceRequired", mob.isPersistent());
            nbt.putBoolean("LeftHanded", mob.isLeftHanded());
            nbt.putBoolean("CanPickUpLoot", mob.canPickUpLoot());
        }

        // Animal data
        if (living instanceof AnimalEntity animal) {
            nbt.putInt("InLove", animal.getLoveTicks());
            nbt.putInt("Age", animal.getBreedingAge());
            if (animal.getLovingPlayer() != null) {
                nbt.putIntArray("LoveCause", new int[]{
                        (int)(animal.getLovingPlayer().getUuid().getMostSignificantBits() >> 32),
                        (int) animal.getLovingPlayer().getUuid().getMostSignificantBits(),
                        (int)(animal.getLovingPlayer().getUuid().getLeastSignificantBits() >> 32),
                        (int) animal.getLovingPlayer().getUuid().getLeastSignificantBits()
                });
            }
        }
    }

    private static void addItemEntityData(NbtCompound nbt, ItemEntity itemEntity,
                                           MinecraftClient client) {
        var stack = itemEntity.getStack();
        NbtCompound itemNbt = new NbtCompound();
        itemNbt.putString("id", Registries.ITEM.getId(stack.getItem()).toString());
        itemNbt.putByte("Count", (byte) stack.getCount());
        nbt.put("Item", itemNbt);
        nbt.putShort("PickupDelay", (short) 10);
    }

    public static Map<ChunkPos, List<NbtCompound>> getAll() {
        return entities;
    }

    public static List<NbtCompound> getEntitiesForChunk(ChunkPos chunkPos) {
        List<NbtCompound> result = entities.getOrDefault(chunkPos, new ArrayList<>());
        System.out.println(" Retrieved " + result.size() + " entities for chunk " + chunkPos.x + "," + chunkPos.z);
        return result;
    }

    public static int getTotalTrackedEntities() {
        return entities.values().stream().mapToInt(List::size).sum();
    }

    public static void clear() {
        int count = getTotalTrackedEntities();
        entities.clear();
        System.out.println(" Cleared " + count + " tracked entities");
    }
}
