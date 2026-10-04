package com.minenorth_harvest.config;

import net.minecraft.world.level.Level;

/**
 * Valeurs de config dont le client a besoin (prédiction de casse, poudre d'os).
 * Côté serveur : lecture directe de la config. Côté client : valeurs reçues du serveur.
 */
public final class SyncedConfig {
    private SyncedConfig() {}

    public static volatile double clientManualLogSpeed = 0.4;
    public static volatile boolean clientChopEnabled = true;
    public static volatile boolean clientBonemealFruits = true;

    public static double manualLogSpeed(Level level) {
        return level.isClientSide ? clientManualLogSpeed : HarvestConfig.MANUAL_LOG_SPEED.get();
    }

    public static boolean chopEnabled(Level level) {
        return level.isClientSide ? clientChopEnabled : HarvestConfig.CHOP_ENABLED.get();
    }

    public static boolean bonemealFruits(boolean isClient) {
        return isClient ? clientBonemealFruits : HarvestConfig.BONEMEAL_FRUITS.get();
    }
}
