package com.minenorth_harvest.client;

import com.minenorth_harvest.chop.ChopMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** HUD du mini-jeu : barre avec zone verte / dorée et curseur qui fait des allers-retours. */
public final class ChopOverlay implements IGuiOverlay {
    public static final ChopOverlay INSTANCE = new ChopOverlay();

    private static final int BAR_W = 160;
    private static final int BAR_H = 10;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientChopState.active || mc.level == null || mc.options.hideGui) return;
        Font font = mc.font;

        int x = (sw - BAR_W) / 2;
        int y = sh - 100;

        // Panneau
        g.fill(x - 8, y - 20, x + BAR_W + 8, y + BAR_H + 24, 0xB0101010);
        g.fill(x - 8, y - 20, x + BAR_W + 8, y - 19, 0xFF8B5A2B);

        // Titre
        Component title = Component.translatable("hud.minenorth_harvest.title",
                ClientChopState.progress, ClientChopState.required);
        g.drawCenteredString(font, title, sw / 2, y - 14, 0xFFE8C07A);

        // Barre
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xFF000000);
        g.fill(x, y, x + BAR_W, y + BAR_H, 0xFF4A3220);

        int zx1 = x + Math.round((ClientChopState.zoneCenter - ClientChopState.zoneWidth / 2f) * BAR_W);
        int zx2 = x + Math.round((ClientChopState.zoneCenter + ClientChopState.zoneWidth / 2f) * BAR_W);
        g.fill(zx1, y, zx2, y + BAR_H, 0xFF43A047);

        int px1 = x + Math.round((ClientChopState.zoneCenter - ClientChopState.perfectWidth / 2f) * BAR_W);
        int px2 = x + Math.round((ClientChopState.zoneCenter + ClientChopState.perfectWidth / 2f) * BAR_W);
        if (px2 <= px1) px2 = px1 + 1;
        g.fill(px1, y, px2, y + BAR_H, 0xFFFFD54F);

        // Curseur
        double time = mc.level.getGameTime() + partialTick;
        float cursor = ClientChopState.cursor(time);
        int cx = x + Math.round(cursor * BAR_W);
        g.fill(cx - 2, y - 3, cx + 2, y + BAR_H + 3, 0xFF000000);
        g.fill(cx - 1, y - 2, cx + 1, y + BAR_H + 2, 0xFFFFFFFF);

        // Progression
        int py = y + BAR_H + 4;
        g.fill(x, py, x + BAR_W, py + 3, 0xFF2A2A2A);
        int filled = ClientChopState.required <= 0 ? 0
                : Math.min(BAR_W, BAR_W * ClientChopState.progress / ClientChopState.required);
        g.fill(x, py, x + filled, py + 3, 0xFFD2843C);

        // Résultat du dernier coup (fondu)
        long age = ClientChopState.clientTicks - ClientChopState.resultClientTick;
        if (age >= 0 && age < 20 && ClientChopState.lastResult != ChopMath.RESULT_NONE) {
            int alpha = (int) (255 * (1f - age / 20f));
            if (alpha > 8) {
                String key;
                int color;
                switch (ClientChopState.lastResult) {
                    case ChopMath.RESULT_PERFECT -> { key = "hud.minenorth_harvest.perfect"; color = 0xFFD54F; }
                    case ChopMath.RESULT_GOOD -> { key = "hud.minenorth_harvest.good"; color = 0x7CD67F; }
                    default -> { key = "hud.minenorth_harvest.miss"; color = 0xE57373; }
                }
                Component txt = ClientChopState.lastResult == ChopMath.RESULT_PERFECT && ClientChopState.combo > 1
                        ? Component.translatable("hud.minenorth_harvest.combo", Component.translatable(key), ClientChopState.combo)
                        : Component.translatable(key);
                g.drawCenteredString(font, txt, sw / 2, y - 32 - (int) (age / 2), (alpha << 24) | color);
            }
        }

        // Aide
        Component hint = ClientChopState.ecoMalus
                ? Component.translatable("hud.minenorth_harvest.eco_malus")
                : Component.translatable("hud.minenorth_harvest.hint");
        g.drawCenteredString(font, hint, sw / 2, py + 6, ClientChopState.ecoMalus ? 0xFFE57373 : 0xFFAAAAAA);
    }
}
