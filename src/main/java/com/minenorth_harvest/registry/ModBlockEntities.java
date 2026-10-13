package com.minenorth_harvest.registry;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.block.CastingBucketBlockEntity;
import com.minenorth_harvest.block.OreFurnaceBlockEntity;
import com.minenorth_harvest.block.SawmillBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> TYPES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MineNorthHarvest.MODID);

    public static final RegistryObject<BlockEntityType<SawmillBlockEntity>> SAWMILL = TYPES.register("sawmill",
            () -> BlockEntityType.Builder.of(SawmillBlockEntity::new, ModBlocks.SAWMILL.get()).build(null));
    public static final RegistryObject<BlockEntityType<OreFurnaceBlockEntity>> ORE_FURNACE = TYPES.register("foundry",
            () -> BlockEntityType.Builder.of(OreFurnaceBlockEntity::new, ModBlocks.FOUNDRY.get()).build(null));
    public static final RegistryObject<BlockEntityType<CastingBucketBlockEntity>> CASTING_BUCKET = TYPES.register("casting_bucket",
            () -> BlockEntityType.Builder.of(CastingBucketBlockEntity::new, ModBlocks.CASTING_BUCKET.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.minenorth_harvest.block.RefineryBlockEntity>> REFINERY = TYPES.register("refinery",
            () -> BlockEntityType.Builder.of(com.minenorth_harvest.block.RefineryBlockEntity::new, ModBlocks.REFINERY.get()).build(null));
    public static final RegistryObject<BlockEntityType<com.minenorth_harvest.block.PumpjackBlockEntity>> PUMPJACK = TYPES.register("pumpjack",
            () -> BlockEntityType.Builder.of(com.minenorth_harvest.block.PumpjackBlockEntity::new, ModBlocks.PUMPJACK.get()).build(null));

    public static final RegistryObject<BlockEntityType<com.minenorth_harvest.block.WindmillBlockEntity>> MILL = TYPES.register("mill",
            () -> BlockEntityType.Builder.of(com.minenorth_harvest.block.WindmillBlockEntity::new, ModBlocks.MILL.get()).build(null));

    private ModBlockEntities() {}
}
