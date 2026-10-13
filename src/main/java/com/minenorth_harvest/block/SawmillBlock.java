package com.minenorth_harvest.block;

import com.minenorth_harvest.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Scierie : clic droit avec des bûches pour les poser sur le chariot, la scie les débite toute seule,
 * clic droit à main nue pour récupérer les planches (accroupi : reprendre les bûches non sciées).
 */
public class SawmillBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE = Shapes.block();

    /**
     * Cellules occupées en plus du bloc maître (décalages x, y, z pour une scierie orientée au nord) :
     * 2 de large (x), 2 de haut (y), 3 de long (z : une cellule devant, le maître, une derrière).
     */
    public static final int[][] PARTS = {
            {0, 0, -1}, {0, 0, 1}, {1, 0, -1}, {1, 0, 0}, {1, 0, 1},
            {0, 1, -1}, {0, 1, 0}, {0, 1, 1}, {1, 1, -1}, {1, 1, 0}, {1, 1, 1}};

    public static Rotation rotationFor(Direction facing) {
        return switch (facing) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    public static BlockPos partPos(BlockPos master, Direction facing, int index) {
        int[] o = PARTS[index];
        return master.offset(new BlockPos(o[0], o[1], o[2]).rotate(rotationFor(facing)));
    }

    public static BlockPos masterOf(BlockPos part, Direction facing, int index) {
        int[] o = PARTS[index];
        return part.subtract(new BlockPos(o[0], o[1], o[2]).rotate(rotationFor(facing)));
    }

    public SawmillBlock(Properties properties) {
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
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        for (int i = 0; i < PARTS.length; i++) {
            BlockPos p = partPos(pos, facing, i);
            if (p.getY() >= level.getMaxBuildHeight() || !level.getBlockState(p).canBeReplaced(ctx)) return null; // pas la place
        }
        return defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable net.minecraft.world.entity.LivingEntity placer, ItemStack stack) {
        Direction facing = state.getValue(FACING);
        for (int i = 0; i < PARTS.length; i++) {
            level.setBlock(partPos(pos, facing, i), com.minenorth_harvest.registry.ModBlocks.SAWMILL_PART.get().defaultBlockState()
                    .setValue(SawmillPartBlock.FACING, facing).setValue(SawmillPartBlock.PART, i), Block.UPDATE_ALL);
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
        return SHAPE;
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of(new ItemStack(this));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SawmillBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.SAWMILL.get(), SawmillBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof SawmillBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            int moved = be.insert(held);
            if (moved > 0) {
                if (!player.getAbilities().instabuild) held.shrink(moved);
                level.playSound(null, pos, SoundEvents.WOOD_PLACE, SoundSource.BLOCKS, 0.9f, 0.8f);
                return InteractionResult.CONSUME;
            }
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.sawmill_hint"), true);
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown() && !be.input().isEmpty()) {
            give(player, be.takeInput());
            return InteractionResult.CONSUME;
        }
        if (!be.output().isEmpty()) {
            ItemStack out = be.takeOutput();
            Component name = out.getHoverName();
            int count = out.getCount();
            give(player, out);
            level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.1f);
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.mill_done", count, name), true);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.translatable("message.minenorth_harvest.sawmill_hint"), true);
        return InteractionResult.CONSUME;
    }

    private static void give(Player player, ItemStack stack) {
        while (!stack.isEmpty()) {
            ItemStack part = stack.split(Math.min(stack.getCount(), stack.getMaxStackSize()));
            if (!player.getInventory().add(part)) player.drop(part, false);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof SawmillBlockEntity be) {
            dropAll(level, pos, be.input());
            dropAll(level, pos, be.output());
        }
        if (!state.is(newState.getBlock())) { // le maître part : toutes les cellules annexes disparaissent
            Direction facing = state.getValue(FACING);
            for (int i = 0; i < PARTS.length; i++) {
                BlockPos p = partPos(pos, facing, i);
                if (level.getBlockState(p).is(com.minenorth_harvest.registry.ModBlocks.SAWMILL_PART.get())) {
                    level.setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    private static void dropAll(Level level, BlockPos pos, ItemStack stack) {
        ItemStack rest = stack.copy();
        while (!rest.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.8, pos.getZ() + 0.5,
                    rest.split(Math.min(rest.getCount(), rest.getMaxStackSize())));
        }
    }
}
