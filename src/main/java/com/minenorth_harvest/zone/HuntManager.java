package com.minenorth_harvest.zone;

import com.minenorth_harvest.config.HarvestConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Port de chasse.sk : apparition d'animaux autour des joueurs en zone de chasse + récompenses. */
public final class HuntManager {
    private HuntManager() {}

    private record Weighted(EntityType<?> type, int weight) {}

    // ------------------------------------------------------------ entrée / sortie

    static void onEnter(ServerPlayer p, String zone) {
        p.sendSystemMessage(Component.translatable("message.minenorth_harvest.hunt_enter", zone));
        if (HarvestConfig.HUNT_TITLE.get()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 40, 10));
            p.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.minenorth_harvest.hunt")));
            p.connection.send(new ClientboundSetSubtitleTextPacket(Component.literal("§7" + zone)));
        }
    }

    static void onLeave(ServerPlayer p, String zone) {
        p.sendSystemMessage(Component.translatable("message.minenorth_harvest.hunt_leave", zone));
    }

    // ------------------------------------------------------------ apparition

    static void tick(MinecraftServer server, int tickCounter) {
        if (!HarvestConfig.HUNT_ENABLED.get()) return;
        if (tickCounter % (HarvestConfig.HUNT_INTERVAL.get() * 20) != 0) return;

        List<Weighted> animals = parseAnimals();
        if (animals.isEmpty()) return;
        Set<EntityType<?>> counted = new HashSet<>();
        for (Weighted w : animals) counted.add(w.type);
        Set<Block> ground = parseGround();

        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            ServerLevel level = p.serverLevel();
            List<Zone> zones = new ArrayList<>();
            for (Zone z : ZoneData.get(level).zonesAt(p.getX(), p.getY(), p.getZ())) {
                if (z.type == ZoneType.CHASSE) zones.add(z);
            }
            if (zones.isEmpty()) continue;

            int r = HarvestConfig.HUNT_COUNT_RADIUS.get();
            AABB box = p.getBoundingBox().inflate(r, r, r);
            int count = level.getEntitiesOfClass(LivingEntity.class, box, e -> counted.contains(e.getType())).size();
            int max = HarvestConfig.HUNT_MAX_ANIMALS.get();

            for (int i = 0; i < HarvestConfig.HUNT_ATTEMPTS.get() && count < max; i++) {
                if (trySpawn(level, p, zones, animals, ground)) count++;
            }
        }
    }

    private static boolean trySpawn(ServerLevel level, ServerPlayer p, List<Zone> zones, List<Weighted> animals, Set<Block> ground) {
        RandomSource random = level.random;
        int radius = HarvestConfig.HUNT_SPAWN_RADIUS.get();
        int min = Math.min(HarvestConfig.HUNT_MIN_DISTANCE.get(), radius - 1);
        int dx, dz;
        int guard = 0;
        do {
            dx = random.nextInt(radius * 2 + 1) - radius;
            dz = random.nextInt(radius * 2 + 1) - radius;
        } while (dx * dx + dz * dz < min * min && ++guard < 10);

        int x = p.getBlockX() + dx;
        int z = p.getBlockZ() + dz;
        if (!level.hasChunkAt(new BlockPos(x, p.getBlockY(), z))) return false;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        BlockPos spawn = new BlockPos(x, y, z);

        // toujours à l'intérieur d'une zone de chasse
        boolean inside = false;
        for (Zone zone : zones) if (zone.contains(spawn)) { inside = true; break; }
        if (!inside) return false;
        // uniquement sur les blocs autorisés (herbe par défaut)
        if (!ground.isEmpty() && !ground.contains(level.getBlockState(spawn.below()).getBlock())) return false;
        if (!level.getBlockState(spawn).isAir() && !level.getBlockState(spawn).canBeReplaced()) return false;

        EntityType<?> type = pick(animals, random);
        Entity e = type.spawn(level, spawn, MobSpawnType.EVENT);
        return e != null;
    }

    private static EntityType<?> pick(List<Weighted> list, RandomSource random) {
        int total = 0;
        for (Weighted w : list) total += w.weight;
        int roll = random.nextInt(Math.max(1, total));
        for (Weighted w : list) {
            roll -= w.weight;
            if (roll < 0) return w.type;
        }
        return list.get(0).type;
    }

    private static List<Weighted> parseAnimals() {
        List<Weighted> out = new ArrayList<>();
        for (String s : HarvestConfig.HUNT_ANIMALS.get()) {
            String[] parts = s.split("=");
            ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
            if (id == null || !ForgeRegistries.ENTITY_TYPES.containsKey(id)) continue;
            int weight = 1;
            if (parts.length > 1) {
                try { weight = Math.max(0, Integer.parseInt(parts[1].trim())); } catch (NumberFormatException ignored) {}
            }
            if (weight > 0) out.add(new Weighted(ForgeRegistries.ENTITY_TYPES.getValue(id), weight));
        }
        return out;
    }

    private static Set<Block> parseGround() {
        Set<Block> out = new HashSet<>();
        for (String s : HarvestConfig.HUNT_GROUND.get()) {
            ResourceLocation id = ResourceLocation.tryParse(s.trim());
            if (id != null && ForgeRegistries.BLOCKS.containsKey(id)) out.add(ForgeRegistries.BLOCKS.getValue(id));
        }
        return out;
    }

    // ------------------------------------------------------------ récompenses

    /** Récompense applicable à cette entité (type configuré + zone si onlyInHuntingZones), sinon null. */
    private static HuntRewards.Reward rewardFor(LivingEntity entity) {
        if (entity.level().isClientSide) return null;
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        HuntRewards.Reward reward = id == null ? null : HuntRewards.rewards.get(id);
        if (reward == null) return null;
        if (HuntRewards.onlyInZones) {
            for (Zone z : ZoneData.get((ServerLevel) entity.level()).zonesAt(entity.getX(), entity.getY(), entity.getZ())) {
                if (z.type == ZoneType.CHASSE) return reward;
            }
            return null;
        }
        return reward;
    }

    /**
     * L'animal ne lâche RIEN au sol : les récompenses vont directement dans l'inventaire du chasseur.
     * (Appelé en priorité LOWEST pour supprimer aussi les drops ajoutés par d'autres mods.)
     */
    public static void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        HuntRewards.Reward reward = rewardFor(entity);
        if (reward == null) return;

        if (HuntRewards.clearVanillaDrops) {
            event.getDrops().clear();
            event.setCanceled(true);
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer hunter)) return; // pas de chasseur : rien

        ServerLevel level = (ServerLevel) entity.level();
        boolean lost = false;

        RandomSource random = level.random;
        for (HuntRewards.RewardItem ri : reward.items()) {
            if (ri.chance() < 1.0 && random.nextDouble() >= ri.chance()) continue;
            Item item = ForgeRegistries.ITEMS.getValue(ri.item());
            if (item == null || !ForgeRegistries.ITEMS.containsKey(ri.item())) continue;
            ItemStack stack = new ItemStack(item, Math.max(1, ri.count()));
            if (!ri.name().isEmpty()) {
                stack.setHoverName(Component.literal(color(ri.name())).withStyle(s -> s.withItalic(false)));
            }
            if (!hunter.getInventory().add(stack) && !stack.isEmpty()) {
                if (HuntRewards.dropIfInventoryFull) {
                    hunter.drop(stack, false);
                } else {
                    lost = true;
                }
            }
        }
        hunter.inventoryMenu.broadcastChanges();
        if (!reward.message().isEmpty()) hunter.sendSystemMessage(Component.literal(color(reward.message())));
        if (lost) hunter.sendSystemMessage(Component.translatable("message.minenorth_harvest.hunt_inventory_full"));
    }

    /** Pas d'orbes d'XP non plus pour les animaux chassés (option noExperience). */
    public static void onExperience(LivingExperienceDropEvent event) {
        if (HuntRewards.noExperience && rewardFor(event.getEntity()) != null) {
            event.setDroppedExperience(0);
            event.setCanceled(true);
        }
    }

    private static String color(String s) {
        return s.replace('&', '§');
    }
}
