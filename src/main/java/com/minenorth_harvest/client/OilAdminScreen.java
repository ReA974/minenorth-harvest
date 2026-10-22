package com.minenorth_harvest.client;

import com.minenorth_harvest.network.C2SOilAdmin;
import com.minenorth_harvest.network.ModNetwork;
import com.minenorth_harvest.network.S2COilAdmin;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Menu admin des zones pétrole (baguette de zone, accroupi + clic droit) : création d'une zone à partir de la sélection, chevaux à bascule de chaque
 * zone (ajout à sa position, retrait, remise en place), repousse, contour. Tout est revérifié par le serveur (opérateur niveau 2).
 */
public class OilAdminScreen extends Screen {
    private static final int W = 400, H = 250, ZONE_ROWS = 7, SITE_ROWS = 6;

    private S2COilAdmin data;
    private String selected = "";
    private int zonePage, sitePage;
    private String message = "";
    private long armedDelete;
    private String nameText = "";
    private EditBox nameBox;

    private OilAdminScreen(S2COilAdmin data) {
        super(Component.literal("Zones pétrole"));
        this.data = data;
        this.message = data.message();
    }

    /** Ouvre le menu ou met à jour celui qui est déjà ouvert. */
    public static void show(S2COilAdmin d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof OilAdminScreen s) {
            s.data = d;
            s.message = d.message();
            s.rebuildWidgets();
        } else {
            mc.setScreen(new OilAdminScreen(d));
        }
    }

    private S2COilAdmin.ZoneView zone() {
        for (S2COilAdmin.ZoneView z : data.zones()) if (z.name().equalsIgnoreCase(selected)) return z;
        return null;
    }

    private void send(int action, String zone, BlockPos pos) {
        ModNetwork.sendToServer(new C2SOilAdmin(action, zone, pos));
    }

    @Override
    protected void init() {
        if (zone() == null) selected = data.zones().isEmpty() ? "" : data.zones().get(0).name();
        int x = (width - W) / 2, y = (height - H) / 2;
        // colonne de gauche : les zones
        List<S2COilAdmin.ZoneView> zs = data.zones();
        int pages = Math.max(1, (zs.size() + ZONE_ROWS - 1) / ZONE_ROWS);
        zonePage = Math.min(zonePage, pages - 1);
        for (int i = 0; i < ZONE_ROWS; i++) {
            int idx = zonePage * ZONE_ROWS + i;
            if (idx >= zs.size()) break;
            String name = zs.get(idx).name();
            boolean sel = name.equalsIgnoreCase(selected);
            addRenderableWidget(Button.builder(Component.literal((sel ? "► " : "") + trim(name, 12)), b -> { selected = name; sitePage = 0; rebuildWidgets(); })
                    .bounds(x + 10, y + 34 + i * 20, 118, 18).build());
        }
        if (pages > 1) {
            addRenderableWidget(Button.builder(Component.literal("▲"), b -> { zonePage = Math.max(0, zonePage - 1); rebuildWidgets(); }).bounds(x + 10, y + 176, 57, 16).build());
            addRenderableWidget(Button.builder(Component.literal("▼"), b -> { zonePage = Math.min(pages - 1, zonePage + 1); rebuildWidgets(); }).bounds(x + 71, y + 176, 57, 16).build());
        }
        // détails de la zone choisie
        S2COilAdmin.ZoneView z = zone();
        int rx = x + 140, rw = W - 150;
        if (z != null) {
            addRenderableWidget(Button.builder(Component.literal("Repousse : " + (z.regrow() ? "oui" : "non")), b -> send(C2SOilAdmin.TOGGLE_REGROW, z.name(), BlockPos.ZERO))
                    .bounds(rx + rw - 110, y + 30, 110, 16).build());
            List<S2COilAdmin.SiteView> sites = z.sites();
            int sp = Math.max(1, (sites.size() + SITE_ROWS - 1) / SITE_ROWS);
            sitePage = Math.min(sitePage, sp - 1);
            for (int i = 0; i < SITE_ROWS; i++) {
                int idx = sitePage * SITE_ROWS + i;
                if (idx >= sites.size()) break;
                BlockPos pos = sites.get(idx).pos();
                addRenderableWidget(Button.builder(Component.literal("✖"), b -> send(C2SOilAdmin.REMOVE_SITE, z.name(), pos))
                        .bounds(rx + rw - 18, y + 70 + i * 17, 18, 15).build());
            }
            if (sp > 1) {
                addRenderableWidget(Button.builder(Component.literal("▲"), b -> { sitePage = Math.max(0, sitePage - 1); rebuildWidgets(); }).bounds(rx, y + 174, 40, 14).build());
                addRenderableWidget(Button.builder(Component.literal("▼"), b -> { sitePage = Math.min(sp - 1, sitePage + 1); rebuildWidgets(); }).bounds(rx + 44, y + 174, 40, 14).build());
            }
            int bw = (rw - 6) / 2;
            addRenderableWidget(Button.builder(Component.literal("+ Cheval ici"), b -> send(C2SOilAdmin.ADD_SITE, z.name(), BlockPos.ZERO)).bounds(rx, y + 140, bw, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Remettre en place"), b -> send(C2SOilAdmin.RESTORE, z.name(), BlockPos.ZERO)).bounds(rx + bw + 6, y + 140, bw, 18).build());
            addRenderableWidget(Button.builder(Component.literal("Afficher le contour"), b -> send(C2SOilAdmin.SHOW, z.name(), BlockPos.ZERO)).bounds(rx + rw - bw - 0, y + 174, bw, 14).build());
            boolean armed = System.currentTimeMillis() < armedDelete;
            addRenderableWidget(Button.builder(Component.literal(armed ? "Confirmer la suppression" : "Supprimer la zone"), b -> {
                if (System.currentTimeMillis() < armedDelete) { armedDelete = 0; send(C2SOilAdmin.DELETE, z.name(), BlockPos.ZERO); }
                else { armedDelete = System.currentTimeMillis() + 4000; rebuildWidgets(); }
            }).bounds(rx, y + 192, rw, 16).build());
        }
        // création d'une zone à partir de la sélection de la baguette
        nameBox = new EditBox(font, x + 10, y + H - 26, 150, 16, Component.literal("Nom"));
        nameBox.setMaxLength(24);
        nameBox.setValue(nameText);
        nameBox.setResponder(s -> nameText = s);
        nameBox.setHint(Component.literal("Nom de la nouvelle zone"));
        addRenderableWidget(nameBox);
        addRenderableWidget(Button.builder(Component.literal("Créer avec ma sélection"), b -> {
            send(C2SOilAdmin.CREATE, nameText, BlockPos.ZERO);
            nameText = "";
        }).bounds(x + 166, y + H - 27, 130, 18).build());
        addRenderableWidget(Button.builder(Component.literal("Fermer"), b -> onClose()).bounds(x + W - 90, y + H - 27, 80, 18).build());
    }

    private static String trim(String s, int n) { return s.length() <= n ? s : s.substring(0, n - 1) + "…"; }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0x66000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        int x = (width - W) / 2, y = (height - H) / 2;
        PumpScreen.rr(g, x - 1, y - 1, W + 2, H + 2, 12, 0x66FFFFFF);
        PumpScreen.rr(g, x, y, W, H, 11, 0xF0141824);
        g.drawString(font, "Zones pétrole", x + 12, y + 12, 0xFFFFC857, false);
        g.drawString(font, "Baguette de zone : accroupi + clic droit", x + W - 12 - font.width("Baguette de zone : accroupi + clic droit"), y + 12, 0xFF6F7890, false);
        S2COilAdmin.ZoneView z = zone();
        int rx = x + 140, rw = W - 150;
        if (data.zones().isEmpty()) {
            g.drawString(font, "Aucune zone pétrole.", x + 12, y + 40, 0xFF9AA3B5, false);
            g.drawString(font, "1. Sélectionnez deux coins avec la baguette.", x + 12, y + 56, 0xFF9AA3B5, false);
            g.drawString(font, "2. Nommez la zone et créez-la ci-dessous.", x + 12, y + 68, 0xFF9AA3B5, false);
        }
        if (z != null) {
            g.drawString(font, trim(z.name(), 22) + "  ·  " + z.volume() + " blocs", rx, y + 34, 0xFFFFFFFF, false);
            g.drawString(font, "Chevaux à bascule (" + z.sites().size() + ")", rx, y + 56, 0xFF9AA3B5, false);
            List<S2COilAdmin.SiteView> sites = z.sites();
            for (int i = 0; i < SITE_ROWS; i++) {
                int idx = sitePage * SITE_ROWS + i;
                if (idx >= sites.size()) break;
                S2COilAdmin.SiteView s = sites.get(idx);
                String dir = switch (Direction.from3DDataValue(s.facing())) { case NORTH -> "N"; case SOUTH -> "S"; case EAST -> "E"; default -> "O"; };
                String line = s.pos().getX() + " " + s.pos().getY() + " " + s.pos().getZ() + "  " + dir;
                g.drawString(font, line, rx, y + 74 + i * 17, 0xFFFFFFFF, false);
                g.drawString(font, s.present() ? "en place" : "absent", rx + 110, y + 74 + i * 17, s.present() ? 0xFF2FBF5B : 0xFFE3B341, false);
            }
            if (sites.isEmpty()) g.drawString(font, "Placez-vous dans la zone puis « + Cheval ici ».", rx, y + 74, 0xFF6F7890, false);
        }
        if (!message.isEmpty()) {
            Component m = Component.literal(message);
            int mw = font.width(m);
            g.drawString(font, m, x + (W - Math.min(mw, W - 20)) / 2, y + H - 42, 0xFFFFFFFF, false);
        }
        super.render(g, mx, my, pt);
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
