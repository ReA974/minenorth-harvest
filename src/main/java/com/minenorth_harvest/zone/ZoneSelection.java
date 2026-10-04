package com.minenorth_harvest.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Sélection en cours de chaque admin (baguette de zone), côté serveur, non sauvegardée. */
public final class ZoneSelection {
    private ZoneSelection() {}

    public static final class Sel {
        public ResourceKey<Level> dimension;
        @Nullable public BlockPos pos1;
        @Nullable public BlockPos pos2;

        public boolean complete() { return pos1 != null && pos2 != null; }
    }

    private static final Map<UUID, Sel> SELECTIONS = new HashMap<>();

    @Nullable
    public static Sel get(ServerPlayer player) {
        Sel s = SELECTIONS.get(player.getUUID());
        if (s != null && s.dimension != player.level().dimension()) return null;
        return s;
    }

    public static void set(ServerPlayer player, BlockPos pos, boolean first) {
        Sel s = SELECTIONS.computeIfAbsent(player.getUUID(), u -> new Sel());
        if (s.dimension != player.level().dimension()) {
            s.dimension = player.level().dimension();
            s.pos1 = null;
            s.pos2 = null;
        }
        BlockPos p = pos.immutable();
        if (first) {
            if (p.equals(s.pos1)) return; // évite le spam du clic gauche maintenu
            s.pos1 = p;
        } else {
            if (p.equals(s.pos2)) return;
            s.pos2 = p;
        }
        if (s.complete()) {
            Zone preview = new Zone("selection", ZoneType.BUCHERON, s.pos1, s.pos2);
            player.displayClientMessage(Component.translatable(first
                    ? "message.minenorth_harvest.wand_pos1_size" : "message.minenorth_harvest.wand_pos2_size",
                    p.toShortString(), preview.volume()), false);
            ZoneManager.show(player, preview, 10);
        } else {
            player.displayClientMessage(Component.translatable(first
                    ? "message.minenorth_harvest.wand_pos1" : "message.minenorth_harvest.wand_pos2", p.toShortString()), false);
        }
    }

    public static void clear(UUID id) {
        SELECTIONS.remove(id);
    }
}
