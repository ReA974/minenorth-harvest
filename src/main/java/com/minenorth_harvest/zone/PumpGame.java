package com.minenorth_harvest.zone;

import com.minenorth_harvest.block.PumpjackBlock;
import com.minenorth_harvest.block.PumpjackBlockEntity;
import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.network.ModNetwork;
import com.minenorth_harvest.network.S2CPump;
import com.minenorth_harvest.registry.ModItems;
import com.minenorth_harvest.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Mini-jeu du cheval à bascule, côté serveur (autoritaire). Le serveur choisit la graine, mesure le temps de la partie avec sa propre horloge,
 * n'accepte qu'un appui par manche, refuse les instants « dans le futur » ou trop anciens, juge chaque appui avec les mêmes règles que le client
 * (PumpMath), plafonne le gain à la réserve du puits et impose un délai entre deux parties. Les opérateurs sont soumis aux mêmes règles.
 */
public final class PumpGame {
    private PumpGame() {}

    private static final class Session {
        final BlockPos pos;
        final long seed;
        final int rounds;
        final long startNanos = System.nanoTime();
        final boolean[] done;
        int perfect, good, miss;

        Session(BlockPos pos, long seed, int rounds) {
            this.pos = pos.immutable();
            this.seed = seed;
            this.rounds = rounds;
            this.done = new boolean[rounds];
        }

        int elapsedMs() { return (int) ((System.nanoTime() - startNanos) / 1_000_000L); }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    /** Fin du délai entre deux parties (tick de jeu) par joueur. */
    private static final Map<UUID, Long> COOLDOWN = new HashMap<>();
    private static final double REACH = 5.0;
    /** Marge accordée au retard du réseau sur l'instant annoncé par le client. */
    private static final int LAG_MS = 1500, FUTURE_MS = 150;

    private static void bar(ServerPlayer p, String text) { p.displayClientMessage(Component.literal(text), true); }

    /** Clic droit sur le cheval : démarre une partie si tout est en règle. */
    public static void begin(ServerPlayer p, ServerLevel level, BlockPos pos, PumpjackBlockEntity be) {
        if (SESSIONS.containsKey(p.getUUID())) return;
        if (ZoneData.get(level).zoneAt(pos, ZoneType.PETROLE) == null) {
            p.displayClientMessage(Component.translatable("message.minenorth_harvest.pumpjack_no_zone"), true);
            return;
        }
        long now = level.getGameTime();
        Long cd = COOLDOWN.get(p.getUUID());
        if (cd != null && cd > now) {
            p.displayClientMessage(Component.translatable("message.minenorth_harvest.pumpjack_cooldown", (cd - now + 19) / 20), true);
            return;
        }
        if (be.isDry(now)) {
            p.displayClientMessage(Component.translatable("message.minenorth_harvest.pumpjack_dry", (be.dryRemainingTicks(now) + 19) / 20), true);
            return;
        }
        int rounds = HarvestConfig.PUMP_ROUNDS.get();
        long seed = level.random.nextLong();
        SESSIONS.put(p.getUUID(), new Session(pos, seed, rounds));
        be.workUntil(now + (PumpMath.totalMs(rounds) + 3000) / 50L);
        ModNetwork.sendTo(p, S2CPump.begin(pos, seed, rounds, be.reserve()));
    }

    /** Appui du joueur à l'instant ms (horloge du client, depuis le début de la partie). */
    public static void press(ServerPlayer p, int ms) {
        Session s = SESSIONS.get(p.getUUID());
        if (s == null) return;
        ServerLevel level = p.serverLevel();
        if (!valid(p, level, s)) { cancel(p); return; }
        int server = s.elapsedMs();
        if (ms > server + FUTURE_MS || ms < server - LAG_MS) return;   // instant impossible : l'appui est ignoré
        int round = PumpMath.roundAt(ms, s.rounds);
        if (round < 0 || s.done[round]) return;
        s.done[round] = true;
        int rating = PumpMath.rate(s.seed, round, ms);
        if (rating == PumpMath.PERFECT) s.perfect++; else if (rating == PumpMath.GOOD) s.good++; else s.miss++;
        fx(level, s.pos, rating);
        ModNetwork.sendTo(p, S2CPump.ack(round, rating));
    }

