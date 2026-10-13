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
import net.minecraft.world.level.LevelReader;
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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.core.BlockPos.MutableBlockPos;

import java.util.List;

/** Cellule annexe de la scierie (invisible) : renvoie les clics au bloc maître et le détruit avec elle. */
public class SawmillPartBlock extends Block {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, SawmillBlock.PARTS.length - 1);

    public SawmillPartBlock(Properties properties) {
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
        return RenderShape.INVISIBLE;
    }

    /** Étage du bas : plein. Étage du haut : seulement le portique de la lame (cellules du milieu et de l'arrière). */
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
        int[] o = SawmillBlock.PARTS[state.getValue(PART)];
        if (o[1] == 0) return Shapes.block();
        return o[2] >= 0 ? Shapes.block() : Shapes.empty();
    }

    @Override
    @SuppressWarnings("deprecation")
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state) {
        return new ItemStack(ModItems.SAWMILL.get());
    }

    private static BlockPos master(BlockPos pos, BlockState state) {
        return SawmillBlock.masterOf(pos, state.getValue(FACING), state.getValue(PART));
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        BlockPos mp = master(pos, state);
        BlockState ms = level.getBlockState(mp);
        if (!ms.is(ModBlocks.SAWMILL.get())) return InteractionResult.PASS;
        return ms.getBlock().use(ms, level, mp, player, hand, hit);
    }

    /** Casser une cellule annexe casse toute la scierie (le maître lâche l'objet, sauf en créatif). */
    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockPos mp = master(pos, state);
        if (!level.isClientSide && level.getBlockState(mp).is(ModBlocks.SAWMILL.get())) {
            level.destroyBlock(mp, !player.isCreative(), player);
        }
        super.playerWillDestroy(level, pos, state, player);
    }
}
