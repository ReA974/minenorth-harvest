package com.minenorth_harvest.client;

import com.minenorth_harvest.block.PumpjackBlockEntity;
import com.minenorth_harvest.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Chevalet de pompage : balancier basculant sur le palier, tête de cheval, tige polie qui monte et descend dans le puits,
 * bielle verticale et contrepoids qui tournent. Repère = modèle (pixels, origine = coin du bloc maître, avant = z 0).
 */
public class PumpjackRenderer implements BlockEntityRenderer<PumpjackBlockEntity> {
    private static final float PIVOT_Y = 36f, PIVOT_Z = 26f, FRONT = 18f, REAR = 20f;

    public PumpjackRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public boolean shouldRenderOffScreen(PumpjackBlockEntity be) {
        return true;
    }

    @Override
    public void render(PumpjackBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        float time = be.getLevel().getGameTime() + partial;
        float phase = be.working() ? time * (float) (2 * Math.PI / 60.0) : 0f;
        float theta = 12f * (float) Math.sin(phase);          // inclinaison du balancier (degrés)
        float sin = (float) Math.sin(Math.toRadians(theta));
        BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();
        BlockState dark = Blocks.BLACKSTONE.defaultBlockState();
        BlockState yellow = Blocks.YELLOW_CONCRETE.defaultBlockState();
        BlockState steel = Blocks.POLISHED_ANDESITE.defaultBlockState();

        ps.pushPose();
        RenderUtil.orient(ps, be.facing());

        // --- balancier : pivote autour de l'axe X au sommet du chevalet
        ps.pushPose();
        ps.translate(8f * RenderUtil.PX, PIVOT_Y * RenderUtil.PX, PIVOT_Z * RenderUtil.PX);
        ps.mulPose(Axis.XP.rotationDegrees(theta));
        RenderUtil.drawBox(ps, buffers, iron, light, -3f, -2f, -FRONT, 6f, 4f, FRONT + REAR);                    // poutre
        RenderUtil.drawBox(ps, buffers, yellow, light, -3.4f, -3.2f, -FRONT - 3.5f, 6.8f, 8.4f, 4.5f);           // tête de cheval
        RenderUtil.drawBox(ps, buffers, dark, light, -3.6f, -1.4f, REAR - 4f, 7.2f, 2.8f, 5f);                   // contrepoids de queue
        RenderUtil.drawBox(ps, buffers, dark, light, -3.2f, -3.0f, -2f, 6.4f, 6f, 4f);                          // palier central
        ps.popPose();

        // --- tige polie : suit la tête du balancier dans le puits
        float tipY = PIVOT_Y + FRONT * sin;
        float top = tipY - 3.5f;
        float rodH = Math.max(2f, top - 24f);
        RenderUtil.drawBox(ps, buffers, steel, light, 7.3f, 24f, 7.3f, 1.4f, rodH, 1.4f);
        RenderUtil.drawBox(ps, buffers, dark, light, 6.2f, top - 0.8f, 6.2f, 3.6f, 1.6f, 3.6f);                  // collier

        // --- bielle verticale entre le moteur et la queue du balancier
        float rearY = PIVOT_Y - REAR * sin;
        float pitH = Math.max(2f, rearY - 15f);
        RenderUtil.drawBox(ps, buffers, steel, light, 7.2f, 15f, 44.2f, 1.6f, pitH, 1.6f);

        // --- contrepoids tournants de chaque côté de la boîte d'engrenages
        float crank = phase * 57.2958f;
        for (float x : new float[]{-1.6f, 16f}) {
            ps.pushPose();
            ps.translate(x * RenderUtil.PX, 12f * RenderUtil.PX, 31f * RenderUtil.PX);
            ps.mulPose(Axis.XP.rotationDegrees(-crank));
            RenderUtil.drawBox(ps, buffers, dark, light, 0f, -5f, -5f, 1.6f, 10f, 10f);
            RenderUtil.drawBox(ps, buffers, yellow, light, 0.2f, -5.4f, -2.2f, 1.6f, 4f, 4.4f);
            ps.popPose();
        }

        // --- pétrole en attente : bidon qui flotte au-dessus du puits
        if (be.stored() > 0) {
            ps.pushPose();
            ps.translate(8f * RenderUtil.PX, (30f + 1.2f * (float) Math.sin(time * 0.15f)) * RenderUtil.PX, 8f * RenderUtil.PX);
            ps.mulPose(Axis.YP.rotationDegrees(time * 3f));
            ps.scale(0.5f, 0.5f, 0.5f);
            Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(ModItems.CRUDE_OIL.get()), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
            ps.popPose();
        }
        ps.popPose();
    }
}
