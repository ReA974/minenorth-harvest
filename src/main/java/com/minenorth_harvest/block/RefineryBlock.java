package com.minenorth_harvest.block;

import com.minenorth_harvest.registry.ModBlockEntities;
import com.minenorth_harvest.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
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

/**
 * Raffinerie (bloc maître). Interactions selon l'endroit visé :
 * brut en main (n'importe où) = remplir le réservoir ; combustible en main (n'importe où) = alimenter le four ;
 * pupitre (avant droit) : main nue = coup de pompe, accroupi = ouvrir la vanne ; collecteur (arrière droit) : main nue = récupérer l'essence.
 */
public class RefineryBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RefineryBlock(Properties properties) {
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
        if (!MultiblockUtil.fits(ctx, RefineryLayout.PARTS, facing)) return null;
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int i = 0; i < RefineryLayout.PARTS.length; i++) {
            level.setBlock(MultiblockUtil.partPos(pos, facing, RefineryLayout.PARTS[i]), ModBlocks.REFINERY_PART.get().defaultBlockState()
                    .setValue(RefineryPartBlock.FACING, facing).setValue(RefineryPartBlock.PART, i), Block.UPDATE_ALL);
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
        return MultiblockUtil.shape(RefineryLayout.MASTER, state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new RefineryBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != ModBlockEntities.REFINERY.get()) return null;
        return level.isClientSide ? (BlockEntityTicker<T>) (BlockEntityTicker<RefineryBlockEntity>) RefineryBlockEntity::clientTick
                : (BlockEntityTicker<T>) (BlockEntityTicker<RefineryBlockEntity>) RefineryBlockEntity::serverTick;
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof RefineryBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            int moved = be.insertInput(held);
            if (moved == 0) moved = be.insertFuel(held);
            if (moved > 0) {
                if (!player.getAbilities().instabuild) held.shrink(moved);
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 0.7f, 0.8f);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.refinery_hint"), true);
            return InteractionResult.CONSUME;
        }

        int[] off = MultiblockUtil.offsetOf(pos, state.getValue(FACING), RefineryLayout.PARTS, hit.getBlockPos());
        boolean panel = off != null && off[0] == 2 && off[2] == 0;
        boolean collector = off != null && off[0] == 2 && off[2] == 1;
        if (collector || (off != null && off[0] == 2 && !be.output().isEmpty() && !panel)) {
            if (be.output().isEmpty()) {
                player.displayClientMessage(Component.translatable("message.minenorth_harvest.refinery_empty"), true);
                return InteractionResult.CONSUME;
            }
            ItemStack out = be.takeOutput();
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
        if (panel) {
            if (player.isShiftKeyDown()) be.vent(level); else be.pump(level);
            player.displayClientMessage(RefineryBlockEntity.status(be), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.minenorth_harvest.refinery_hint"), true);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (level.getBlockEntity(pos) instanceof RefineryBlockEntity be) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, be.input().copy());
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, be.fuel().copy());
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5, be.output().copy());
            }
            MultiblockUtil.removeParts(level, pos, state.getValue(FACING), RefineryLayout.PARTS, ModBlocks.REFINERY_PART.get());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
