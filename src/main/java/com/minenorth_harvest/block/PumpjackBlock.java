package com.minenorth_harvest.block;

import com.minenorth_harvest.registry.ModBlockEntities;
import com.minenorth_harvest.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
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

/** Chevalet de pompage (bloc maître) : se pose SUR un gisement de pétrole ; clic droit à main nue pour récupérer le brut. */
public class PumpjackBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public PumpjackBlock(Properties properties) {
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
        if (!ctx.getLevel().getBlockState(ctx.getClickedPos().below()).is(ModBlocks.OIL_DEPOSIT.get())) {
            if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.translatable("message.minenorth_harvest.pumpjack_need_deposit"), true);
            return null;   // il faut un gisement de pétrole juste dessous
        }
        if (!MultiblockUtil.fits(ctx, PumpjackLayout.PARTS, facing)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int i = 0; i < PumpjackLayout.PARTS.length; i++) {
            level.setBlock(MultiblockUtil.partPos(pos, facing, PumpjackLayout.PARTS[i]), ModBlocks.PUMPJACK_PART.get().defaultBlockState()
                    .setValue(PumpjackPartBlock.FACING, facing).setValue(PumpjackPartBlock.PART, i), Block.UPDATE_ALL);
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
        return MultiblockUtil.shape(PumpjackLayout.MASTER, state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PumpjackBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.PUMPJACK.get(), PumpjackBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof PumpjackBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (be.dry()) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.pumpjack_dry"), true);
            return InteractionResult.CONSUME;
        }
        ItemStack out = be.take();
        if (out.isEmpty()) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.pumpjack_empty"), true);
            return InteractionResult.CONSUME;
        }
        Component name = out.getHoverName();
        int count = out.getCount();
        while (!out.isEmpty()) {
            ItemStack part = out.split(Math.min(out.getCount(), out.getMaxStackSize()));
            if (!player.getInventory().add(part)) player.drop(part, false);
        }
        level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.5f, 1.0f);
        player.displayClientMessage(Component.translatable("message.minenorth_harvest.mill_done", count, name), true);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof PumpjackBlockEntity be && be.stored() > 0) {
                net.minecraft.world.Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, be.take());
            }
            MultiblockUtil.removeParts(level, pos, state.getValue(FACING), PumpjackLayout.PARTS, ModBlocks.PUMPJACK_PART.get());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
