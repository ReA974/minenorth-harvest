package com.minenorth_harvest.client;

import com.minenorth_harvest.MineNorthHarvest;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MineNorthHarvest.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientModEvents {
    private ClientModEvents() {}

    @SubscribeEvent
    public static void registerRenderers(net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(com.minenorth_harvest.registry.ModBlockEntities.SAWMILL.get(), SawmillRenderer::new);
        event.registerBlockEntityRenderer(com.minenorth_harvest.registry.ModBlockEntities.ORE_FURNACE.get(), OreFurnaceRenderer::new);
        event.registerBlockEntityRenderer(com.minenorth_harvest.registry.ModBlockEntities.CASTING_BUCKET.get(), CastingBucketRenderer::new);
        event.registerBlockEntityRenderer(com.minenorth_harvest.registry.ModBlockEntities.REFINERY.get(), RefineryRenderer::new);
        event.registerBlockEntityRenderer(com.minenorth_harvest.registry.ModBlockEntities.PUMPJACK.get(), PumpjackRenderer::new);
        event.registerBlockEntityRenderer(com.minenorth_harvest.registry.ModBlockEntities.MILL.get(), WindmillRenderer::new);
        event.registerBlockEntityRenderer(com.minenorth_harvest.registry.ModBlockEntities.MILLSTONE.get(), MillstoneRenderer::new);
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "chop_minigame", ChopOverlay.INSTANCE);
    }
}
