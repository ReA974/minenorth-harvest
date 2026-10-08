package com.minenorth_harvest.registry;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.block.FruitLeavesBlock;
import com.minenorth_harvest.block.FruitSaplingBlock;
import com.minenorth_harvest.block.FruitTreeGrower;
import com.minenorth_harvest.block.FruitType;
import com.minenorth_harvest.block.MillBlock;
import com.minenorth_harvest.block.OilDepositBlock;
import com.minenorth_harvest.block.TreeStumpBlock;
import com.minenorth_harvest.config.HarvestConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModBlocks {
    private static final VoxelShape REFINERY_SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 5, 16),
            Block.box(2, 5, 2, 14, 12, 14),
            Block.box(6, 12, 6, 10, 16, 10));

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MineNorthHarvest.MODID);

    public static final Map<FruitType, RegistryObject<Block>> LEAVES = new EnumMap<>(FruitType.class);
    public static final Map<FruitType, RegistryObject<Block>> SAPLINGS = new EnumMap<>(FruitType.class);

    static {
        for (FruitType type : FruitType.values()) {
            Block leavesBase = type == FruitType.CHERRY ? Blocks.CHERRY_LEAVES : Blocks.OAK_LEAVES;
            LEAVES.put(type, BLOCKS.register(type.id() + "_leaves",
                    () -> new FruitLeavesBlock(type, BlockBehaviour.Properties.copy(leavesBase))));
            SAPLINGS.put(type, BLOCKS.register(type.id() + "_sapling",
                    () -> new FruitSaplingBlock(new FruitTreeGrower(type), BlockBehaviour.Properties.copy(Blocks.OAK_SAPLING))));
        }
    }

    public static final RegistryObject<Block> TREE_STUMP = BLOCKS.register("tree_stump",
            () -> new TreeStumpBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.WOOD)
                    .strength(2.5f)
                    .sound(SoundType.WOOD)
                    .noOcclusion()));

    public static final RegistryObject<Block> MILL = BLOCKS.register("mill",
            () -> new MillBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.STONE)
                    .strength(2.0f, 6.0f)
                    .sound(SoundType.STONE)
                    .noOcclusion()));

    public static final RegistryObject<Block> OIL_DEPOSIT = BLOCKS.register("oil_deposit",
            () -> new OilDepositBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.STONE)));

    public static final RegistryObject<Block> REFINERY = BLOCKS.register("refinery",
            () -> new MillBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0f, 6.0f)
                    .requiresCorrectToolForDrops()
                    .sound(SoundType.METAL)
                    .noOcclusion(),
                    new MillBlock.Machine(REFINERY_SHAPE, HarvestConfig.REFINERY_RECIPES::get, HarvestConfig.REFINERY_TURNS::get,
                            "message.minenorth_harvest.refinery_hint", "message.minenorth_harvest.refinery_progress", SoundEvents.BREWING_STAND_BREW, ParticleTypes.LARGE_SMOKE)));

    private ModBlocks() {}
}
