package com.minenorth_harvest.block;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Four à minerai (fonderie) : le bec est sur la face avant, le seau de coulée se pose devant. */
public class OreFurnaceBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    public OreFurnaceBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
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
        return new OreFurnaceBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModBlockEntities.ORE_FURNACE.get(), OreFurnaceBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (!(level.getBlockEntity(pos) instanceof OreFurnaceBlockEntity be)) return InteractionResult.PASS;
        if (level.isClientSide) return InteractionResult.SUCCESS;

        ItemStack held = player.getItemInHand(hand);
        if (!held.isEmpty()) {
            if (MachineRecipes.find(held, HarvestConfig.FOUNDRY_RECIPES.get()) != null) {
                int moved = be.insertOre(held);
                if (moved > 0) {
                    if (!player.getAbilities().instabuild) held.shrink(moved);
                    level.playSound(null, pos, SoundEvents.STONE_PLACE, SoundSource.BLOCKS, 0.8f, 0.7f);
                    return InteractionResult.CONSUME;
                }
            } else if (OreFurnaceBlockEntity.burnTimeOf(held) > 0) {
                int moved = be.insertFuel(held);
                if (moved > 0) {
                    if (!player.getAbilities().instabuild) held.shrink(moved);
                    level.playSound(null, pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.5f, 0.8f);
                    return InteractionResult.CONSUME;
                }
            }
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.furnace_hint"), true);
            return InteractionResult.CONSUME;
        }

        if (player.isShiftKeyDown()) { // reprendre ce qu'on a mis
            ItemStack back = !be.ore().isEmpty() ? be.takeOre() : be.takeFuel();
            while (!back.isEmpty()) {
                ItemStack part = back.split(Math.min(back.getCount(), back.getMaxStackSize()));
                if (!player.getInventory().add(part)) player.drop(part, false);
            }
            return InteractionResult.CONSUME;
        }
        if (!be.pending().isEmpty() && !be.hasBucket(level)) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.furnace_bucket"), true);
            return InteractionResult.CONSUME;
        }
        if (!be.pump(level)) {
            player.displayClientMessage(Component.translatable("message.minenorth_harvest.furnace_hint"), true);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof OreFurnaceBlockEntity be) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.ore().copy());
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.fuel().copy());
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, be.pending().copy());
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** Flammes au bec, fumée à la cheminée quand le four est allumé. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) return;
        Direction f = state.getValue(FACING);
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + f.getStepX() * 0.42 + (random.nextDouble() - 0.5) * 0.3,
                    pos.getY() + 0.4 + random.nextDouble() * 0.2, pos.getZ() + 0.5 + f.getStepZ() * 0.42 + (random.nextDouble() - 0.5) * 0.3, 0, 0.01, 0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.7, pos.getY() + 1.4, pos.getZ() + 0.7, 0, 0.06, 0);
        }
        if (random.nextInt(30) == 0) {
            level.playLocalSound(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, SoundEvents.BLASTFURNACE_FIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.8f, 1.0f, false);
        }
    }
}
