package com.minenorth_harvest.chop;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;

/** Détecte un arbre naturel à partir d'une bûche. */
public final class TreeScanner {
    private TreeScanner() {}

    public record Tree(List<BlockPos> logs, BlockPos base, int naturalLeaves, boolean tooBig, @Nullable Block leaves) {}

    public static Tree scan(Level level, BlockPos start, int maxLogs) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<BlockPos> logs = new ArrayList<>();
        queue.add(start.immutable());
        visited.add(start.immutable());

        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            if (!level.isLoaded(p) || !level.getBlockState(p).is(BlockTags.LOGS)) continue;
            logs.add(p);
            if (logs.size() > maxLogs) {
                return new Tree(logs, start, 0, true, null);
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = p.offset(dx, dy, dz);
                        if (visited.add(n)) queue.add(n);
                    }
                }
            }
        }

        // Base = bûche la plus basse, la plus proche du point cliqué
        BlockPos base = logs.stream()
                .min(Comparator.<BlockPos>comparingInt(BlockPos::getY)
                        .thenComparingDouble(b -> b.distSqr(new BlockPos(start.getX(), b.getY(), start.getZ()))))
                .orElse(start);

        // Feuilles naturelles (non posées par un joueur) au contact des bûches
        Set<BlockPos> leaves = new HashSet<>();
        Map<Block, Integer> leafCount = new HashMap<>();
        for (BlockPos log : logs) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos n = log.offset(dx, dy, dz);
                        if (leaves.contains(n)) continue;
                        BlockState s = level.getBlockState(n);
                        if (s.getBlock() instanceof LeavesBlock && s.hasProperty(LeavesBlock.PERSISTENT)
                                && !s.getValue(LeavesBlock.PERSISTENT)) {
                            if (leaves.add(n.immutable())) leafCount.merge(s.getBlock(), 1, Integer::sum);
                        }
                    }
                }
            }
            if (leaves.size() > 256) break;
        }

        logs.sort(Comparator.comparingInt(BlockPos::getY));
        Block dominant = leafCount.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        return new Tree(logs, base, leaves.size(), false, dominant);
    }

    /** Heuristique légère : y a-t-il des feuilles naturelles au-dessus/autour de cette bûche ? */
    public static boolean looksNatural(Level level, BlockPos pos) {
        for (int dy = 0; dy <= 7; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockState s = level.getBlockState(pos.offset(dx, dy, dz));
                    if (s.getBlock() instanceof LeavesBlock && s.hasProperty(LeavesBlock.PERSISTENT)
                            && !s.getValue(LeavesBlock.PERSISTENT)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
