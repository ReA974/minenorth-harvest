package com.minenorth_harvest.registry;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.block.FruitLeavesBlock;
import com.minenorth_harvest.block.FruitSaplingBlock;
import com.minenorth_harvest.block.FruitTreeGrower;
import com.minenorth_harvest.block.FruitType;
import com.minenorth_harvest.block.TreeStumpBlock;
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

    private ModBlocks() {}
}
