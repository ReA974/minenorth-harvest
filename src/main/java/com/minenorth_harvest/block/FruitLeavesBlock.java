package com.minenorth_harvest.block;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.config.SyncedConfig;
import com.minenorth_harvest.data.HarvestData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Feuillage fruitier : 0 = feuilles, 1 = en fleurs, 2 = fruits verts, 3 = fruits mûrs.
 * Clic droit sur un feuillage mûr = cueillette, puis le cycle recommence.
 */
public class FruitLeavesBlock extends LeavesBlock implements BonemealableBlock {
    public static final IntegerProperty AGE = BlockStateProperties.AGE_3;
    public static final int RIPE = 3;

    private final FruitType type;

    public FruitLeavesBlock(FruitType type, Properties properties) {
        super(properties);
        this.type = type;
        this.registerDefaultState(this.defaultBlockState().setValue(AGE, 0));
    }

    public FruitType getFruitType() {
        return type;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(AGE);
    }

    /** Peut encore mûrir (feuillage vivant, pas encore mûr). */
    public static boolean canGrow(BlockState state) {
        return state.getBlock() instanceof FruitLeavesBlock && !isDecaying(state) && state.getValue(AGE) < RIPE;
    }

    private static boolean isDecaying(BlockState state) {
        return !state.getValue(PERSISTENT) && state.getValue(DISTANCE) == 7;
    }

    @Override
    public boolean isRandomlyTicking(BlockState state) {
        return true;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (isDecaying(state)) {
            super.randomTick(state, level, pos, random);
            return;
        }
        int age = state.getValue(AGE);
        if (age < RIPE) {
            if (random.nextDouble() < HarvestConfig.FRUIT_GROWTH_CHANCE.get()) {
                level.setBlock(pos, state.setValue(AGE, age + 1), Block.UPDATE_CLIENTS);
            }
        } else if (random.nextDouble() < HarvestConfig.FRUIT_FALL_CHANCE.get()) {
            BlockPos below = pos.below();
            if (level.getBlockState(below).isAir()) {
                Block.popResource(level, below, new ItemStack(type.fruit()));
                level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        int age = state.getValue(AGE);
        if (age < RIPE) {
            if (age == 2 && hand == InteractionHand.MAIN_HAND && player.getItemInHand(hand).isEmpty()) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("message.minenorth_harvest.unripe"), true);
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        int total = harvest(level, pos, state, player, hit.getDirection());
        HarvestData.addFruits(player, total);
        return InteractionResult.CONSUME;
    }

    /** Cueille un feuillage mûr, renvoie le nombre de fruits donnés. */
    private int harvest(Level level, BlockPos pos, BlockState state, Player player, Direction face) {
        FruitLeavesBlock block = (FruitLeavesBlock) state.getBlock();
        RandomSource random = level.getRandom();
        int min = HarvestConfig.HARVEST_MIN.get();
        int max = Math.max(min, HarvestConfig.HARVEST_MAX.get());
        int count = min + random.nextInt(max - min + 1);

        if (count > 0) {
            ItemStack stack = new ItemStack(block.type.fruit(), count);
            if (face != null) {
                Block.popResourceFromFace(level, pos, face, stack);
            } else {
                Block.popResource(level, pos, stack);
            }
        }
        level.playSound(null, pos, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS,
                1.0f, 0.8f + random.nextFloat() * 0.4f);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.HAPPY_VILLAGER, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    4, 0.35, 0.35, 0.35, 0.0);
        }
        level.setBlock(pos, state.setValue(AGE, 0), Block.UPDATE_CLIENTS);
        level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, state));
        return count;
    }

    // ---- Poudre d'os ----

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient) {
        return SyncedConfig.bonemealFruits(isClient) && state.getValue(AGE) < RIPE;
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state) {
        return true;
    }

    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(AGE, Math.min(RIPE, state.getValue(AGE) + 1)), Block.UPDATE_CLIENTS);
    }

    // ---- Inflammable comme des feuilles ----

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 60;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        return 30;
    }
}
