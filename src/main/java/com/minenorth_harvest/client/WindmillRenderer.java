package com.minenorth_harvest.client;

import com.minenorth_harvest.block.WindmillBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moulin à vent : quatre ailes à claire-voie qui tournent (lentement au vent, vite quand on moud), meule visible dans la fenêtre,
 * volant de manivelle sur le côté. Repère = modèle (pixels, origine = coin du maître, avant = z 0, tour centrée sur x 8 / z 8).
 */
public class WindmillRenderer implements BlockEntityRenderer<WindmillBlockEntity> {
    private static final float HUB_X = 8f, HUB_Y = 43f, HUB_Z = -13.5f;

    public WindmillRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public boolean shouldRenderOffScreen(WindmillBlockEntity be) {
        return true;
    }

    @Override
    public void render(WindmillBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        long gt = be.getLevel().getGameTime();
        float now = gt + partial;
        boolean milling = gt - be.lastTurn() < 14;
        if (be.lastRender >= 0) {
            float dt = Math.max(0f, Math.min(5f, now - be.lastRender));
            be.angle = (be.angle + dt * (milling ? 7.5f : 0.7f)) % 360f;
            be.stoneAngle = (be.stoneAngle + dt * (milling ? 11f : 1.1f)) % 360f;
        }
        be.lastRender = now;

        BlockState plank = Blocks.DARK_OAK_PLANKS.defaultBlockState();
        BlockState frame = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState cloth = Blocks.WHITE_WOOL.defaultBlockState();
        BlockState dark = Blocks.BLACKSTONE.defaultBlockState();
        BlockState stone = Blocks.SMOOTH_STONE.defaultBlockState();
        BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();

        ps.pushPose();
        RenderUtil.orient(ps, be.facing());

        // --- ailes
        ps.pushPose();
        ps.translate(HUB_X * RenderUtil.PX, HUB_Y * RenderUtil.PX, HUB_Z * RenderUtil.PX);
        ps.mulPose(Axis.ZP.rotationDegrees(be.angle));
        RenderUtil.drawBox(ps, buffers, dark, light, -2.6f, -2.6f, -1.2f, 5.2f, 5.2f, 3.2f);          // moyeu
        for (int k = 0; k < 4; k++) {
            ps.pushPose();
            ps.mulPose(Axis.ZP.rotationDegrees(90f * k));
            RenderUtil.drawBox(ps, buffers, plank, light, -0.9f, 2f, -0.6f, 1.8f, 30f, 1.2f);         // longeron
            RenderUtil.drawBox(ps, buffers, frame, light, 0.9f, 6f, -0.4f, 7f, 0.9f, 0.8f);           // barreaux
            RenderUtil.drawBox(ps, buffers, frame, light, 0.9f, 14f, -0.4f, 7f, 0.9f, 0.8f);
            RenderUtil.drawBox(ps, buffers, frame, light, 0.9f, 22f, -0.4f, 7f, 0.9f, 0.8f);
            RenderUtil.drawBox(ps, buffers, frame, light, 0.9f, 29f, -0.4f, 7f, 0.9f, 0.8f);
            RenderUtil.drawBox(ps, buffers, frame, light, 7.4f, 6f, -0.4f, 0.9f, 24f, 0.8f);         // montant extérieur
            RenderUtil.drawBox(ps, buffers, cloth, light, 1.4f, 7f, -0.15f, 6f, 6.6f, 0.3f);          // toile
            RenderUtil.drawBox(ps, buffers, cloth, light, 1.4f, 15f, -0.15f, 6f, 6.6f, 0.3f);
            RenderUtil.drawBox(ps, buffers, cloth, light, 1.4f, 23f, -0.15f, 6f, 5.4f, 0.3f);
            ps.popPose();
        }
        ps.popPose();

        // --- meule derrière la fenêtre
        ps.pushPose();
        ps.translate(8f * RenderUtil.PX, 30.5f * RenderUtil.PX, -9.6f * RenderUtil.PX);
        ps.mulPose(Axis.ZP.rotationDegrees(be.stoneAngle));
        RenderUtil.drawBox(ps, buffers, stone, light, -4.3f, -4.3f, -0.8f, 8.6f, 8.6f, 1.6f);
        RenderUtil.drawBox(ps, buffers, dark, light, -4.3f, -0.4f, -0.9f, 8.6f, 0.8f, 0.4f);            // rainures
        RenderUtil.drawBox(ps, buffers, dark, light, -0.4f, -4.3f, -0.9f, 0.8f, 8.6f, 0.4f);
        RenderUtil.drawBox(ps, buffers, iron, light, -0.9f, -0.9f, -1.2f, 1.8f, 1.8f, 0.8f);
        ps.popPose();

        // --- volant de manivelle (côté droit)
        ps.pushPose();
        ps.translate(31f * RenderUtil.PX, 9f * RenderUtil.PX, 8f * RenderUtil.PX);
        ps.mulPose(Axis.XP.rotationDegrees(be.stoneAngle * 1.6f));
        RenderUtil.drawBox(ps, buffers, plank, light, 0f, -0.7f, -6f, 1.4f, 1.4f, 12f);
        RenderUtil.drawBox(ps, buffers, plank, light, 0f, -6f, -0.7f, 1.4f, 12f, 1.4f);
        RenderUtil.drawBox(ps, buffers, iron, light, -0.2f, -1.2f, -1.2f, 2.0f, 2.4f, 2.4f);
        RenderUtil.drawBox(ps, buffers, frame, light, 0.2f, 5f, -1f, 1.0f, 2.4f, 2.0f);                  // poignée
        ps.popPose();

        ps.popPose();
    }
}
