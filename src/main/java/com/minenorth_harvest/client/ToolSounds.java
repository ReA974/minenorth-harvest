package com.minenorth_harvest.client;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.item.PowerTool;
import com.minenorth_harvest.registry.ModItems;
import com.minenorth_harvest.registry.ModSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Sons réalistes des outils à moteur, joués côté client uniquement (aucun effet sur le serveur ni sur les drops) :
 * - marteau-piqueur : démarrage court, boucle de martèlement pendant qu'on perce (clic droit maintenu), retombée à l'arrêt ;
 * - tronçonneuse 2 temps : lanceur puis ralenti « brap-brap » irrégulier, régime de coupe aigu quand on maintient le clic droit, ralenti, arrêt.
 * Chaque joueur proche a ses propres sons, qui suivent le joueur dans l'espace (portée {@link #RANGE} blocs) et s'arrêtent avec lui.
 * Sons synthétisés par tools/gen_tool_sounds.py (aucun échantillon protégé).
 */
@Mod.EventBusSubscriber(modid = MineNorthHarvest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ToolSounds {
    private ToolSounds() {}

    /** Portée d'écoute (blocs) et volume général. */
    private static final double RANGE = 40;
    private static final float VOLUME = 0.9f;
    /** La tronçonneuse reste au ralenti 5 s après le dernier coup de lame, puis s'arrête. */
    private static final int SAW_IDLE_TICKS = 100;

    private static final class Voice {
        boolean jack, saw;
        int jackTicks, sawTicks;
        long sawLastUse;
        Follow jackLoop, sawIdle, sawCut;
    }

    private static final Map<Integer, Voice> VOICES = new HashMap<>();

    /** Son en boucle qui suit un joueur ; le volume monte et descend en douceur vers sa cible. */
    private static final class Follow extends AbstractTickableSoundInstance {
        private final Player player;
        private float target;
        private boolean fading;

        Follow(SoundEvent event, Player player) {
            super(event, SoundSource.PLAYERS, SoundInstance.createUnseededRandom());
            this.player = player;
            this.looping = true;
            this.delay = 0;
            this.volume = 0.0f;
            this.attenuation = SoundInstance.Attenuation.LINEAR;
            this.relative = false;
            follow();
        }

        private void follow() {
            this.x = player.getX();
            this.y = player.getY() + 1.0;
            this.z = player.getZ();
        }

        @Override
        public void tick() {
            if (player.isRemoved() || !player.isAlive()) { stop(); return; }
            follow();
            volume += (target - volume) * 0.35f;
            if (fading && volume < 0.02f) stop();
        }

        void set(float targetVolume, float pitchValue) {
            if (fading) return;
            this.target = targetVolume * VOLUME;
            this.pitch = pitchValue;
        }

        void fadeOut() { fading = true; target = 0f; }

        @Override
        public boolean canStartSilent() { return true; }
    }

    private static void shot(Minecraft mc, SoundEvent event, Player p, float volume, float pitch) {
        mc.getSoundManager().play(new SimpleSoundInstance(event, SoundSource.PLAYERS, volume * VOLUME, pitch, SoundInstance.createUnseededRandom(),
                p.getX(), p.getY() + 1.0, p.getZ()));
    }

    private static Follow loop(Minecraft mc, SoundEvent event, Player p) {
        Follow f = new Follow(event, p);
        mc.getSoundManager().play(f);
        return f;
    }

    private static void stopAll(Voice v) {
        if (v.jackLoop != null) v.jackLoop.fadeOut();
        if (v.sawIdle != null) v.sawIdle.fadeOut();
        if (v.sawCut != null) v.sawCut.fadeOut();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            for (Voice v : VOICES.values()) stopAll(v);
            VOICES.clear();
            return;
        }
        long now = mc.level.getGameTime();
        Set<Integer> seen = new HashSet<>();
        for (Player p : mc.level.players()) {
            if (p.distanceToSqr(mc.player) > RANGE * RANGE) continue;
            ItemStack main = p.getMainHandItem();
            boolean hasJack = main.is(ModItems.JACKHAMMER.get()) && PowerTool.fuel(main) > 0;
            boolean hasSaw = main.is(ModItems.CHAINSAW.get()) && PowerTool.fuel(main) > 0;
            boolean using = p.isUsingItem() && p.getUseItem().getItem() == main.getItem();
            Voice v = VOICES.get(p.getId());
            if (v == null && !((hasJack || hasSaw) && using)) continue;
            if (v == null) { v = new Voice(); VOICES.put(p.getId(), v); }
            seen.add(p.getId());
            tickJack(mc, p, v, hasJack && using);
            tickSaw(mc, p, v, hasSaw, using, now);
        }
        Iterator<Map.Entry<Integer, Voice>> it = VOICES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Integer, Voice> e = it.next();
            Voice v = e.getValue();
            if (!seen.contains(e.getKey())) { stopAll(v); it.remove(); continue; }
            if (!v.jack && !v.saw) it.remove();
        }
    }

    private static void tickJack(Minecraft mc, Player p, Voice v, boolean run) {
        if (run && !v.jack) {
            v.jack = true;
            v.jackTicks = 0;
            shot(mc, ModSounds.JACKHAMMER_START.get(), p, 1.0f, 1.0f);
            v.jackLoop = loop(mc, ModSounds.JACKHAMMER_LOOP.get(), p);
        } else if (!run && v.jack) {
            v.jack = false;
            if (v.jackLoop != null) v.jackLoop.fadeOut();
            shot(mc, ModSounds.JACKHAMMER_END.get(), p, 1.0f, 1.0f);
            return;
        }
        if (v.jack && v.jackLoop != null) {
            v.jackTicks++;
            // la boucle prend le relais quand le démarrage a fini de monter en régime (~0,45 s)
            v.jackLoop.set(v.jackTicks < 9 ? 0f : 1.0f, 1.0f);
        }
    }

    private static void tickSaw(Minecraft mc, Player p, Voice v, boolean holds, boolean using, long now) {
        if (holds && using) v.sawLastUse = now;
        boolean engine = holds && (using || (v.saw && now - v.sawLastUse < SAW_IDLE_TICKS));
        if (engine && !v.saw) {
            v.saw = true;
            v.sawTicks = 0;
            v.sawLastUse = now;
            shot(mc, ModSounds.CHAINSAW_START.get(), p, 1.0f, 1.0f);
            v.sawIdle = loop(mc, ModSounds.CHAINSAW_IDLE.get(), p);
            v.sawCut = loop(mc, ModSounds.CHAINSAW_CUT.get(), p);
        } else if (!engine && v.saw) {
            v.saw = false;
            if (v.sawIdle != null) v.sawIdle.fadeOut();
            if (v.sawCut != null) v.sawCut.fadeOut();
            shot(mc, ModSounds.CHAINSAW_STOP.get(), p, 1.0f, 1.0f);
            return;
        }
        if (!v.saw) return;
        v.sawTicks++;
        boolean starting = v.sawTicks < 26;                    // le lanceur et les premiers calages se jouent seuls
        boolean biting = using && aimsAtLog(mc, p);            // la chaîne mord dans le bois : plein régime
        if (v.sawIdle != null) v.sawIdle.set(starting ? 0f : using ? 0.22f : 0.85f, 1.0f);
        if (v.sawCut != null) v.sawCut.set(starting ? 0f : using ? (biting ? 1.0f : 0.8f) : 0f, biting ? 1.0f : 0.92f);
    }

    /** Pour le joueur local : vise-t-il une bûche ? (les autres joueurs : on suppose que oui pendant qu'ils maintiennent le clic) */
    private static boolean aimsAtLog(Minecraft mc, Player p) {
        if (p != mc.player) return true;
        HitResult hit = mc.hitResult;
        return hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK && mc.level.getBlockState(b.getBlockPos()).is(BlockTags.LOGS);
    }
}
