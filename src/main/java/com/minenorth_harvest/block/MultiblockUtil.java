package com.minenorth_harvest.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/** Outils communs aux machines multibloc : cellules annexes placées autour du bloc maître selon l'orientation. */
public final class MultiblockUtil {
    private MultiblockUtil() {}

    public static BlockPos partPos(BlockPos master, Direction facing, int[] o) {
        return master.offset(new BlockPos(o[0], o[1], o[2]).rotate(SawmillBlock.rotationFor(facing)));
    }

    public static BlockPos masterOf(BlockPos part, Direction facing, int[] o) {
        return part.subtract(new BlockPos(o[0], o[1], o[2]).rotate(SawmillBlock.rotationFor(facing)));
    }

    /** Décalage (repère « nord ») de la cellule {@code cell} par rapport au maître, ou null si ce n'est pas une cellule de la machine. */
    @Nullable
    public static int[] offsetOf(BlockPos master, Direction facing, int[][] parts, BlockPos cell) {
        if (cell.equals(master)) return new int[]{0, 0, 0};
        for (int[] o : parts) if (partPos(master, facing, o).equals(cell)) return o;
        return null;
    }

    public static boolean fits(BlockPlaceContext ctx, int[][] parts, Direction facing) {
        Level level = ctx.getLevel();
        for (int[] o : parts) {
            BlockPos p = partPos(ctx.getClickedPos(), facing, o);
            if (p.getY() >= level.getMaxBuildHeight() || p.getY() < level.getMinBuildHeight() || !level.getBlockState(p).canBeReplaced(ctx)) return false;
        }
        return true;
    }

    public static void removeParts(Level level, BlockPos master, Direction facing, int[][] parts, Block partBlock) {
        for (int[] o : parts) {
            BlockPos p = partPos(master, facing, o);
            if (level.getBlockState(p).is(partBlock)) level.setBlock(p, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Point du monde d'un point du modèle (pixels, repère du maître orienté au nord). */
    public static Vec3 worldPoint(BlockPos master, Direction f, double px, double py, double pz) {
        double dx = px / 16.0 - 0.5, dz = pz / 16.0 - 0.5, rx, rz;
        switch (f) {
            case EAST -> { rx = -dz; rz = dx; }
            case SOUTH -> { rx = -dx; rz = -dz; }
            case WEST -> { rx = dz; rz = -dx; }
            default -> { rx = dx; rz = dz; }
        }
        return new Vec3(master.getX() + 0.5 + rx, master.getY() + py / 16.0, master.getZ() + 0.5 + rz);
    }

    private static final Map<String, VoxelShape> CACHE = new HashMap<>();

    /** Forme de collision d'une cellule : boîtes (pixels locaux, machine orientée au nord) tournées selon l'orientation. */
    public static VoxelShape shape(float[][] boxes, Direction facing) {
        return CACHE.computeIfAbsent(System.identityHashCode(boxes) + "/" + facing, k -> {
            VoxelShape s = Shapes.empty();
            for (float[] b : boxes) {
                double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
                double ax0, ax1, az0, az1;
                switch (facing) {
                    case EAST -> { ax0 = 16 - z1; ax1 = 16 - z0; az0 = x0; az1 = x1; }
                    case SOUTH -> { ax0 = 16 - x1; ax1 = 16 - x0; az0 = 16 - z1; az1 = 16 - z0; }
                    case WEST -> { ax0 = z0; ax1 = z1; az0 = 16 - x1; az1 = 16 - x0; }
                    default -> { ax0 = x0; ax1 = x1; az0 = z0; az1 = z1; }
                }
                s = Shapes.or(s, Block.box(Math.max(0, ax0), Math.max(0, y0), Math.max(0, az0), Math.min(16, ax1), Math.min(16, y1), Math.min(16, az1)));
            }
            return s.isEmpty() ? Shapes.block() : s;
        });
    }

    public static BlockState air() { return Blocks.AIR.defaultBlockState(); }
}
