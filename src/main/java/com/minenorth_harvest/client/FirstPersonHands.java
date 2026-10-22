package com.minenorth_harvest.client;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.registry.ModItems;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vue à la première personne : tronçonneuse et marteau-piqueur se tiennent à DEUX mains. Minecraft ne dessine que le bras de la main qui tient
 * l'objet ; on dessine ici le bras de l'autre main (main vide), posé sur la deuxième poignée. Réglages de position : DX / DY / DZ.
 */
@Mod.EventBusSubscriber(modid = MineNorthHarvest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FirstPersonHands {
    private FirstPersonHands() {}

    /** Décalage du bras de renfort vers le centre de l'écran (vers l'outil) par rapport au bras vanilla. */
    private static final float DX = 0.34f, DY = 0.10f, DZ = 0.0f;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        if (player == null || player.isInvisible() || !player.getOffhandItem().isEmpty()) return;
        ItemStack held = event.getItemStack();
        if (!held.is(ModItems.JACKHAMMER.get()) && !held.is(ModItems.CHAINSAW.get())) return;
        if (!(mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        var ps = event.getPoseStack();
        float equip = event.getEquipProgress();
        ps.pushPose();
        // même placement que le bras gauche vanilla (main vide), puis décalage vers l'outil
        ps.translate(-0.64000005F + DX, -0.6F + equip * -0.6F + DY, -0.71999997F + DZ);
        ps.mulPose(Axis.YP.rotationDegrees(-45.0F));
        ps.translate(1.0F, 3.6F, 3.5F);
        ps.mulPose(Axis.ZP.rotationDegrees(-120.0F));
        ps.mulPose(Axis.XP.rotationDegrees(200.0F));
        ps.mulPose(Axis.YP.rotationDegrees(135.0F));
        ps.translate(-5.6F, 0.0F, 0.0F);
        renderer.renderLeftHand(ps, event.getMultiBufferSource(), event.getPackedLight(), player);
        ps.popPose();
    }
}
