package com.minenorth_harvest.client;

import com.minenorth_harvest.chop.ChopMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.IGuiOverlay;

/** HUD du mini-jeu « furie » : jauge qui se remplit à chaque coup, combo, et qui se vide si on ralentit. */
public final class ChopOverlay implements IGuiOverlay {
    public static final ChopOverlay INSTANCE = new ChopOverlay();

    private static final int BAR_W = 160;
    private static final int BAR_H = 12;

    @Override
    public void render(ForgeGui gui, GuiGraphics g, float partialTick, int sw, int sh) {
        Minecraft mc = Minecraft.getInstance();
        if (!ClientChopState.active || mc.level == null || mc.options.hideGui) return;
        Font font = mc.font;

        int x = (sw - BAR_W) / 2;
        int y = sh - 100;
        long age = ClientChopState.clientTicks - ClientChopState.resultClientTick;
        boolean hot = ClientChopState.combo >= 3;

        // léger tremblement quand le combo est chaud
        int shake = hot && age < 4 ? (int) (age % 2 == 0 ? 1 : -1) : 0;
        g.fill(x - 8 + shake, y - 20, x + BAR_W + 8 + shake, y + BAR_H + 22, 0xB0101010);
        g.fill(x - 8 + shake, y - 20, x + BAR_W + 8 + shake, y - 19, hot ? 0xFFFF7A18 : 0xFF8B5A2B);

        g.drawCenteredString(font, Component.translatable("hud.minenorth_harvest.title",
                ClientChopState.progress, ClientChopState.required), sw / 2, y - 14, 0xFFE8C07A);

        g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xFF000000);
        g.fill(x, y, x + BAR_W, y + BAR_H, 0xFF3A2818);
        int filled = ClientChopState.required <= 0 ? 0
                : Math.min(BAR_W, BAR_W * ClientChopState.progress / ClientChopState.required);
        int color = hot ? 0xFFFF7A18 : ClientChopState.combo >= 1 ? 0xFFFFC107 : 0xFFD2843C;
        g.fill(x, y, x + filled, y + BAR_H, color);
        g.fill(x, y, x + filled, y + 3, hot ? 0xFFFFD54F : 0xFFE8A85A);

        // combo : texte qui grossit avec la série
        if (ClientChopState.combo >= 1) {
            String txt = hot ? "\u00a76\u00a7l" + Component.translatable("hud.minenorth_harvest.fury").getString() + " x" + ClientChopState.combo
                    : Component.translatable("hud.minenorth_harvest.combo", Component.translatable("hud.minenorth_harvest.good"), ClientChopState.combo).getString();
            float scale = 1f + Math.min(0.8f, ClientChopState.combo * 0.06f) + (age < 3 ? 0.15f : 0f);
            g.pose().pushPose();
            g.pose().translate(sw / 2f, y - 32, 0);
            g.pose().scale(scale, scale, 1f);
            g.drawCenteredString(font, txt, 0, 0, hot ? 0xFFFF9A3C : 0xFFFFE08A);
            g.pose().popPose();
        }

        Component hint = ClientChopState.ecoMalus
                ? Component.translatable("hud.minenorth_harvest.eco_malus")
                : Component.translatable("hud.minenorth_harvest.hint");
        g.drawCenteredString(font, hint, sw / 2, y + BAR_H + 8, ClientChopState.ecoMalus ? 0xFFE57373 : 0xFFAAAAAA);
    }
}
