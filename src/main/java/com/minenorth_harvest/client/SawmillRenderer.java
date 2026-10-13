package com.minenorth_harvest.client;

import com.minenorth_harvest.block.SawmillBlock;
import com.minenorth_harvest.block.SawmillBlockEntity;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.Direction;

/** Scierie : lame qui tourne, tronc qui glisse sous la lame et ressort en planches. */
public class SawmillRenderer implements BlockEntityRenderer<SawmillBlockEntity> {
    private static final float LOG_LEN = 7f;      // pixels
    private static final float START_Z = 12.5f;   // centre du tronc au départ (arrière)
    private static final float END_Z = 3.5f;      // centre du tronc à l'arrivée (avant)
    private static final float BLADE_Z = 8f;
    private static final float BED_TOP = 8f;

    public SawmillRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(SawmillBlockEntity be, float partial, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
        if (be.getLevel() == null) return;
        Direction facing = be.getBlockState().getValue(SawmillBlock.FACING);
        boolean working = be.cycleStart() >= 0;
        float f = 0f;
        float time = be.getLevel().getGameTime() + partial;
        if (working) f = Math.max(0f, Math.min(1f, (time - be.cycleStart()) / be.cycleLen()));

        ps.pushPose();
        RenderUtil.orient(ps, facing);

        // --- lame circulaire (item plat qui tourne autour de l'axe Z)
        ps.pushPose();
        ps.translate(0.5, 10.5 * RenderUtil.PX, BLADE_Z * RenderUtil.PX);
        float angle = working ? time * 55f : 0f;
        ps.mulPose(Axis.ZP.rotationDegrees(angle));
        ps.scale(0.62f, 0.62f, 0.62f);
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
            drawBlock(ps, buffers, net.minecraft.world.level.block.Blocks.SMOOTH_STONE_SLAB.defaultBlockState(), light,
                    5f, BED_TOP, zMin, 6f, 1f, LOG_LEN);
            // partie pas encore sciée : le tronc
            if (zMax - cut > 0.05f && !in.isEmpty()) {
                BlockState log = Block.byItem(in.getItem()).defaultBlockState();
                if (log.hasProperty(RotatedPillarBlock.AXIS)) log = log.setValue(RotatedPillarBlock.AXIS, net.minecraft.core.Direction.Axis.Z);
                drawBlock(ps, buffers, log, light, 5.5f, BED_TOP + 1f, cut, 5f, 5f, zMax - cut);
            }
            // partie sciée : planches empilées
            if (cut - zMin > 0.05f && working) {
                ItemStack made = new ItemStack(net.minecraft.world.item.Items.OAK_PLANKS);
                MachineOut planks = MachineOut.of(be);
                for (int i = 0; i < 3; i++) {
                    drawBlock(ps, buffers, planks.state, light, 5.5f, BED_TOP + 1f + i * 1.6f, zMin, 5f, 1.5f, cut - zMin);
                }
            }
        }
        // planches prêtes à l'avant
        if (!out.isEmpty()) {
            BlockState planks = Block.byItem(out.getItem()).defaultBlockState();
            int stacks = Math.min(4, 1 + out.getCount() / 8);
            for (int i = 0; i < stacks; i++) {
                drawBlock(ps, buffers, planks, light, 5f, BED_TOP + 1f + i * 1.6f, 0.5f, 6f, 1.5f, 5f);
            }
        }
        ps.popPose();
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

    /** Planches produites par la recette en cours (d'après la sortie, sinon planches de chêne). */
    private record MachineOut(BlockState state) {
        static MachineOut of(SawmillBlockEntity be) {
            if (!be.output().isEmpty()) return new MachineOut(Block.byItem(be.output().getItem()).defaultBlockState());
            com.minenorth_harvest.block.MachineRecipes.Recipe r = com.minenorth_harvest.block.MachineRecipes.find(be.input(),
                    com.minenorth_harvest.config.HarvestConfig.SAWMILL_RECIPES.get());
            Block b = r == null ? net.minecraft.world.level.block.Blocks.OAK_PLANKS : Block.byItem(r.output());
            return new MachineOut(b.defaultBlockState());
        }
    }
}
