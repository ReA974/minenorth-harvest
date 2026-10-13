package com.minenorth_harvest.client;

import com.minenorth_harvest.block.CastingBucketBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Seau de coulée : surface de métal en fusion qui s'assombrit puis lingots refroidis au fond. */
public class CastingBucketRenderer implements BlockEntityRenderer<CastingBucketBlockEntity> {
    public CastingBucketRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(CastingBucketBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        ItemStack c = be.contents();
        if (c.isEmpty()) return;
        if (be.isMolten()) {
            int rgb = CastingBucketBlockEntity.metalColor(c);
            // le métal refroidit : on le fonce peu à peu
            float cool = 1f - (float) be.melt() / be.meltTotal();
            float k = 1f - 0.45f * cool;
            int r = (int) (((rgb >> 16) & 255) * k), g = (int) (((rgb >> 8) & 255) * k), b = (int) ((rgb & 255) * k);
            ps.pushPose();
            RenderUtil.glowBox(ps, RenderUtil.glowBuffer(buffers), RenderUtil.sprite("block/lava_still"),
                    4.6f, 0f, 4.6f, 11.4f, 5.3f, 11.4f, (r << 16) | (g << 8) | b, true);
            ps.popPose();
        } else {
            int n = Math.min(c.getCount(), 4);
            float[][] spots = {{6f, 5f}, {10f, 5f}, {6f, 10f}, {10f, 10f}};
            for (int i = 0; i < n; i++) {
                ps.pushPose();
                ps.translate(spots[i][0] * RenderUtil.PX + 0.0, 1.5 * RenderUtil.PX + (i / 2) * 0.0, spots[i][1] * RenderUtil.PX);
                ps.mulPose(Axis.XP.rotationDegrees(90f));
                ps.mulPose(Axis.ZP.rotationDegrees(i * 37f));
                ps.scale(0.42f, 0.42f, 0.42f);
                Minecraft.getInstance().getItemRenderer().renderStatic(c.copyWithCount(1), ItemDisplayContext.NONE,
                        light, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), i);
                ps.popPose();
            }
        }
    }
}
