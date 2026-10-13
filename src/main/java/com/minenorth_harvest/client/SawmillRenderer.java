package com.minenorth_harvest.client;

import com.minenorth_harvest.block.MachineRecipes;
import com.minenorth_harvest.block.SawmillBlock;
import com.minenorth_harvest.block.SawmillBlockEntity;
import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Scierie 2 x 3 x 2 : le repère est celui du modèle (pixels, 16 = 1 bloc, origine = coin du bloc maître, orientée au nord) ;
 * le tronc glisse de l'arrière (z 32) vers l'avant (z -16) sous la lame circulaire (z 16) et ressort en planches.
 */
public class SawmillRenderer implements BlockEntityRenderer<SawmillBlockEntity> {
    private static final float LOG_LEN = 14f;
    private static final float START_Z = 24f;    // centre du tronc au départ (arrière)
    private static final float END_Z = -8f;      // centre du tronc à l'arrivée (avant)
    private static final float BLADE_Z = 16f;
    private static final float BED_TOP = 17f;
    private static final float CX = 16f;          // axe central de la table

    public SawmillRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public boolean shouldRenderOffScreen(SawmillBlockEntity be) {
        return true;
    }

    @Override
    public void render(SawmillBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        Direction facing = be.getBlockState().getValue(SawmillBlock.FACING);
        boolean working = be.cycleStart() >= 0;
        float time = be.getLevel().getGameTime() + partial;
        float f = working ? Math.max(0f, Math.min(1f, (time - be.cycleStart()) / be.cycleLen())) : 0f;

        ps.pushPose();
        RenderUtil.orient(ps, facing);

        // --- lame circulaire (item plat qui tourne autour de l'axe Z)
        ps.pushPose();
        ps.translate(CX * RenderUtil.PX, 21f * RenderUtil.PX, BLADE_Z * RenderUtil.PX);
        ps.mulPose(Axis.ZP.rotationDegrees(working ? time * 60f : 0f));
        ps.scale(1.06f, 1.06f, 1.06f);
        Minecraft.getInstance().getItemRenderer().renderStatic(new ItemStack(ModItems.SAW_BLADE.get()), ItemDisplayContext.NONE,
                light, OverlayTexture.NO_OVERLAY, ps, buffers, be.getLevel(), 0);
        ps.popPose();

        ItemStack in = be.input();
        ItemStack out = be.output();
        if (working || !in.isEmpty()) {
            float centre = working ? START_Z + (END_Z - START_Z) * f : START_Z;
            float zMin = centre - LOG_LEN / 2f, zMax = centre + LOG_LEN / 2f;
            float cut = Math.max(zMin, Math.min(zMax, BLADE_Z));
            // chariot
            drawBlock(ps, buffers, Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), light, CX - 6f, BED_TOP + 0.2f, zMin, 12f, 1.6f, LOG_LEN);
            // partie pas encore sciée : le tronc
            if (zMax - cut > 0.05f && !in.isEmpty()) {
                BlockState log = Block.byItem(in.getItem()).defaultBlockState();
                if (log.hasProperty(RotatedPillarBlock.AXIS)) log = log.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
                drawBlock(ps, buffers, log, light, CX - 5f, BED_TOP + 1.8f, cut, 10f, 10f, zMax - cut);
            }
            // partie sciée : planches empilées
            if (cut - zMin > 0.05f && working) {
                BlockState planks = plankState(be);
                for (int i = 0; i < 3; i++) {
                    drawBlock(ps, buffers, planks, light, CX - 5f, BED_TOP + 1.8f + i * 3.4f, zMin, 10f, 3.2f, cut - zMin);
                }
            }
        }
        // planches prêtes à l'avant de la table
        if (!out.isEmpty()) {
            BlockState planks = Block.byItem(out.getItem()).defaultBlockState();
            int stacks = Math.min(4, 1 + out.getCount() / 8);
            for (int i = 0; i < stacks; i++) {
                drawBlock(ps, buffers, planks, light, CX - 5f, BED_TOP + 0.2f + i * 3.4f, -14.5f, 10f, 3.2f, 10f);
            }
        }
        ps.popPose();
    }

    private static BlockState plankState(SawmillBlockEntity be) {
        if (!be.output().isEmpty()) return Block.byItem(be.output().getItem()).defaultBlockState();
        MachineRecipes.Recipe r = MachineRecipes.find(be.input(), HarvestConfig.SAWMILL_RECIPES.get());
        return (r == null ? Blocks.OAK_PLANKS : Block.byItem(r.output())).defaultBlockState();
    }

    /** Un cuboïde de bloc (pixels) : on affiche le bloc unitaire mis à l'échelle. */
    private static void drawBlock(PoseStack ps, MultiBufferSource buffers, BlockState state, int light,
                                  float x, float y, float z, float sx, float sy, float sz) {
        ps.pushPose();
        ps.translate(x * RenderUtil.PX, y * RenderUtil.PX, z * RenderUtil.PX);
        ps.scale(sx * RenderUtil.PX, sy * RenderUtil.PX, sz * RenderUtil.PX);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }
}
