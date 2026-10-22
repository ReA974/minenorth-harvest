package com.minenorth_harvest.client;

import com.minenorth_harvest.network.C2SPump;
import com.minenorth_harvest.network.ModNetwork;
import com.minenorth_harvest.network.S2CPump;
import com.minenorth_harvest.zone.PumpMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Mini-jeu du cheval à bascule : un curseur traverse la barre à chaque manche, on appuie (Espace ou clic) quand il passe sur la cible verte.
 * L'écran n'affiche que ; le serveur juge chaque appui avec son horloge et paie le pétrole (voir PumpGame).
 */
public class PumpScreen extends Screen {
    private static final int W = 300, H = 196;
    private static final int PLAY = 0, WAIT = 1, DONE = 2;

    private static PumpScreen current;

    private final long seed;
    private final int rounds;
    private final int reserve;
    private final long startNanos = System.nanoTime();
    private final int[] rating;
    private int phase = PLAY;
    private boolean endSent;
    private long flashUntil;
    private int flashRating = -1;
    private int oil, perfect, good, miss;
    private boolean aborted;

    private PumpScreen(S2CPump m) {
        super(Component.literal("Pompage du pétrole"));
        this.seed = m.seed();
        this.rounds = Math.max(1, m.a());
        this.reserve = m.b();
        this.rating = new int[rounds];
        java.util.Arrays.fill(rating, -1);
    }

    /** Réception d'un paquet du serveur (thread client). */
    public static void onPacket(S2CPump m) {
        Minecraft mc = Minecraft.getInstance();
        switch (m.kind()) {
            case S2CPump.BEGIN -> {
                current = new PumpScreen(m);
                mc.setScreen(current);
            }
            case S2CPump.ACK -> {
                if (current != null && m.a() >= 0 && m.a() < current.rounds) current.rating[m.a()] = m.b();
            }
            case S2CPump.RESULT -> {
                if (current != null) current.finish(m.a(), m.b(), m.c(), m.d(), false);
            }
            case S2CPump.ABORT -> {
                if (current != null) current.finish(0, 0, 0, 0, true);
            }
            default -> { }
        }
    }

    private int elapsedMs() { return (int) ((System.nanoTime() - startNanos) / 1_000_000L); }

    private void finish(int oil, int perfect, int good, int miss, boolean aborted) {
        this.oil = oil; this.perfect = perfect; this.good = good; this.miss = miss; this.aborted = aborted;
        phase = DONE;
        rebuildWidgets();
    }

