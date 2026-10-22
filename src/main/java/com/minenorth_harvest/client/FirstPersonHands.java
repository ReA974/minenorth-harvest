package com.minenorth_harvest.client;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.registry.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Vue à la première personne : le marteau-piqueur se tient à DEUX mains. Le bras vanilla (une seule main, collée à la caméra) est remplacé : on
 * dessine l'outil seul, bien cadré en bas à droite du centre, avec ses deux gants jaunes sur les poignées (ils font partie du modèle). Il vibre
 * quand le moteur tourne. La tronçonneuse garde le rendu vanilla (une main) ; sa pose à deux mains se voit à la troisième personne.
 */
@Mod.EventBusSubscriber(modid = MineNorthHarvest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FirstPersonHands {
    private FirstPersonHands() {}

    /** Position du centre de l'outil devant la caméra (blocs : droite, haut, profondeur). Cadrage vérifié par rendu logiciel. */
    private static final float X = 0.20f, Y = -0.28f, Z = -0.95f;

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        ItemStack held = event.getItemStack();
        if (player == null || !held.is(ModItems.JACKHAMMER.get())) return;
        event.setCanceled(true);
        var ps = event.getPoseStack();
        float t = player.tickCount + event.getPartialTick();
        boolean running = player.isUsingItem() && player.getUseItem().is(ModItems.JACKHAMMER.get());
        float shake = running ? (float) Math.sin(t * 2.6) * 0.006f : 0f;
        ps.pushPose();
        ps.translate(X + shake, Y + event.getEquipProgress() * -0.6f + shake * 0.6f, Z);
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, held, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, ps,
                event.getMultiBufferSource(), event.getPackedLight());
        ps.popPose();
    }
}
