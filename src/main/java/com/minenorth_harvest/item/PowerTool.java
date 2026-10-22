package com.minenorth_harvest.item;

import com.minenorth_harvest.registry.ModItems;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.player.Player;

/**
 * Outils à moteur (tronçonneuse, marteau-piqueur) : réservoir d'essence dans le NBT de l'objet (« Fuel », 0 à {@link #CAPACITY}).
 * Un bidon d'essence (item gasoline) de l'inventaire remplit le réservoir. Réservoir vide : l'outil est lent et ne s'anime plus.
 */
public final class PowerTool {
    private PowerTool() {}

    public static final int CAPACITY = 100;
    /** Unités consommées : par seconde de moteur en marche, par bloc cassé, par arbre abattu à la tronçonneuse. */
    public static final int PER_SECOND = 1, PER_BLOCK = 1, PER_TREE = 5;

    public static int fuel(ItemStack st) {
        return st.hasTag() ? st.getTag().getInt("Fuel") : 0;
    }

    public static void setFuel(ItemStack st, int v) {
        st.getOrCreateTag().putInt("Fuel", Math.max(0, Math.min(CAPACITY, v)));
    }

    public static ItemStack full(ItemStack st) {
        setFuel(st, CAPACITY);
        return st;
    }

    /** Remplit le réservoir avec une essence de l'inventaire. Renvoie faux s'il est déjà plein ou s'il n'y a pas d'essence. */
    public static boolean refill(ServerPlayer p, ItemStack tool) {
        if (fuel(tool) >= CAPACITY) return false;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ModItems.GASOLINE.get())) {
                s.shrink(1);
                setFuel(tool, CAPACITY);
                p.serverLevel().playSound(null, p.blockPosition(), net.minecraft.sounds.SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 0.8f, 1.3f);
                p.displayClientMessage(Component.literal("§aRéservoir plein."), true);
                return true;
            }
        }
        p.displayClientMessage(Component.literal("§cPas d'essence dans votre inventaire."), true);
        return false;
    }

    /** Prélève n unités ; si le réservoir est trop bas, tente d'abord de le remplir. Faux si impossible. */
    public static boolean consume(ServerPlayer p, ItemStack tool, int n) {
        if (fuel(tool) < n && !refillQuiet(p, tool)) return false;
        setFuel(tool, fuel(tool) - n);
        return true;
    }

    private static boolean refillQuiet(ServerPlayer p, ItemStack tool) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.is(ModItems.GASOLINE.get())) {
                s.shrink(1);
                setFuel(tool, Math.min(CAPACITY, fuel(tool) + CAPACITY));
                return true;
            }
        }
        return false;
    }

    /**
     * Particules du moteur en marche (serveur : vues de tous les joueurs proches). Les BRUITS ne sont plus joués ici : chaque client joue les vrais sons
     * du marteau-piqueur et de la tronçonneuse (ToolSounds), qui suivent le joueur. Les paramètres de son sont ignorés (compatibilité des appels).
     */
    public static void fx(Player p, SoundEvent a, SoundEvent b, float pitchA, ParticleOptions particle) {
        if (!(p.level() instanceof ServerLevel level)) return;
        long t = level.getGameTime();
        if (t % 4 == 0) {
            var look = p.getLookAngle();
            level.sendParticles(particle, p.getX() + look.x * 0.9, p.getEyeY() - 0.5 + look.y * 0.9, p.getZ() + look.z * 0.9, 2, 0.1, 0.1, 0.1, 0.02);
        }
    }
}