    @Override
    protected void init() {
        int x = (width - W) / 2, y = (height - H) / 2;
        if (phase == DONE) {
            addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(x + (W - 100) / 2, y + H - 30, 100, 20).build());
        } else if (phase == PLAY) {
            addRenderableWidget(Button.builder(Component.literal("Arrêter"), b -> stop()).bounds(x + (W - 100) / 2, y + H - 30, 100, 20).build());
        }
    }

    private void stop() {
        if (phase == PLAY) {
            phase = WAIT;
            rebuildWidgets();
        }
        sendEnd();
    }

    private void sendEnd() {
        if (endSent) return;
        endSent = true;
        ModNetwork.sendToServer(new C2SPump(1, 0));
    }

    @Override
    public void tick() {
        if (phase != DONE && elapsedMs() > PumpMath.totalMs(rounds) + 400) {
            if (phase == PLAY) { phase = WAIT; rebuildWidgets(); }
            sendEnd();
        }
    }

    private void press() {
        if (phase != PLAY) return;
        int ms = elapsedMs();
        int r = PumpMath.roundAt(ms, rounds);
        if (r < 0 || rating[r] >= 0) return;
        rating[r] = PumpMath.rate(seed, r, ms);
        flashRating = rating[r];
        flashUntil = System.currentTimeMillis() + 650;
        ModNetwork.sendToServer(new C2SPump(0, ms));
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_ENTER) { press(); return true; }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (phase == PLAY) { press(); return true; }
        return false;
    }

    /** Échap : arrête la partie (le pétrole déjà gagné est payé), puis ferme. */
    @Override
    public void onClose() {
        if (phase == PLAY) { stop(); return; }
        current = null;
        super.onClose();
    }

    @Override
    public void removed() {
        if (phase != DONE) sendEnd();
        if (current == this) current = null;
    }

    @Override
    public boolean isPauseScreen() { return false; }

    // ------------------------------------------------------------------ dessin

    static void rr(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            int inset = r - (int) Math.round(Math.sqrt(Math.max(0, r * r - (r - i - 0.5) * (r - i - 0.5))));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - i - 1, x + w - inset, y + h - i, color);
        }
        g.fill(x, y + r, x + w, y + h - r, color);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = (width - W) / 2, y = (height - H) / 2;
        rr(g, x - 1, y - 1, W + 2, H + 2, 12, 0x66FFFFFF);
        rr(g, x, y, W, H, 11, 0xF0141824);
        g.drawCenteredString(font, "Cheval à bascule", x + W / 2, y + 10, 0xFFFFC857);
        if (phase == DONE) { renderResult(g, x, y); super.render(g, mx, my, pt); return; }
        g.drawCenteredString(font, "Réserve du puits : " + reserve, x + W / 2, y + 24, 0xFF9AA3B5);

        int ms = elapsedMs();
        int round = PumpMath.roundAt(ms, rounds);
        int showRound = ms < PumpMath.LEAD_MS ? 0 : Math.max(0, Math.min(rounds - 1, round < 0 ? rounds - 1 : round));
        int bx = x + 20, bw = W - 40, by = y + 70, bh = 20;
        rr(g, bx - 2, by - 2, bw + 4, bh + 4, 6, 0xFF2B3144);
        rr(g, bx, by, bw, bh, 5, 0xFF0C0F18);
        double t = PumpMath.target(seed, showRound);
        int gw = (int) Math.round(PumpMath.GOOD_W * 2 * bw), pw = (int) Math.round(PumpMath.PERFECT_W * 2 * bw);
        int tc = bx + (int) Math.round(t * bw);
        g.fill(tc - gw / 2, by + 1, tc + gw / 2, by + bh - 1, 0xFF7A6A1E);
        g.fill(tc - pw / 2, by + 1, tc + pw / 2, by + bh - 1, 0xFF2FBF5B);
        if (ms >= PumpMath.LEAD_MS && round >= 0) {
            int mxp = bx + (int) Math.round(PumpMath.cursor(ms, round) * bw);
            g.fill(mxp - 1, by - 5, mxp + 2, by + bh + 5, 0xFFFFFFFF);
        }
        if (ms < PumpMath.LEAD_MS) {
            int n = Math.max(1, 3 - ms * 3 / PumpMath.LEAD_MS);
            g.pose().pushPose();
            g.pose().translate(x + W / 2f, y + 38, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawCenteredString(font, phase == PLAY ? "Prêt ?  " + n : "", 0, 0, 0xFFFFFFFF);
            g.pose().popPose();
        } else {
            g.drawCenteredString(font, "Manche " + (Math.min(rounds, showRound + 1)) + " / " + rounds, x + W / 2, y + 40, 0xFFFFFFFF);
        }
        // pastilles des manches
        int dot = 12, gap = 4, total = rounds * dot + (rounds - 1) * gap;
        int dx = x + (W - total) / 2, dy = y + 112;
        for (int i = 0; i < rounds; i++) {
            int c = rating[i] < 0 ? 0xFF3A4156 : rating[i] == PumpMath.PERFECT ? 0xFF2FBF5B : rating[i] == PumpMath.GOOD ? 0xFFE3B341 : 0xFFD24A4A;
            rr(g, dx + i * (dot + gap), dy, dot, dot, 4, c);
            if (i == round && phase == PLAY) g.renderOutline(dx + i * (dot + gap) - 1, dy - 1, dot + 2, dot + 2, 0xFFFFFFFF);
        }
        // retour sur le dernier appui
        if (System.currentTimeMillis() < flashUntil && flashRating >= 0) {
            String txt = flashRating == PumpMath.PERFECT ? "PARFAIT !" : flashRating == PumpMath.GOOD ? "Bien" : "Raté";
            int col = flashRating == PumpMath.PERFECT ? 0xFF2FBF5B : flashRating == PumpMath.GOOD ? 0xFFE3B341 : 0xFFD24A4A;
            g.drawCenteredString(font, txt, x + W / 2, y + 134, col);
        }
        g.drawCenteredString(font, phase == PLAY ? "Espace ou clic : pomper   ·   Échap : arrêter" : "Calcul du pétrole…", x + W / 2, y + H - 48, 0xFF9AA3B5);
        super.render(g, mx, my, pt);
    }

    private void renderResult(GuiGraphics g, int x, int y) {
        int cx = x + W / 2;
        if (aborted) {
            g.drawCenteredString(font, "Partie interrompue", cx, y + 60, 0xFFD24A4A);
            return;
        }
        g.drawCenteredString(font, oil > 0 ? "Pétrole brut obtenu" : "Pas une goutte…", cx, y + 44, 0xFFFFFFFF);
        g.pose().pushPose();
        g.pose().translate(cx, y + 62, 0);
        g.pose().scale(3f, 3f, 1f);
        g.drawCenteredString(font, String.valueOf(oil), 0, 0, oil > 0 ? 0xFFFFC857 : 0xFF9AA3B5);
        g.pose().popPose();
        g.drawCenteredString(font, "Parfaits : " + perfect + "   ·   Bien : " + good + "   ·   Ratés : " + miss, cx, y + 104, 0xFF9AA3B5);
        g.drawCenteredString(font, oil > 0 ? "Le pétrole est dans votre inventaire." : "Retentez votre chance dans un instant !", cx, y + 124, 0xFF9AA3B5);
    }
}
