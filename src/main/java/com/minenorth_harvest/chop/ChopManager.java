package com.minenorth_harvest.chop;

import com.minenorth_harvest.block.FruitLeavesBlock;
import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.job.HarvestData;
import com.minenorth_harvest.network.ModNetwork;
import com.minenorth_harvest.network.S2CChopState;
import com.minenorth_harvest.registry.ModBlocks;
import com.minenorth_harvest.zone.Zone;
import com.minenorth_harvest.zone.ZoneData;
import com.minenorth_harvest.zone.ZoneType;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.level.BlockEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Logique serveur du mini-jeu d'abattage. */
public final class ChopManager {
    private ChopManager() {}

    private static final Map<UUID, ChopSession> SESSIONS = new HashMap<>();
    private static final List<FellingTask> FELLING = new ArrayList<>();

    public static boolean inSession(ServerPlayer player) {
        return SESSIONS.containsKey(player.getUUID());
    }

    public static boolean isAxe(ItemStack stack) {
        return !stack.isEmpty() && stack.canPerformAction(ToolActions.AXE_DIG);
    }

    // ------------------------------------------------------------------ démarrage

    public static void tryStart(ServerPlayer player, BlockPos pos) {
        if (!HarvestConfig.CHOP_ENABLED.get()) return;
        if (inSession(player)) {
            cancel(player, true);
            return;
        }
        ServerLevel level = player.serverLevel();
        if (!level.isLoaded(pos) || player.distanceToSqr(pos.getCenter()) > 36.0) return;
        if (player.isSpectator()) return;
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.LOGS)) return;
        if (!isAxe(player.getMainHandItem())) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.need_axe"), true);
            return;
        }
        for (ChopSession other : SESSIONS.values()) {
            if (other.logs.contains(pos)) {
                player.displayClientMessage(Component.translatable("message.minenorth_harvest.already_chopping"), true);
                return;
            }
        }
        for (FellingTask t : FELLING) {
            if (t.contains(pos)) return;
        }

        TreeScanner.Tree tree = TreeScanner.scan(level, pos, HarvestConfig.MAX_TREE_LOGS.get());
        if (tree.tooBig()) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.tree_too_big"), true);
            return;
        }
        if (tree.naturalLeaves() < HarvestConfig.MIN_LEAVES.get()) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.not_a_tree"), true);
            return;
        }
        // Zones de bûcheronnage
        ZoneData zones = ZoneData.get(level);
        Zone zone = zones.zoneAt(tree.base(), ZoneType.BUCHERON);
        if (zone == null) zone = zones.zoneAt(pos, ZoneType.BUCHERON);
        if (zone == null && HarvestConfig.REQUIRE_ZONE.get()) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.outside_zone"), true);
            return;
        }
        boolean bypass = zone != null && HarvestConfig.ZONES_BYPASS_PROTECTION.get();

        // Protection : si un mod/plugin de claim refuse la casse de la base, on refuse (sauf zone qui fait foi).
        if (!bypass && MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, tree.base(), level.getBlockState(tree.base()), player))) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.protected"), true);
            return;
        }

        boolean exhausted = HarvestData.isForestExhausted(player);

        int required = HarvestConfig.BASE_HITS.get() + tree.logs().size() / HarvestConfig.LOGS_PER_EXTRA_HIT.get();
        required = Math.min(required, HarvestConfig.MAX_HITS.get());
        if (exhausted) required += HarvestConfig.ECO_EXTRA_HITS.get();
        required = Math.max(1, required);

        ChopSession s = new ChopSession(player, pos.immutable(), tree.base(), tree.logs(), required, exhausted);
        s.zone = zone;
        s.zoneWidth = (float) Math.min(0.9, HarvestConfig.ZONE_WIDTH.get());
        s.perfectWidth = (float) Math.min(s.zoneWidth, HarvestConfig.PERFECT_WIDTH.get());
        s.period = HarvestConfig.CURSOR_PERIOD.get();
        long now = level.getGameTime();
        s.startTick = now;
        s.lastActivityTick = now;
        moveZone(s, level.random);
        SESSIONS.put(player.getUUID(), s);

        level.playSound(null, pos, SoundEvents.AXE_STRIP, SoundSource.PLAYERS, 0.8f, 0.8f);
        player.displayClientMessage(Component.translatable(exhausted
                ? "message.minenorth_harvest.start_exhausted"
                : "message.minenorth_harvest.start"), true);
        sync(s);
    }

    private static void moveZone(ChopSession s, RandomSource random) {
        float half = s.zoneWidth / 2f;
        float min = half + 0.04f;
        float max = 1f - half - 0.04f;
        float c = max <= min ? 0.5f : min + random.nextFloat() * (max - min);
        // évite que la zone reste au même endroit
        if (Math.abs(c - s.zoneCenter) < 0.12f) c = Mth.clamp(1f - c, min, Math.max(min, max));
        s.zoneCenter = c;
    }

    // ------------------------------------------------------------------ coups

    public static void swing(ServerPlayer player, float clientCursor) {
        ChopSession s = SESSIONS.get(player.getUUID());
        if (s == null) return;
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();

        if (now - s.lastSwingTick < HarvestConfig.SWING_COOLDOWN.get()) return;
        if (!validate(s)) {
            cancel(player, true);
            return;
        }

        // Validation anti-triche légère : la position envoyée doit correspondre au curseur
        // à un instant récent (tolère la latence). Sinon on prend la valeur serveur.
        float cursor = s.cursorAt(now);
        if (clientCursor >= 0f && clientCursor <= 1f) {
            for (double t = now - 8; t <= now + 1; t += 0.25) {
                if (Math.abs(s.cursorAt(t) - clientCursor) < 0.025f) {
                    cursor = clientCursor;
                    break;
                }
            }
        }

        s.lastSwingTick = now;
        s.lastActivityTick = now;
        s.swings++;
        byte result = ChopMath.evaluate(cursor, s.zoneCenter, s.zoneWidth, s.perfectWidth);
        s.lastResult = result;
        s.resultSeq++;

        ItemStack axe = player.getMainHandItem();
        int damage = 1 + (result == ChopMath.RESULT_MISS ? HarvestConfig.MISS_EXTRA_DAMAGE.get() : 0);
        if (!player.getAbilities().instabuild) {
            axe.hurtAndBreak(damage, player, p -> p.broadcastBreakEvent(InteractionHand.MAIN_HAND));
        }
        player.swing(InteractionHand.MAIN_HAND, true);

        BlockState targetState = level.getBlockState(s.target);
        double x = s.target.getX() + 0.5, y = s.target.getY() + 0.5, z = s.target.getZ() + 0.5;
        switch (result) {
            case ChopMath.RESULT_PERFECT -> {
                s.progress += 2;
                s.combo++;
                s.perfects++;
                level.playSound(null, s.target, SoundEvents.AXE_STRIP, SoundSource.PLAYERS, 1.0f, 1.2f);
                level.playSound(null, s.target, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.5f, 0.8f + 0.1f * Math.min(s.combo, 6));
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, targetState), x, y, z, 20, 0.3, 0.3, 0.3, 0.15);
                level.sendParticles(ParticleTypes.CRIT, x, y, z, 10, 0.3, 0.3, 0.3, 0.2);
            }
            case ChopMath.RESULT_GOOD -> {
                s.progress += 1;
                s.combo = 0;
                level.playSound(null, s.target, SoundEvents.AXE_STRIP, SoundSource.PLAYERS, 1.0f, 1.0f);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, targetState), x, y, z, 10, 0.3, 0.3, 0.3, 0.1);
            }
            default -> {
                s.combo = 0;
                level.playSound(null, s.target, SoundEvents.WOOD_HIT, SoundSource.PLAYERS, 1.0f, 0.6f);
            }
        }

        if (s.progress >= s.required) {
            finish(s);
            return;
        }

        if (result != ChopMath.RESULT_MISS) {
            // le curseur continue sa course (pas de retour à gauche) mais accélère
            int newPeriod = Math.max(HarvestConfig.MIN_CURSOR_PERIOD.get(), s.period - 2);
            s.startTick = ChopMath.rebase(now, s.startTick, s.period, newPeriod);
            s.period = newPeriod;
            moveZone(s, level.random);
        }
        sync(s);
    }

    private static boolean validate(ChopSession s) {
        ServerPlayer p = s.player;
        if (p.isRemoved() || !p.isAlive()) return false;
        if (!isAxe(p.getMainHandItem())) return false;
        if (p.distanceToSqr(s.target.getCenter()) > 64.0) return false;
        return p.serverLevel().getBlockState(s.target).is(BlockTags.LOGS);
    }

    private static void finish(ChopSession s) {
        SESSIONS.remove(s.player.getUUID());
        ServerPlayer player = s.player;
        ServerLevel level = player.serverLevel();
        RandomSource random = level.random;

        int bonus = 0;
        if (!s.ecoMalus) {
            for (int i = 0; i < s.perfects; i++) {
                if (random.nextDouble() < HarvestConfig.BONUS_LOG_PER_PERFECT.get()) bonus++;
            }
        }

        // Re-scan pour prendre l'état actuel de l'arbre
        TreeScanner.Tree tree = TreeScanner.scan(level, s.target, HarvestConfig.MAX_TREE_LOGS.get());
        List<BlockPos> logs = tree.tooBig() ? s.logs : tree.logs();

        HarvestData.onTreeFelled(player);
        level.playSound(null, s.target, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.BLOCKS, 0.7f, 0.6f);
        boolean bypass = s.zone != null && HarvestConfig.ZONES_BYPASS_PROTECTION.get();
        String regrowSapling = s.zone != null && s.zone.regrow ? saplingFor(tree, level.getBlockState(s.base)) : null;
        FELLING.add(new FellingTask(level, player, logs, tree.tooBig() ? s.base : tree.base(), bonus, bypass, regrowSapling));

        Component msg = HarvestData.isForestExhausted(player)
                ? Component.translatable("message.minenorth_harvest.timber_exhausted", bonus)
                : Component.translatable("message.minenorth_harvest.timber", bonus);
        player.displayClientMessage(msg, true);
        ModNetwork.sendTo(player, S2CChopState.inactive());
    }

    /** Devine la pousse à replanter : feuillage fruitier, sinon x_leaves / x_log -> x_sapling, sinon chêne. */
    private static String saplingFor(TreeScanner.Tree tree, BlockState baseLog) {
        if (tree.leaves() instanceof FruitLeavesBlock fruit) {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(ModBlocks.SAPLINGS.get(fruit.getFruitType()).get());
            if (id != null) return id.toString();
        }
        List<ResourceLocation> candidates = new ArrayList<>();
        if (tree.leaves() != null) {
            ResourceLocation l = ForgeRegistries.BLOCKS.getKey(tree.leaves());
            if (l != null) candidates.add(new ResourceLocation(l.getNamespace(), l.getPath().replace("_leaves", "_sapling")));
        }
        ResourceLocation logId = ForgeRegistries.BLOCKS.getKey(baseLog.getBlock());
        if (logId != null) {
            String path = logId.getPath().replace("stripped_", "").replace("_wood", "_log");
            candidates.add(new ResourceLocation(logId.getNamespace(), path.replace("_log", "_sapling")));
        }
        for (ResourceLocation c : candidates) {
            if (ForgeRegistries.BLOCKS.containsKey(c)) return c.toString();
        }
        return "minecraft:oak_sapling";
    }

    public static void cancel(ServerPlayer player, boolean notify) {
        ChopSession s = SESSIONS.remove(player.getUUID());
        if (s == null) return;
        if (notify && !player.isRemoved()) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.cancelled"), true);
        }
        if (!player.isRemoved()) ModNetwork.sendTo(player, S2CChopState.inactive());
    }

    private static void sync(ChopSession s) {
        ModNetwork.sendTo(s.player, new S2CChopState(true, s.progress, s.required, s.combo,
                s.zoneCenter, s.zoneWidth, s.perfectWidth, s.period, s.startTick, s.lastResult, s.resultSeq, s.ecoMalus));
    }

    // ------------------------------------------------------------------ tick serveur

    public static void tick() {
        Iterator<FellingTask> it = FELLING.iterator();
        while (it.hasNext()) {
            if (it.next().tick()) it.remove();
        }

        if (SESSIONS.isEmpty()) return;
        int timeout = HarvestConfig.SESSION_TIMEOUT.get() * 20;
        List<ServerPlayer> toCancel = new ArrayList<>();
        for (ChopSession s : SESSIONS.values()) {
            long now = s.player.serverLevel().getGameTime();
            if (!validate(s) || now - s.lastActivityTick > timeout) toCancel.add(s.player);
        }
        for (ServerPlayer p : toCancel) cancel(p, true);
    }

    public static void onLogout(ServerPlayer player) {
        SESSIONS.remove(player.getUUID());
    }

    public static void clearAll() {
        SESSIONS.clear();
        FELLING.clear();
    }
}
