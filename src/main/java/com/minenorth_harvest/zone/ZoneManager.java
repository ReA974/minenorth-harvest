package com.minenorth_harvest.zone;

import com.minenorth_harvest.block.FruitLeavesBlock;
import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Tick serveur des zones : messages d'entrée, accélération des vergers, repousse, affichage. */
public final class ZoneManager {
    private ZoneManager() {}

    /** Ticks aléatoires vanilla par bloc et par seconde (randomTickSpeed = 3) ≈ 1 / 68. */
    private static final double VANILLA_TICKS_PER_SECOND = 20.0 * 3.0 / 4096.0;

    private static final Map<UUID, String> CURRENT_ZONE = new HashMap<>();
    private static final List<Showing> SHOWING = new ArrayList<>();
    private static int tickCounter;

    private record Showing(ServerPlayer player, ServerLevel level, Zone zone, long until) {}

    public static void tick() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        tickCounter++;

        if (tickCounter % 10 == 0) {
            trackPlayers(server);
            tickShowing();
        }
        if (tickCounter % 20 == 0) {
            for (ServerLevel level : server.getAllLevels()) {
                ZoneData data = ZoneData.get(level);
                boostOrchards(level, data);
                processRegrows(level, data);
            }
        }
    }

    // ------------------------------------------------------------ joueurs dans les zones

    private static final Map<ServerLevel, Set<Zone>> ACTIVE_ORCHARDS = new HashMap<>();

    private static void trackPlayers(MinecraftServer server) {
        ACTIVE_ORCHARDS.clear();
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.isSpectator()) continue;
            ServerLevel level = p.serverLevel();
            ZoneData data = ZoneData.get(level);
            Zone zone = data.zoneAt(p.getX(), p.getY(), p.getZ());

            if (zone != null && zone.type == ZoneType.VERGER) {
                ACTIVE_ORCHARDS.computeIfAbsent(level, l -> new HashSet<>()).add(zone);
            }

            String key = zone == null ? "" : level.dimension().location() + "/" + zone.name;
            String previous = CURRENT_ZONE.getOrDefault(p.getUUID(), "");
            if (!key.equals(previous)) {
                CURRENT_ZONE.put(p.getUUID(), key);
                if (HarvestConfig.ZONE_ENTER_MESSAGE.get()) {
                    if (zone != null) {
                        p.displayClientMessage(Component.translatable("message.minenorth_harvest.zone_enter." + zone.type.id(), zone.name), true);
                    } else {
                        p.displayClientMessage(Component.translatable("message.minenorth_harvest.zone_leave"), true);
                    }
                }
            }
        }
    }

    public static void onLogout(ServerPlayer player) {
        CURRENT_ZONE.remove(player.getUUID());
        SHOWING.removeIf(s -> s.player.getUUID().equals(player.getUUID()));
    }

    // ------------------------------------------------------------ vergers

    private static void boostOrchards(ServerLevel level, ZoneData data) {
        Set<Zone> active = ACTIVE_ORCHARDS.get(level);
        if (active == null || active.isEmpty()) return;
        RandomSource random = level.random;
        double growth = HarvestConfig.FRUIT_GROWTH_CHANCE.get();
        int maxChecks = HarvestConfig.ORCHARD_MAX_CHECKS.get();

        for (Zone zone : active) {
            if (data.byName(zone.name) != zone) continue; // zone supprimée entre temps
            double mult = zone.speed > 0 ? zone.speed : HarvestConfig.ORCHARD_SPEED.get();
            if (mult <= 1.0) continue;
            // Nombre de ticks aléatoires "en plus" à distribuer dans la zone pendant cette seconde
            double wanted = zone.volume() * (mult - 1.0) * VANILLA_TICKS_PER_SECOND;
            int checks = (int) Math.min(maxChecks, Math.floor(wanted) + (random.nextDouble() < wanted % 1 ? 1 : 0));
            if (checks <= 0) continue;
            // Si on plafonne, chaque test compte pour plusieurs ticks
            double chance = Math.min(1.0, growth * (wanted / checks));

            int sx = zone.max.getX() - zone.min.getX() + 1;
            int sy = zone.max.getY() - zone.min.getY() + 1;
            int sz = zone.max.getZ() - zone.min.getZ() + 1;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < checks; i++) {
                pos.set(zone.min.getX() + random.nextInt(sx), zone.min.getY() + random.nextInt(sy), zone.min.getZ() + random.nextInt(sz));
                if (!level.isLoaded(pos)) continue;
                BlockState state = level.getBlockState(pos);
                if (FruitLeavesBlock.canGrow(state) && random.nextDouble() < chance) {
                    level.setBlock(pos, state.setValue(FruitLeavesBlock.AGE, state.getValue(FruitLeavesBlock.AGE) + 1), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    // ------------------------------------------------------------ repousse des souches

    private static void processRegrows(ServerLevel level, ZoneData data) {
        if (data.regrows().isEmpty()) return;
        long now = level.getGameTime();
        Iterator<ZoneData.Regrow> it = data.regrows().iterator();
        boolean changed = false;
        while (it.hasNext()) {
            ZoneData.Regrow r = it.next();
            if (r.due() > now || !level.isLoaded(r.pos())) continue;
            it.remove();
            changed = true;
            if (!level.getBlockState(r.pos()).is(ModBlocks.TREE_STUMP.get())) continue; // déjà replantée / retirée
            ResourceLocation id = ResourceLocation.tryParse(r.sapling());
            Block sapling = id == null ? null : ForgeRegistries.BLOCKS.getValue(id);
            if (sapling == null || !ForgeRegistries.BLOCKS.containsKey(id)) continue;
            BlockState state = sapling.defaultBlockState();
            level.setBlock(r.pos(), state, Block.UPDATE_ALL);
            if (!state.canSurvive(level, r.pos())) {
                level.setBlock(r.pos(), ModBlocks.TREE_STUMP.get().defaultBlockState(), Block.UPDATE_ALL);
            } else {
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, r.pos().getX() + 0.5, r.pos().getY() + 0.4, r.pos().getZ() + 0.5,
                        8, 0.3, 0.3, 0.3, 0.0);
            }
        }
        if (changed) data.setDirty();
    }

    // ------------------------------------------------------------ affichage des contours

    public static void show(ServerPlayer player, Zone zone, int seconds) {
        SHOWING.removeIf(s -> s.player.getUUID().equals(player.getUUID()));
        SHOWING.add(new Showing(player, player.serverLevel(), zone, player.serverLevel().getGameTime() + seconds * 20L));
    }

    private static void tickShowing() {
        Iterator<Showing> it = SHOWING.iterator();
        while (it.hasNext()) {
            Showing s = it.next();
            if (s.player.isRemoved() || s.player.serverLevel() != s.level || s.level.getGameTime() > s.until) {
                it.remove();
                continue;
            }
            drawOutline(s.player, s.level, s.zone);
        }
    }

    private static void drawOutline(ServerPlayer player, ServerLevel level, Zone z) {
        double x1 = z.min.getX(), y1 = z.min.getY(), z1 = z.min.getZ();
        double x2 = z.max.getX() + 1, y2 = z.max.getY() + 1, z2 = z.max.getZ() + 1;
        double perimeter = 4 * ((x2 - x1) + (y2 - y1) + (z2 - z1));
        double step = Math.max(1.0, perimeter / 600.0);
        var particle = z.type == ZoneType.VERGER ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.FLAME;
        double[][] corners = {{x1, y1, z1}, {x2, y1, z1}, {x2, y1, z2}, {x1, y1, z2}};
        for (int i = 0; i < 4; i++) {
            double[] a = corners[i], b = corners[(i + 1) % 4];
            line(player, level, particle, a[0], y1, a[2], b[0], y1, b[2], step);
            line(player, level, particle, a[0], y2, a[2], b[0], y2, b[2], step);
            line(player, level, particle, a[0], y1, a[2], a[0], y2, a[2], step);
        }
    }

    private static void line(ServerPlayer player, ServerLevel level, net.minecraft.core.particles.ParticleOptions particle,
                             double ax, double ay, double az, double bx, double by, double bz, double step) {
        double len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        int n = Math.max(1, (int) (len / step));
        for (int i = 0; i <= n; i++) {
            double t = (double) i / n;
            level.sendParticles(player, particle, true, ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t,
                    1, 0, 0, 0, 0);
        }
    }

    public static void clearAll() {
        CURRENT_ZONE.clear();
        SHOWING.clear();
        ACTIVE_ORCHARDS.clear();
    }
}
