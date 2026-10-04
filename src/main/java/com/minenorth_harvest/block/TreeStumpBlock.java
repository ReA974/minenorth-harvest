package com.minenorth_harvest.block;

import com.minenorth_harvest.data.HarvestData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Souche laissée après un abattage. Clic droit avec une pousse = replantation (réduit la dette écologique).
 */
public class TreeStumpBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 7, 14);

    public TreeStumpBlock(Properties properties) {
        super(properties);
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = player.getItemInHand(hand);
        if (!stack.is(ItemTags.SAPLINGS) || !(stack.getItem() instanceof BlockItem blockItem)) {
            if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
                player.displayClientMessage(Component.translatable("message.minenorth_harvest.stump_hint"), true);
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockState sapling = blockItem.getBlock().defaultBlockState();
        level.setBlock(pos, sapling, Block.UPDATE_ALL);
        if (!sapling.canSurvive(level, pos)) {
            level.setBlock(pos, state, Block.UPDATE_ALL);
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.replant_fail"), true);
            return InteractionResult.FAIL;
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);

        HarvestData.onReplant(player);
        level.playSound(null, pos, SoundEvents.GRASS_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        ServerLevel server = (ServerLevel) level;
        server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.4, pos.getZ() + 0.5,
                10, 0.4, 0.3, 0.4, 0.0);

        player.displayClientMessage(Component.translatable("message.minenorth_harvest.replanted",
                HarvestData.getEcoDebt(player)), true);
        return InteractionResult.CONSUME;
    }
}
