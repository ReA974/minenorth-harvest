package com.minenorth_harvest.client;

import com.minenorth_harvest.block.RefineryBlockEntity;
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
 * Raffinerie : niveau de brut dans le regard, gueule du four incandescente, flamme de torchère, cadran de pression à aiguille,
 * levier de pompe et volant de vanne animés, essence en lévitation au collecteur. Repère = modèle (pixels, origine = coin du maître, avant = z 0).
 */
public class RefineryRenderer implements BlockEntityRenderer<RefineryBlockEntity> {
    private static final float DIAL_X = 40f, DIAL_Y = 13f, DIAL_Z = 1.4f;

    public RefineryRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public boolean shouldRenderOffScreen(RefineryBlockEntity be) {
        return true;
    }

    @Override
    public void render(RefineryBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        long gt = be.getLevel().getGameTime();
        float time = gt + partial;
        boolean fire = be.working() || be.burn() > 0;

        ps.pushPose();
        RenderUtil.orient(ps, be.facing());
        var vc = RenderUtil.glowBuffer(buffers);

        // --- niveau de brut dans le regard du réservoir
        int crude = be.input().getCount();
        if (crude > 0) {
            float h = 11.6f * Math.min(1f, crude / 64f);
            RenderUtil.glowBox(ps, vc, RenderUtil.sprite("block/lava_still"), 6.3f, 8.2f, 3.0f, 13.7f, 8.2f + Math.max(0.6f, h), 3.5f, 0x4A2A10, false);
        }

        // --- gueule du four
        if (fire) {
            float flick = 0.85f + 0.15f * (float) Math.sin(time * 0.7f);
            int c = (int) (255 * flick) << 16 | (int) (140 * flick) << 8 | 40;
            RenderUtil.glowBox(ps, vc, RenderUtil.sprite("block/lava_still"), 26.1f, 4.2f, 5.0f, 29.9f, 13.8f, 5.35f, c, false);
        }

        // --- flamme de torchère (plans croisés sur la tête du mât)
        if (fire) {
            float h = 9.5f + 2.2f * (float) Math.sin(time * 0.9f) + 1.2f * (float) Math.sin(time * 2.3f);
            RenderUtil.crossQuads(ps, vc, RenderUtil.sprite("block/fire_0"), 42f, 59.2f, 16f, 7.5f, h, 0xFFFFFF);
        }

        // --- cadran de pression : graduations (vert 40-70, rouge dès 90) + aiguille
        float frac = Math.min(1.1f, be.pressure() / 100f);
        BlockState tick = Blocks.BLACK_CONCRETE.defaultBlockState();
        for (int i = 0; i <= 10; i++) {
            float ang = -135f + 270f * (i / 10f);
            BlockState st = i * 10 >= 90 ? Blocks.RED_CONCRETE.defaultBlockState()
                    : (i * 10 >= RefineryBlockEntity.GREEN_MIN && i * 10 <= RefineryBlockEntity.GREEN_MAX) ? Blocks.LIME_CONCRETE.defaultBlockState() : tick;
            ps.pushPose();
            ps.translate(DIAL_X * RenderUtil.PX, DIAL_Y * RenderUtil.PX, DIAL_Z * RenderUtil.PX);
            ps.mulPose(Axis.ZP.rotationDegrees(ang));
            RenderUtil.drawBox(ps, buffers, st, light, -0.3f, 2.9f, -0.2f, 0.6f, 1.0f, 0.4f);
            ps.popPose();
        }
        ps.pushPose();
        ps.translate(DIAL_X * RenderUtil.PX, DIAL_Y * RenderUtil.PX, (DIAL_Z - 0.2f) * RenderUtil.PX);
        float jitter = be.pressure() >= RefineryBlockEntity.DANGER ? (float) Math.sin(time * 3.5f) * 3f : 0f;
        ps.mulPose(Axis.ZP.rotationDegrees(-135f + 270f * Math.min(1.1f, frac) + jitter));
        RenderUtil.drawBox(ps, buffers, Blocks.RED_CONCRETE.defaultBlockState(), light, -0.25f, -0.4f, -0.2f, 0.5f, 3.6f, 0.4f);
        RenderUtil.drawBox(ps, buffers, Blocks.BLACK_CONCRETE.defaultBlockState(), light, -0.7f, -0.7f, -0.4f, 1.4f, 1.4f, 0.8f);
        ps.popPose();

        // --- levier de pompe (côté droit du pupitre) : oscille à chaque coup
        float age = gt - be.lastPump() + partial;
        float swing = age < 7f ? 50f * (float) Math.sin(Math.PI * age / 7f) : 0f;
        ps.pushPose();
        ps.translate(46f * RenderUtil.PX, 12f * RenderUtil.PX, 7f * RenderUtil.PX);
        ps.mulPose(Axis.XP.rotationDegrees(-swing));
        RenderUtil.drawBox(ps, buffers, Blocks.IRON_BLOCK.defaultBlockState(), light, 0.2f, -1f, -0.5f, 1.0f, 8.0f, 1.0f);
        RenderUtil.drawBox(ps, buffers, Blocks.RED_CONCRETE.defaultBlockState(), light, -0.3f, 6.6f, -1.0f, 2.0f, 2.2f, 2.0f);
        ps.popPose();

        // --- volant de vanne (face du pupitre) : un demi-tour quand on décharge
        float va = gt - be.lastVent() + partial;
        float spin = va < 12f ? 180f * va / 12f : 0f;
        ps.pushPose();
        ps.translate(40f * RenderUtil.PX, 6.2f * RenderUtil.PX, 1.2f * RenderUtil.PX);
        ps.mulPose(Axis.ZP.rotationDegrees(spin));
        RenderUtil.drawBox(ps, buffers, Blocks.RED_CONCRETE.defaultBlockState(), light, -2.4f, -0.35f, -0.4f, 4.8f, 0.7f, 0.7f);
        RenderUtil.drawBox(ps, buffers, Blocks.RED_CONCRETE.defaultBlockState(), light, -0.35f, -2.4f, -0.4f, 0.7f, 4.8f, 0.7f);
        RenderUtil.drawBox(ps, buffers, Blocks.IRON_BLOCK.defaultBlockState(), light, -0.8f, -0.8f, -0.6f, 1.6f, 1.6f, 1.0f);
        ps.popPose();

        // --- essence prête au collecteur : bidon en lévitation
        ItemStack out = be.output();
        if (!out.isEmpty()) {
            ps.pushPose();
            ps.translate(36f * RenderUtil.PX, (20f + 1.2f * (float) Math.sin(time * 0.15f)) * RenderUtil.PX, 22f * RenderUtil.PX);
            ps.mulPose(Axis.YP.rotationDegrees(time * 3f));
            ps.scale(0.55f, 0.55f, 0.55f);
            Minecraft.getInstance().getItemRenderer().renderStatic(out.copyWithCount(1), ItemDisplayContext.GROUND, light, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
            ps.popPose();
        }
        ps.popPose();
    }
}
