package com.minenorth_harvest.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Évier à eau illimitée : clic droit avec un seau vide = seau d'eau, avec une bouteille vide = bouteille d'eau. */
public class SinkBlock extends HorizontalDirectionalBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    private static final VoxelShape SHAPE = Block.box(0, 0, 1, 16, 15, 15);

    public SinkBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.QUARTZ).strength(2.0f, 6.0f).sound(SoundType.STONE).noOcclusion());
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) { b.add(FACING); }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c) { return SHAPE; }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        ItemStack result;
        if (held.is(Items.BUCKET)) result = new ItemStack(Items.WATER_BUCKET);
        else if (held.is(Items.GLASS_BOTTLE)) result = PotionUtils.setPotion(new ItemStack(Items.POTION), Potions.WATER);
        else {
            if (!level.isClientSide && !held.isEmpty()) player.displayClientMessage(Component.literal("§7Tenez un seau ou une bouteille vide."), true);
            return held.isEmpty() ? InteractionResult.PASS : InteractionResult.SUCCESS;
        }
        if (!level.isClientSide) {
            // Un seul objet est rempli (comme un chaudron) ; le reste du tas est gardé.
            if (!player.getAbilities().instabuild) held.shrink(1);
            if (held.isEmpty() && !player.getAbilities().instabuild) player.setItemInHand(hand, result);
            else if (!player.getInventory().add(result)) player.drop(result, false);
            level.playSound(null, pos, result.is(Items.WATER_BUCKET) ? SoundEvents.BUCKET_FILL : SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1f, 1f);
            if (level instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.SPLASH, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.05);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