    /** Fin de partie (le joueur ferme l'écran ou la partie est terminée) : paie le pétrole. */
    public static void end(ServerPlayer p) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s == null) return;
        ServerLevel level = p.serverLevel();
        long now = level.getGameTime();
        COOLDOWN.put(p.getUUID(), now + HarvestConfig.PUMP_COOLDOWN.get() * 20L);
        int oil = 0;
        if (level.getBlockEntity(s.pos) instanceof PumpjackBlockEntity be && level.getBlockState(s.pos).is(ModBlocks.PUMPJACK.get())
                && p.distanceToSqr(s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5) <= (REACH + 3) * (REACH + 3) && !be.isDry(now)) {
            oil = Math.min(be.reserve(), s.perfect * HarvestConfig.PUMP_PERFECT_OIL.get() + s.good * HarvestConfig.PUMP_GOOD_OIL.get());
            if (oil > 0) be.consume(oil, now);
            be.workUntil(now + 40);
        }
        if (oil > 0) {
            ItemStack out = new ItemStack(ModItems.CRUDE_OIL.get(), oil);
            while (!out.isEmpty()) {
                ItemStack part = out.split(Math.min(out.getCount(), out.getMaxStackSize()));
                if (!p.getInventory().add(part)) p.drop(part, false);
            }
            level.playSound(null, s.pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 0.8f, 0.8f);
        }
        ModNetwork.sendTo(p, S2CPump.result(oil, s.perfect, s.good, s.miss));
        bar(p, oil > 0 ? "§6Pétrole brut obtenu : §f" + oil : "§7Pas une goutte… retentez votre chance !");
    }

    /** Partie interrompue sans paiement (déconnexion, mort, éloignement). */
    public static void cancel(ServerPlayer p) {
        Session s = SESSIONS.remove(p.getUUID());
        if (s == null) return;
        COOLDOWN.put(p.getUUID(), p.serverLevel().getGameTime() + HarvestConfig.PUMP_COOLDOWN.get() * 20L);
        ModNetwork.sendTo(p, S2CPump.abort());
    }

    public static void forget(UUID id) {
        SESSIONS.remove(id);
        COOLDOWN.remove(id);
    }

    private static boolean valid(ServerPlayer p, ServerLevel level, Session s) {
        return p.isAlive() && level.getBlockState(s.pos).is(ModBlocks.PUMPJACK.get())
                && p.distanceToSqr(s.pos.getX() + 0.5, s.pos.getY() + 0.5, s.pos.getZ() + 0.5) <= (REACH + 3) * (REACH + 3);
    }

    /** Tick serveur : parties expirées (le client n'a plus donné signe de vie) et joueurs éloignés. */
    public static void tick(MinecraftServer server) {
        if (SESSIONS.isEmpty()) return;
        for (UUID id : new ArrayList<>(SESSIONS.keySet())) {
            Session s = SESSIONS.get(id);
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p == null) { SESSIONS.remove(id); continue; }
            if (!valid(p, p.serverLevel(), s)) { cancel(p); continue; }
            if (s.elapsedMs() > PumpMath.totalMs(s.rounds) + 5000) end(p);
        }
    }

    /** Éclaboussures, bruits et balancier selon la note : le pétrole gicle du puits. */
    private static void fx(ServerLevel level, BlockPos master, int rating) {
        var dir = level.getBlockState(master).getValue(PumpjackBlock.FACING);
        var at = com.minenorth_harvest.block.MultiblockUtil.worldPoint(master, dir, 8, 28, 8);
        if (rating == PumpMath.PERFECT) {
            level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 28, 0.25, 0.5, 0.25, 0.12);
            level.sendParticles(ParticleTypes.FALLING_OBSIDIAN_TEAR, at.x, at.y + 0.4, at.z, 10, 0.3, 0.3, 0.3, 0.0);
            level.playSound(null, master, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.7f, 0.9f);
            level.playSound(null, master, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6f, 1.6f);
        } else if (rating == PumpMath.GOOD) {
            level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 10, 0.2, 0.3, 0.2, 0.06);
            level.playSound(null, master, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.6f, 0.8f);
        } else {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
            level.playSound(null, master, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.BLOCKS, 0.7f, 0.6f);
        }
    }
}
