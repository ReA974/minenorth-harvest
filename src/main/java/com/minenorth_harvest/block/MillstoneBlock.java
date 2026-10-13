package com.minenorth_harvest.block;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Moulin à meule (3 × 3 × 2), comme un moulin à huile : une grosse meule de pierre roule dans une cuve circulaire autour d'un axe
 * central. Même usage que le moulin : blé (ou autre entrée de la config [mill]) en main, clic droit maintenu sur n'importe quelle
 * partie ; la meule tourne doucement au repos et plus vite pendant qu'on moud.
 */
public class MillstoneBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public MillstoneBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) { builder.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        return MultiblockUtil.fits(ctx, MillstoneLayout.PARTS, facing) ? defaultBlockState().setValue(FACING, facing) : null;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int i = 0; i < MillstoneLayout.PARTS.length; i++) {
            level.setBlock(MultiblockUtil.partPos(pos, facing, MillstoneLayout.PARTS[i]), ModBlocks.MILLSTONE_PART.get().defaultBlockState()
                    .setValue(MillstonePartBlock.FACING, facing).setValue(MillstonePartBlock.PART, i), Block.UPDATE_ALL);
        }
    }

    /** Le décor est dessiné par le moteur de rendu (la meule tourne) : le bloc lui-même n'a pas de modèle. */
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) { return RenderShape.INVISIBLE; }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return MultiblockUtil.shape(MillstoneLayout.MASTER, state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) { return List.of(new ItemStack(this)); }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) { return new MillstoneBlockEntity(pos, state); }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult r = MillBlock.crank(MillBlock.DEFAULT_MILL, level, pos, player, hand);
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND && r.consumesAction()
                && MachineRecipes.find(player.getItemInHand(hand), HarvestConfig.MILL_RECIPES.get()) != null
                && level.getBlockEntity(pos) instanceof MillstoneBlockEntity be) {
            be.turned(level);
        }
        return r;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            MultiblockUtil.removeParts(level, pos, state.getValue(FACING), MillstoneLayout.PARTS, ModBlocks.MILLSTONE_PART.get());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
