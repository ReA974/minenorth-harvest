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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import com.minenorth_harvest.zone.PumpGame;
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

/** Cheval à bascule (bloc maître) : placé par les zones pétrole ; clic droit = mini-jeu de rythme qui fait sortir le pétrole brut. */
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

    /** Aucun joueur ne pose un cheval à bascule : ils sont placés par les zones pétrole (menu admin de la baguette de zone). */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        if (ctx.getPlayer() != null) ctx.getPlayer().displayClientMessage(Component.literal("§cLes chevaux à bascule se placent depuis le menu des zones pétrole (baguette de zone, accroupi + clic droit)."), true);
        return null;
    }

    /** Construit le cheval (bloc maître + cellules annexes) à cet emplacement si la place est libre. */
    public static boolean build(Level level, BlockPos pos, Direction facing) {
        if (pos.getY() + 3 >= level.getMaxBuildHeight() || pos.getY() < level.getMinBuildHeight()) return false;
        if (!replaceable(level, pos)) return false;
        for (int[] o : PumpjackLayout.PARTS) if (!replaceable(level, MultiblockUtil.partPos(pos, facing, o))) return false;
        level.setBlock(pos, ModBlocks.PUMPJACK.get().defaultBlockState().setValue(FACING, facing), Block.UPDATE_ALL);
        for (int i = 0; i < PumpjackLayout.PARTS.length; i++) {
            level.setBlock(MultiblockUtil.partPos(pos, facing, PumpjackLayout.PARTS[i]), ModBlocks.PUMPJACK_PART.get().defaultBlockState()
                    .setValue(PumpjackPartBlock.FACING, facing).setValue(PumpjackPartBlock.PART, i), Block.UPDATE_ALL);
        }
        return true;
    }

    private static boolean replaceable(Level level, BlockPos p) {
        BlockState st = level.getBlockState(p);
        return st.isAir() || st.canBeReplaced();
    }

    /** Démonte le cheval (bloc maître et cellules annexes) sans rien donner. */
    public static void demolish(Level level, BlockPos pos) {
        BlockState st = level.getBlockState(pos);
        if (!st.is(ModBlocks.PUMPJACK.get())) return;
        MultiblockUtil.removeParts(level, pos, st.getValue(FACING), PumpjackLayout.PARTS, ModBlocks.PUMPJACK_PART.get());
        level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
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
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp && level instanceof ServerLevel sl && level.getBlockEntity(pos) instanceof PumpjackBlockEntity be) {
            PumpGame.begin(sp, sl, pos, be);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            MultiblockUtil.removeParts(level, pos, state.getValue(FACING), PumpjackLayout.PARTS, ModBlocks.PUMPJACK_PART.get());
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
