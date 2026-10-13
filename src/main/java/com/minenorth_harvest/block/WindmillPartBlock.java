package com.minenorth_harvest.block;

import com.minenorth_harvest.registry.ModBlocks;
import com.minenorth_harvest.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;

/** Cellule annexe du moulin : porte son morceau du modèle 3D, renvoie les clics au bloc maître et le détruit avec elle. */
public class WindmillPartBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, WindmillLayout.PARTS.length - 1);

    public WindmillPartBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        return MultiblockUtil.shape(WindmillLayout.BOXES[state.getValue(PART)], state.getValue(FACING));
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.MILL.get());
    }

    private static BlockPos master(BlockPos pos, BlockState state) {
        return MultiblockUtil.masterOf(pos, state.getValue(FACING), WindmillLayout.PARTS[state.getValue(PART)]);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos mp = master(pos, state);
        BlockState ms = level.getBlockState(mp);
        if (!ms.is(ModBlocks.MILL.get())) return InteractionResult.PASS;
        return ms.getBlock().use(ms, level, mp, player, hand, hit);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos mp = master(pos, state);
        if (!level.isClientSide && level.getBlockState(mp).is(ModBlocks.MILL.get())) {
            level.destroyBlock(mp, !player.isCreative(), player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
