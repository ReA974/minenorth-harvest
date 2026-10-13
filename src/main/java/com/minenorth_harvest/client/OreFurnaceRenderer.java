package com.minenorth_harvest.client;

import com.minenorth_harvest.block.CastingBucketBlockEntity;
import com.minenorth_harvest.block.OreFurnaceBlock;
import com.minenorth_harvest.block.OreFurnaceBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;

/** Four à minerai : jet de métal fondu qui coule du bec vers le seau de coulée placé devant. */
public class OreFurnaceRenderer implements BlockEntityRenderer<OreFurnaceBlockEntity> {
    public OreFurnaceRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(OreFurnaceBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null || be.pourStart() < 0 || be.pending().isEmpty()) return;
        Direction facing = be.getBlockState().getValue(OreFurnaceBlock.FACING);
        int rgb = CastingBucketBlockEntity.metalColor(be.pending());
        // le jet "vibre" un peu
        float wob = (float) Math.sin((be.getLevel().getGameTime() + partial) * 1.7f) * 0.15f;
        ps.pushPose();
        RenderUtil.orient(ps, facing);
        // bec à (8, 8, -8) : le jet tombe jusqu'à la surface du seau
        RenderUtil.glowBox(ps, RenderUtil.glowBuffer(buffers), RenderUtil.sprite("block/lava_flow"),
                7.4f + wob, 5.2f, -8.6f, 8.6f + wob, 8.1f, -7.4f, rgb, false);
        ps.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(OreFurnaceBlockEntity be) {
        return true;
    }
}
