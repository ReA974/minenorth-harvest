package com.minenorth_harvest.zone;

import java.util.SplittableRandom;

/**
 * Règles du mini-jeu de pompage, partagées par le serveur (qui juge) et le client (qui affiche) : une manche = un curseur qui traverse la barre de
 * gauche à droite ; il faut appuyer quand il passe sur la cible. Les cibles dérivent d'une graine donnée par le serveur ; le temps est compté en
 * millisecondes depuis le début de la partie (compte à rebours compris).
 */
public final class PumpMath {
    private PumpMath() {}

    /** Compte à rebours avant la première manche. */
    public static final int LEAD_MS = 1800;
    /** Demi-largeur (en fraction de la barre) du coup parfait et du coup bien. */
    public static final double PERFECT_W = 0.06, GOOD_W = 0.16;
    public static final int MISS = 0, GOOD = 1, PERFECT = 2;

    /** Durée de la manche i : de plus en plus rapide. */
    public static int roundMs(int i) { return Math.max(620, 1150 - 60 * i); }

    /** Instant (ms depuis le début de la partie) où commence la manche i. */
    public static int roundStart(int i) {
        int t = LEAD_MS;
        for (int k = 0; k < i; k++) t += roundMs(k);
        return t;
    }

    public static int totalMs(int rounds) { return roundStart(rounds); }

    /** Position de la cible de la manche i, entre 0,30 et 0,85 de la barre. */
    public static double target(long seed, int i) {
        return 0.30 + 0.55 * new SplittableRandom(seed * 31L + i * 0x9E3779B97F4A7C15L).nextDouble();
    }

    /** Manche en cours à l'instant ms, ou -1 (compte à rebours / partie finie). */
    public static int roundAt(int ms, int rounds) {
        if (ms < LEAD_MS) return -1;
        for (int i = 0; i < rounds; i++) if (ms < roundStart(i + 1)) return i;
        return -1;
    }

    /** Position du curseur (0 à 1) de la manche i à l'instant ms. */
    public static double cursor(int ms, int i) {
        return Math.max(0.0, Math.min(1.0, (ms - roundStart(i)) / (double) roundMs(i)));
    }

    /** Note d'un appui à l'instant ms (manche i). */
    public static int rate(long seed, int i, int ms) {
        double d = Math.abs(cursor(ms, i) - target(seed, i));
        return d <= PERFECT_W ? PERFECT : d <= GOOD_W ? GOOD : MISS;
    }
}
