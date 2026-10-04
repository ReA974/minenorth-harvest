package com.minenorth_harvest.registry;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.block.FruitType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MineNorthHarvest.MODID);

    public static final RegistryObject<CreativeModeTab> MAIN = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.minenorth_harvest"))
            .icon(() -> new ItemStack(ModItems.ORANGE.get()))
            .displayItems((params, out) -> {
                for (FruitType type : FruitType.values()) out.accept(ModItems.SAPLINGS.get(type).get());
                for (FruitType type : FruitType.values()) out.accept(ModItems.LEAVES.get(type).get());
                out.accept(ModItems.ORANGE.get());
                out.accept(ModItems.LEMON.get());
                out.accept(ModItems.CHERRIES.get());
                out.accept(ModItems.PEAR.get());
                out.accept(ModItems.TREE_STUMP.get());
                out.accept(ModItems.ZONE_WAND.get());
            })
            .build());

    private ModCreativeTabs() {}
}
