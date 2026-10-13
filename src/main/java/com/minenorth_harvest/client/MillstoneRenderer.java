package com.minenorth_harvest.client;

import com.minenorth_harvest.block.MillstoneBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Moulin à meule, comme un moulin à huile : une grosse meule de pierre verticale roule dans une cuve autour d'un axe central, tirée par
 * une poutre (contrepoids de l'autre côté). Elle tourne doucement au repos et plus vite quand on moud. Repère = pixels, origine = coin
 * du bloc maître (la cuve centrale) : la machine occupe x et z de -16 à 32, l'axe est à x 8 / z 8.
 */
public class MillstoneRenderer implements BlockEntityRenderer<MillstoneBlockEntity> {
    private static final float WHEEL_ORBIT = 14f, WHEEL_RADIUS = 10f, WHEEL_Y = 16f;

    public MillstoneRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public boolean shouldRenderOffScreen(MillstoneBlockEntity be) {
        return true;
    }

    @Override
    public void render(MillstoneBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        long gt = be.getLevel().getGameTime();
        float now = gt + partial;
        boolean milling = gt - be.lastTurn() < 14;
        if (be.lastRender >= 0) {
            float dt = Math.max(0f, Math.min(5f, now - be.lastRender));
            be.angle = (be.angle + dt * (milling ? 6.5f : 1.0f)) % 360f;
        }
        be.lastRender = now;

        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState bed = Blocks.MUD.defaultBlockState();
        BlockState log = Blocks.DARK_OAK_LOG.defaultBlockState();
        BlockState plank = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState dark = Blocks.BLACKSTONE.defaultBlockState();
        BlockState iron = Blocks.IRON_BLOCK.defaultBlockState();

        // --- cuve : socle, murs, lit de mouture
        RenderUtil.drawBox(ps, buffers, bricks, light, -14f, 0f, -14f, 44f, 4f, 44f);
        RenderUtil.drawBox(ps, buffers, bricks, light, -14f, 4f, -14f, 44f, 8f, 4f);
        RenderUtil.drawBox(ps, buffers, bricks, light, -14f, 4f, 26f, 44f, 8f, 4f);
        RenderUtil.drawBox(ps, buffers, bricks, light, -14f, 4f, -10f, 4f, 8f, 36f);
        RenderUtil.drawBox(ps, buffers, bricks, light, 26f, 4f, -10f, 4f, 8f, 36f);
        RenderUtil.drawBox(ps, buffers, plank, light, -15f, 12f, -15f, 46f, 1.5f, 3f);            // rebord de bois
        RenderUtil.drawBox(ps, buffers, plank, light, -15f, 12f, 28f, 46f, 1.5f, 3f);
        RenderUtil.drawBox(ps, buffers, plank, light, -15f, 12f, -12f, 3f, 1.5f, 40f);
        RenderUtil.drawBox(ps, buffers, plank, light, 28f, 12f, -12f, 3f, 1.5f, 40f);
        RenderUtil.drawBox(ps, buffers, bed, light, -10f, 4f, -10f, 36f, 3f, 36f);

        // --- charpente : poteaux d'angle et poutres au-dessus de l'axe
        for (float x : new float[]{-14f, 27f})
            for (float z : new float[]{-14f, 27f})
                RenderUtil.drawBox(ps, buffers, log, light, x, 13.5f, z, 3f, 18.5f, 3f);
        RenderUtil.drawBox(ps, buffers, plank, light, -14f, 30f, 6f, 44f, 2f, 4f);
        RenderUtil.drawBox(ps, buffers, plank, light, 6f, 30f, -14f, 4f, 2f, 44f);

        // --- ensemble tournant : axe, couronne dentée, poutre, meule et contrepoids
        ps.pushPose();
        ps.translate(8f * RenderUtil.PX, 0, 8f * RenderUtil.PX);
        ps.mulPose(Axis.YP.rotationDegrees(-be.angle));
        RenderUtil.drawBox(ps, buffers, log, light, -2f, 7f, -2f, 4f, 24f, 4f);                    // axe vertical
        RenderUtil.drawBox(ps, buffers, dark, light, -5f, 27f, -5f, 10f, 2f, 10f);                 // couronne dentée
        RenderUtil.drawBox(ps, buffers, iron, light, -3f, 25f, -3f, 6f, 2f, 6f);
        RenderUtil.drawBox(ps, buffers, plank, light, -9f, WHEEL_Y - 1.5f, -1.5f, WHEEL_ORBIT + 9f, 3f, 3f);   // poutre
        RenderUtil.drawBox(ps, buffers, stone, light, -13f, WHEEL_Y - 3f, -3f, 5f, 6f, 6f);        // contrepoids
        // meule : disque vertical (octogone fait de quatre bandes) qui roule autour de la poutre
        ps.pushPose();
        ps.translate(WHEEL_ORBIT * RenderUtil.PX, WHEEL_Y * RenderUtil.PX, 0);
        ps.mulPose(Axis.XP.rotationDegrees(be.angle * WHEEL_ORBIT / WHEEL_RADIUS));
        for (int k = 0; k < 4; k++) {
            ps.pushPose();
            ps.mulPose(Axis.XP.rotationDegrees(45f * k));
            RenderUtil.drawBox(ps, buffers, stone, light, -4f, -WHEEL_RADIUS, -6f, 8f, WHEEL_RADIUS * 2f, 12f);
            ps.popPose();
        }
        RenderUtil.drawBox(ps, buffers, iron, light, -5f, -1.5f, -1.5f, 10f, 3f, 3f);              // moyeu
        ps.popPose();
        ps.popPose();
    }
}
