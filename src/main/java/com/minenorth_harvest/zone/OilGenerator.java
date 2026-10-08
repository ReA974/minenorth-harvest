package com.minenorth_harvest.zone;

import com.minenorth_harvest.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** Remplit une zone pétrole : une part (densité) de la roche devient gisement. Les chunks de la zone sont chargés au besoin. */
public final class OilGenerator {
    private OilGenerator() {}

    public static final long MAX_VOLUME = 2_000_000L;

    public record Result(int placed, int chunks, boolean tooBig) {}

    public static Result generate(ServerLevel level, Zone zone) {
        if (zone.volume() > MAX_VOLUME) return new Result(0, 0, true);
        RandomSource random = level.random;
        Block oil = ModBlocks.OIL_DEPOSIT.get();
        int yMin = Math.max(zone.min.getY(), level.getMinBuildHeight());
        int yMax = Math.min(zone.max.getY(), level.getMaxBuildHeight() - 1);
        int placed = 0, chunks = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = zone.min.getX() >> 4; cx <= zone.max.getX() >> 4; cx++) {
            for (int cz = zone.min.getZ() >> 4; cz <= zone.max.getZ() >> 4; cz++) {
                level.getChunk(cx, cz); // charge (ou génère) le chunk
                chunks++;
                int x0 = Math.max(zone.min.getX(), cx << 4), x1 = Math.min(zone.max.getX(), (cx << 4) + 15);
                int z0 = Math.max(zone.min.getZ(), cz << 4), z1 = Math.min(zone.max.getZ(), (cz << 4) + 15);
                for (int x = x0; x <= x1; x++) {
                    for (int z = z0; z <= z1; z++) {
                        for (int y = yMin; y <= yMax; y++) {
                            if (random.nextDouble() >= zone.density) continue;
                            pos.set(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (state.is(BlockTags.STONE_ORE_REPLACEABLES) || state.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)) {
                                level.setBlock(pos, oil.defaultBlockState(), Block.UPDATE_CLIENTS);
                                placed++;
                            }
                        }
                    }
                }
            }
        }
        return new Result(placed, chunks, false);
    }
}
