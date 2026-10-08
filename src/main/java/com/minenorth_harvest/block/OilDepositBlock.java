package com.minenorth_harvest.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Gisement de pétrole : se mine comme un minerai, mais le brut est très inflammable.
 * Une flamme, de la lave ou un briquet à proximité le fait exploser (et propage l'incendie aux gisements voisins).
 */
public class OilDepositBlock extends Block {
    public OilDepositBlock(Properties properties) {
        super(properties);
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return 100;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction face) {
        return 60;
    }

    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) {
        if (level.isClientSide) return;
        level.removeBlock(pos, false);
        level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2.5f, true, Level.ExplosionInteraction.TNT);
    }

    /** Suintement noir sur les faces exposées. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(12) != 0) return;
        Direction face = Direction.getRandom(random);
        BlockPos side = pos.relative(face);
        if (level.getBlockState(side).isSolidRender(level, side)) return;
        double x = pos.getX() + (face.getStepX() == 0 ? random.nextDouble() : 0.5 + face.getStepX() * 0.55);
        double y = pos.getY() + (face.getStepY() == 0 ? random.nextDouble() : 0.5 + face.getStepY() * 0.55);
        double z = pos.getZ() + (face.getStepZ() == 0 ? random.nextDouble() : 0.5 + face.getStepZ() * 0.55);
        level.addParticle(ParticleTypes.DRIPPING_OBSIDIAN_TEAR, x, y, z, 0, 0, 0);
    }
}
