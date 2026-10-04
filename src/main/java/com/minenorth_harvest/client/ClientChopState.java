package com.minenorth_harvest.client;

import com.minenorth_harvest.chop.ChopMath;
import com.minenorth_harvest.network.S2CChopState;

/** État du mini-jeu côté client (rempli par le serveur). */
public final class ClientChopState {
    private ClientChopState() {}

    public static boolean active;
    public static int progress, required, combo;
    public static float zoneCenter, zoneWidth, perfectWidth;
    public static int period;
    public static double startTick;
    public static byte lastResult;
    public static int resultSeq;
    public static long resultClientTick = -1000;
    public static boolean ecoMalus;

    /** Compteur de ticks client (pour les animations). */
    public static long clientTicks;
    public static long lastSwingClientTick = -1000;

    public static void apply(S2CChopState m) {
        active = m.active();
        if (!active) {
            resultSeq = 0;
            return;
        }
        progress = m.progress();
        required = m.required();
        combo = m.combo();
        zoneCenter = m.zoneCenter();
        zoneWidth = m.zoneWidth();
        perfectWidth = m.perfectWidth();
        period = m.period();
        startTick = m.startTick();
        ecoMalus = m.ecoMalus();
        if (m.resultSeq() != resultSeq) {
            resultSeq = m.resultSeq();
            lastResult = m.lastResult();
            resultClientTick = clientTicks;
        }
    }

    public static float cursor(double gameTime) {
        return ChopMath.cursor(gameTime, startTick, period);
    }

    public static void reset() {
        active = false;
        resultSeq = 0;
    }
}
