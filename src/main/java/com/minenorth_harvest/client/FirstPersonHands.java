package com.minenorth_harvest.client;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Première personne : le marteau-piqueur et la tronçonneuse se tiennent à DEUX mains. Le rendu vanilla (un seul bras collé à la caméra) est remplacé :
 * l'outil est dessiné bien cadré devant le joueur, et les DEUX bras du joueur (son skin) sont dessinés, chacun posé de sorte que sa main tombe sur une
 * poignée (droite : poignée arrière / droite ; gauche : poignée avant / gauche). L'ensemble vibre quand le moteur tourne. Cadrage et géométrie
 * vérifiés par rendu logiciel (positions en blocs dans le repère de la caméra : droite, haut, profondeur).
 */
@Mod.EventBusSubscriber(modid = MineNorthHarvest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class FirstPersonHands {
    private FirstPersonHands() {}

    /**
     * Réglage d'un outil. rot / scale = rotation et échelle appliquées par le code (le « display » firstperson des modèles est neutre) ; base = position du centre du modèle ;
     * gripR / gripL = poignées en pixels du modèle ; shoulder* = décalage du point d'où vient chaque bras (en dessous de la main).
     */
    private record Rig(float rx, float ry, float rz, float scale, Vector3f base, Vector3f gripR, Vector3f gripL, Vector3f shoulderR, Vector3f shoulderL, float shake, float drop) {}

    /** Poignées et épaules fixes (liées au modèle) ; l'échelle, la rotation, la position et l'abaissement des mains viennent de {@link ClientToolConfig}. */
    private static Rig rig(boolean jack) {
        ClientToolConfig.Tool c = jack ? ClientToolConfig.JACKHAMMER : ClientToolConfig.CHAINSAW;
        Vector3f base = new Vector3f(c.posX.get().floatValue(), c.posY.get().floatValue(), c.posZ.get().floatValue());
        float rx = c.rotX.get().floatValue(), ry = c.rotY.get().floatValue(), rz = c.rotZ.get().floatValue(), sc = c.scale.get().floatValue(), drop = c.handDrop.get().floatValue();
        return jack
                ? new Rig(rx, ry, rz, sc, base, new Vector3f(10.75f, 11, 8), new Vector3f(5.25f, 11, 8), new Vector3f(0.60f, -1.0f, 0.0f), new Vector3f(-0.60f, -1.0f, 0.0f), 0.006f, drop)
                : new Rig(rx, ry, rz, sc, base, new Vector3f(8, 8.6f, 15.2f), new Vector3f(8, 11.4f, 12), new Vector3f(0.55f, -1.0f, 0.35f), new Vector3f(-0.55f, -1.0f, 0.35f), 0.004f, drop);
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        Minecraft mc = Minecraft.getInstance();
        AbstractClientPlayer player = mc.player;
        ItemStack held = event.getItemStack();
        if (player == null) return;
        boolean jack = held.is(ModItems.JACKHAMMER.get());
        if (!jack && !held.is(ModItems.CHAINSAW.get())) return;
        Rig rig = rig(jack);
        event.setCanceled(true);

        PoseStack ps = event.getPoseStack();
        float t = player.tickCount + event.getPartialTick();
        boolean running = player.isUsingItem() && player.getUseItem().getItem() == held.getItem();
        float amp = running ? rig.shake() : 0f;
        float sx = (float) Math.sin(t * 2.6f) * amp, sy = (float) Math.sin(t * 3.1f + 1f) * amp * 0.7f;
        Vector3f base = new Vector3f(rig.base()).add(sx, sy + event.getEquipProgress() * -0.6f, 0f);

        ps.pushPose();
        // l'outil
        ps.pushPose();
        ps.translate(base.x, base.y, base.z);
        ps.mulPose(new Quaternionf().rotationXYZ((float) Math.toRadians(rig.rx()), (float) Math.toRadians(rig.ry()), (float) Math.toRadians(rig.rz())));
        ps.scale(rig.scale(), rig.scale(), rig.scale());
        mc.getEntityRenderDispatcher().getItemInHandRenderer().renderItem(player, held, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, ps,
                event.getMultiBufferSource(), event.getPackedLight());
        ps.popPose();
        // les deux bras, mains sur les poignées
        if (mc.getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer) {
            Quaternionf q = new Quaternionf().rotationXYZ((float) Math.toRadians(rig.rx()), (float) Math.toRadians(rig.ry()), (float) Math.toRadians(rig.rz()));
            Vector3f gr = grip(rig, q, rig.gripR(), base).sub(0, rig.drop(), 0), gl = grip(rig, q, rig.gripL(), base).sub(0, rig.drop(), 0);   // drop : bras plus bas que les poignées
            arm(ps, renderer, event.getMultiBufferSource(), event.getPackedLight(), player, gr, new Vector3f(gr).add(rig.shoulderR()), false);
            arm(ps, renderer, event.getMultiBufferSource(), event.getPackedLight(), player, gl, new Vector3f(gl).add(rig.shoulderL()), true);
        }
        ps.popPose();
    }

    /** Position d'une poignée (pixels du modèle) dans le repère de la caméra. */
    private static Vector3f grip(Rig rig, Quaternionf q, Vector3f gripPx, Vector3f base) {
        Vector3f p = new Vector3f((gripPx.x - 8) / 16f * rig.scale(), (gripPx.y - 8) / 16f * rig.scale(), (gripPx.z - 8) / 16f * rig.scale());
        return q.transform(p).add(base);
    }

    /** Dessine un bras du joueur dont l'extrémité (la main) est en hand et qui vient de la direction shoulder. */
    private static void arm(PoseStack ps, PlayerRenderer renderer, MultiBufferSource buffers, int light, AbstractClientPlayer player, Vector3f hand, Vector3f shoulder, boolean left) {
        Vector3f d = new Vector3f(hand).sub(shoulder).normalize();
        Quaternionf q = new Quaternionf().rotateTo(new Vector3f(0, 1, 0), d);
        ps.pushPose();
        ps.translate(hand.x, hand.y, hand.z);
        ps.mulPose(q);
        ps.translate(left ? -6f / 16f : 6f / 16f, -12f / 16f, 0f);    // le bout du bras (la main) tombe sur le point voulu
        if (left) renderer.renderLeftHand(ps, buffers, light, player);
        else renderer.renderRightHand(ps, buffers, light, player);
        ps.popPose();
    }
}
