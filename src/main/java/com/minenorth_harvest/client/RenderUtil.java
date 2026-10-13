package com.minenorth_harvest.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Petits outils de rendu partagés par les trois machines (coordonnées en pixels de bloc, 16 = 1 bloc). */
final class RenderUtil {
    private RenderUtil() {}

    static final float PX = 1f / 16f;

    /** Applique l'orientation du bloc (le modèle de base regarde vers le nord). */
    static void orient(PoseStack ps, Direction facing) {
        ps.translate(0.5, 0, 0.5);
        ps.mulPose(Axis.YP.rotationDegrees(180f - facing.toYRot()));
        ps.translate(-0.5, 0, -0.5);
    }

    static TextureAtlasSprite sprite(String path) {
        return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS).getSprite(new ResourceLocation(path));
    }

    static VertexConsumer glowBuffer(MultiBufferSource buffers) {
        return buffers.getBuffer(RenderType.entityCutout(TextureAtlas.LOCATION_BLOCKS));
    }

    /** Boîte pleine lumière (métal en fusion) de (x0,y0,z0) à (x1,y1,z1), en pixels, couleur RGB. */
    static void glowBox(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float x0, float y0, float z0, float x1, float y1, float z1, int rgb, boolean top) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        x0 *= PX; y0 *= PX; z0 *= PX; x1 *= PX; y1 *= PX; z1 *= PX;
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float u0 = sp.getU0(), u1 = sp.getU(8), v0 = sp.getV0(), v1 = sp.getV(8);
        // dessus
        quad(vc, m, n, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, u0, v0, u1, v1, r, g, b);
        if (top) return;
        // côtés
        quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, 0, 0, -1, u0, v0, u1, v1, r, g, b);
        quad(vc, m, n, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, 0, 0, 1, u0, v0, u1, v1, r, g, b);
        quad(vc, m, n, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, -1, 0, 0, u0, v0, u1, v1, r, g, b);
        quad(vc, m, n, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, 1, 0, 0, u0, v0, u1, v1, r, g, b);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float nx, float ny, float nz, float u0, float v0, float u1, float v1, float r, float g, float b) {
        vertex(vc, m, n, ax, ay, az, u0, v1, r, g, b, nx, ny, nz);
        vertex(vc, m, n, bx, by, bz, u1, v1, r, g, b, nx, ny, nz);
        vertex(vc, m, n, cx, cy, cz, u1, v0, r, g, b, nx, ny, nz);
        vertex(vc, m, n, dx, dy, dz, u0, v0, r, g, b, nx, ny, nz);
        // face arrière pour que le jet reste visible des deux côtés
        vertex(vc, m, n, dx, dy, dz, u0, v0, r, g, b, -nx, -ny, -nz);
        vertex(vc, m, n, cx, cy, cz, u1, v0, r, g, b, -nx, -ny, -nz);
        vertex(vc, m, n, bx, by, bz, u1, v1, r, g, b, -nx, -ny, -nz);
        vertex(vc, m, n, ax, ay, az, u0, v1, r, g, b, -nx, -ny, -nz);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v,
                               float r, float g, float b, float nx, float ny, float nz) {
        vc.vertex(m, x, y, z).color(r, g, b, 1f).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, nx, ny, nz).endVertex();
    }

    /** Cuboïde de bloc plein (pixels) : le bloc unitaire mis à l'échelle (x, y, z = coin ; sx, sy, sz = dimensions). */
    static void drawBox(PoseStack ps, MultiBufferSource buffers, net.minecraft.world.level.block.state.BlockState state, int light,
                        float x, float y, float z, float sx, float sy, float sz) {
        ps.pushPose();
        ps.translate(x * PX, y * PX, z * PX);
        ps.scale(sx * PX, sy * PX, sz * PX);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, ps, buffers, light, OverlayTexture.NO_OVERLAY);
        ps.popPose();
    }

    /** Deux plans croisés (flamme) pleine lumière : base au centre (cx, y0, cz), largeur w, hauteur h (pixels). */
    static void crossQuads(PoseStack ps, VertexConsumer vc, TextureAtlasSprite sp, float cx, float y0, float cz, float w, float h, int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        PoseStack.Pose pose = ps.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        float x0 = (cx - w / 2) * PX, x1 = (cx + w / 2) * PX, z0 = (cz - w / 2) * PX, z1 = (cz + w / 2) * PX, ya = y0 * PX, yb = (y0 + h) * PX;
        float cxp = cx * PX, czp = cz * PX;
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        // plan X
        qv(vc, m, n, x0, ya, czp, u0, v1, r, g, b, 0, 0, 1); qv(vc, m, n, x1, ya, czp, u1, v1, r, g, b, 0, 0, 1);
        qv(vc, m, n, x1, yb, czp, u1, v0, r, g, b, 0, 0, 1); qv(vc, m, n, x0, yb, czp, u0, v0, r, g, b, 0, 0, 1);
        qv(vc, m, n, x0, yb, czp, u0, v0, r, g, b, 0, 0, -1); qv(vc, m, n, x1, yb, czp, u1, v0, r, g, b, 0, 0, -1);
        qv(vc, m, n, x1, ya, czp, u1, v1, r, g, b, 0, 0, -1); qv(vc, m, n, x0, ya, czp, u0, v1, r, g, b, 0, 0, -1);
        // plan Z
        qv(vc, m, n, cxp, ya, z0, u0, v1, r, g, b, 1, 0, 0); qv(vc, m, n, cxp, ya, z1, u1, v1, r, g, b, 1, 0, 0);
        qv(vc, m, n, cxp, yb, z1, u1, v0, r, g, b, 1, 0, 0); qv(vc, m, n, cxp, yb, z0, u0, v0, r, g, b, 1, 0, 0);
        qv(vc, m, n, cxp, yb, z0, u0, v0, r, g, b, -1, 0, 0); qv(vc, m, n, cxp, yb, z1, u1, v0, r, g, b, -1, 0, 0);
        qv(vc, m, n, cxp, ya, z1, u1, v1, r, g, b, -1, 0, 0); qv(vc, m, n, cxp, ya, z0, u0, v1, r, g, b, -1, 0, 0);
    }

    private static void qv(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float r, float g, float b, float nx, float ny, float nz) {
        vertex(vc, m, n, x, y, z, u, v, r, g, b, nx, ny, nz);
    }
}
