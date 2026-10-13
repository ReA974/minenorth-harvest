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
import net.minecraft.world.level.block.state.BlockBehaviour;
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
 * Moulin à vent (3 × 3 × 4). On moud comme avant : blé en main, clic droit maintenu sur n'importe quelle partie du moulin ;
 * la meule visible derrière la fenêtre et les ailes tournent plus vite pendant qu'on moud.
 */
public class WindmillBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public WindmillBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction facing = ctx.getHorizontalDirection().getOpposite();
        if (!MultiblockUtil.fits(ctx, WindmillLayout.PARTS, facing)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int i = 0; i < WindmillLayout.PARTS.length; i++) {
            level.setBlock(MultiblockUtil.partPos(pos, facing, WindmillLayout.PARTS[i]), ModBlocks.MILL_PART.get().defaultBlockState()
                    .setValue(WindmillPartBlock.FACING, facing).setValue(WindmillPartBlock.PART, i), Block.UPDATE_ALL);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return MultiblockUtil.shape(WindmillLayout.MASTER, state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WindmillBlockEntity(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        InteractionResult r = MillBlock.crank(MillBlock.DEFAULT_MILL, level, pos, player, hand);
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND && r.consumesAction()
                && MachineRecipes.find(player.getItemInHand(hand), HarvestConfig.MILL_RECIPES.get()) != null
                && level.getBlockEntity(pos) instanceof WindmillBlockEntity be) {
            be.turned(level);
        }
        return r;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            MultiblockUtil.removeParts(level, pos, state.getValue(FACING), WindmillLayout.PARTS, ModBlocks.MILL_PART.get());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
