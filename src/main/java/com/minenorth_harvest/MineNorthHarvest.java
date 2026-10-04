package com.minenorth_harvest;

import com.minenorth_harvest.block.FruitType;
import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.network.ModNetwork;
import com.minenorth_harvest.network.S2CSyncConfig;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.server.ServerLifecycleHooks;
import com.minenorth_harvest.registry.ModBlocks;
import com.minenorth_harvest.registry.ModCreativeTabs;
import com.minenorth_harvest.registry.ModItems;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(MineNorthHarvest.MODID)
public class MineNorthHarvest {
    public static final String MODID = "minenorth_harvest";

    public MineNorthHarvest() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModBlocks.BLOCKS.register(modBus);
        ModItems.ITEMS.register(modBus);
        ModCreativeTabs.TABS.register(modBus);

        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, HarvestConfig.SPEC, "minenorth_harvest-common.toml");

        modBus.addListener(this::commonSetup);
        modBus.addListener(this::onConfigReload);
    }

    /** Config modifiée à chaud : on renvoie les valeurs utiles aux joueurs connectés. */
    private void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != HarvestConfig.SPEC) return;
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> ModNetwork.sendToAll(S2CSyncConfig.fromConfig()));
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        ModNetwork.register();
        event.enqueueWork(() -> {
            for (FruitType type : FruitType.values()) {
                ComposterBlock.COMPOSTABLES.put(ModBlocks.LEAVES.get(type).get().asItem(), 0.3f);
                ComposterBlock.COMPOSTABLES.put(ModBlocks.SAPLINGS.get(type).get().asItem(), 0.3f);
            }
            ComposterBlock.COMPOSTABLES.put(ModItems.ORANGE.get(), 0.65f);
            ComposterBlock.COMPOSTABLES.put(ModItems.LEMON.get(), 0.65f);
            ComposterBlock.COMPOSTABLES.put(ModItems.CHERRIES.get(), 0.65f);
            ComposterBlock.COMPOSTABLES.put(ModItems.PEAR.get(), 0.65f);
        });
    }
}
