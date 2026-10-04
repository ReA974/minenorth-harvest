package com.minenorth_harvest.chop;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

/** Session de mini-jeu d'un joueur (côté serveur). */
public class ChopSession {
    public final ServerPlayer player;
    public final BlockPos target;
    public final BlockPos base;
    public final List<BlockPos> logs;
    public final int required;
    public final boolean ecoMalus;
    /** Zone de bûcheronnage où a lieu l'abattage (peut être null si requireZone=false). */
    public com.minenorth_harvest.zone.Zone zone;

    public int progress;
    public int combo;
    public int perfects;
    public int swings;
    public float zoneCenter;
    public float zoneWidth;
    public float perfectWidth;
    public int period;
    public double startTick;
    public long lastSwingTick = Long.MIN_VALUE / 2;
    public long lastActivityTick;
    public byte lastResult = ChopMath.RESULT_NONE;
    public int resultSeq;

    public ChopSession(ServerPlayer player, BlockPos target, BlockPos base, List<BlockPos> logs, int required, boolean ecoMalus) {
        this.player = player;
        this.target = target;
        this.base = base;
        this.logs = logs;
        this.required = required;
        this.ecoMalus = ecoMalus;
    }

    public float cursorAt(double time) {
        return ChopMath.cursor(time, startTick, period);
    }
}
