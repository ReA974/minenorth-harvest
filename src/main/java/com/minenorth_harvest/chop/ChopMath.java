package com.minenorth_harvest.chop;

/** Calculs partagés client/serveur pour le mini-jeu. */
public final class ChopMath {
    private ChopMath() {}

    public static final byte RESULT_NONE = 0;
    public static final byte RESULT_MISS = 1;
    public static final byte RESULT_GOOD = 2;
    public static final byte RESULT_PERFECT = 3;

    /** Position du curseur (0..1) : aller-retour linéaire, part de 0 à startTick. */
    public static float cursor(double time, double startTick, int period) {
        if (period <= 0) return 0f;
        double elapsed = Math.max(0, time - startTick);
        double phase = (elapsed % period) / period;
        return (float) (phase < 0.5 ? phase * 2.0 : 2.0 - phase * 2.0);
    }

    /** Nouvelle origine pour garder la position du curseur quand la période change. */
    public static double rebase(double now, double startTick, int oldPeriod, int newPeriod) {
        if (oldPeriod <= 0 || oldPeriod == newPeriod) return startTick;
        double elapsed = Math.max(0, now - startTick) % oldPeriod;
        double phase = elapsed / oldPeriod;
        return now - phase * newPeriod;
    }

    public static byte evaluate(float cursor, float zoneCenter, float zoneWidth, float perfectWidth) {
        float d = Math.abs(cursor - zoneCenter);
        if (d <= perfectWidth / 2f) return RESULT_PERFECT;
        if (d <= zoneWidth / 2f) return RESULT_GOOD;
        return RESULT_MISS;
    }
}
