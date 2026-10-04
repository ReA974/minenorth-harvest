package com.minenorth_harvest.block;

import com.minenorth_harvest.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.grower.AbstractTreeGrower;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import org.jetbrains.annotations.Nullable;

/**
 * Fait pousser un arbre fruitier "verger" (tronc court, couronne ronde) en code,
 * sans dépendre d'un configured_feature JSON.
 */
public class FruitTreeGrower extends AbstractTreeGrower {
    private final FruitType type;

    public FruitTreeGrower(FruitType type) {
        this.type = type;
    }

    @Nullable
    @Override
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean hasFlowers) {
        return null; // non utilisé : growTree est redéfini
    }

    @Override
    public boolean growTree(ServerLevel level, ChunkGenerator generator, BlockPos pos, BlockState saplingState, RandomSource random) {
        int trunk = 4 + random.nextInt(2); // hauteur du tronc
        int top = trunk + 1;               // hauteur totale avec la couronne

        // Vérifie la place libre
        for (int y = 1; y <= top; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (y < trunk - 2 && (dx != 0 || dz != 0)) continue;
                    BlockState s = level.getBlockState(pos.offset(dx, y, dz));
                    if (!s.isAir() && !s.canBeReplaced() && !(s.getBlock() instanceof LeavesBlock)) {
                        return false;
                    }
                }
            }
            if (level.isOutsideBuildHeight(pos.above(y))) return false;
        }

        BlockState log = type.log().defaultBlockState();
        Block leavesBlock = ModBlocks.LEAVES.get(type).get();

        level.setBlock(pos, log, Block.UPDATE_ALL);
        for (int y = 1; y < trunk; y++) {
            level.setBlock(pos.above(y), log, Block.UPDATE_ALL);
        }

        // Couronne : 2 couches larges (rayon 2), puis 2 couches étroites (rayon 1)
        for (int y = trunk - 2; y <= top; y++) {
            int radius = y >= trunk ? 1 : 2;
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    boolean corner = Math.abs(dx) == radius && Math.abs(dz) == radius;
                    if (corner && (radius == 1 ? y == top : random.nextInt(3) != 0)) continue;
                    if (dx == 0 && dz == 0 && y < trunk) continue; // tronc
                    BlockPos lp = pos.offset(dx, y, dz);
                    BlockState existing = level.getBlockState(lp);
                    if (!existing.isAir() && !existing.canBeReplaced() && !(existing.getBlock() instanceof LeavesBlock)) continue;

                    int distToTrunk = Math.abs(dx) + Math.abs(dz) + Math.max(0, y - (trunk - 1));
                    BlockState leaves = leavesBlock.defaultBlockState()
                            .setValue(LeavesBlock.PERSISTENT, false)
                            .setValue(LeavesBlock.DISTANCE, Math.max(1, Math.min(7, distToTrunk)))
                            .setValue(FruitLeavesBlock.AGE, random.nextInt(2));
                    level.setBlock(lp, leaves, Block.UPDATE_ALL);
                }
            }
        }
        return true;
    }
}
