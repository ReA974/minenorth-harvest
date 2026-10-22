package com.minenorth_harvest.zone;

import com.minenorth_harvest.network.C2SOilAdmin;
import com.minenorth_harvest.network.ModNetwork;
import com.minenorth_harvest.network.S2COilAdmin;
import com.minenorth_harvest.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** Menu admin des zones pétrole (ouvert avec la baguette de zone, accroupi + clic droit) : tout se règle en jeu, sans commande. Opérateur niveau 2 exigé. */
public final class OilAdmin {
    private OilAdmin() {}

    private static final int MAX_NAME = 24;

    public static void open(ServerPlayer p) {
        if (!p.hasPermissions(2)) return;
        send(p, "");
    }

    public static void handle(ServerPlayer p, C2SOilAdmin m) {
        if (!p.hasPermissions(2)) return;
        ServerLevel level = p.serverLevel();
        ZoneData data = ZoneData.get(level);
        String msg = "";
        Zone zone = m.zone().isEmpty() ? null : data.byName(m.zone());
        switch (m.action()) {
            case C2SOilAdmin.REFRESH -> { }
            case C2SOilAdmin.CREATE -> msg = create(p, data, m.zone());
            case C2SOilAdmin.DELETE -> {
                if (zone == null || zone.type != ZoneType.PETROLE) msg = "§cZone introuvable.";
                else {
                    for (ZoneData.Site s : new ArrayList<>(data.sitesIn(zone))) PumpSites.remove(level, s.pos);
                    data.remove(zone.name);
                    msg = "§aZone « " + zone.name + " » supprimée (chevaux démontés).";
                }
            }
            case C2SOilAdmin.TOGGLE_REGROW -> {
                if (zone == null || zone.type != ZoneType.PETROLE) msg = "§cZone introuvable.";
                else {
                    zone.regrow = !zone.regrow;
                    data.setDirty();
                    msg = zone.regrow ? "§aRepousse activée : un cheval retiré reviendra." : "§eRepousse désactivée : un cheval retiré ne reviendra pas.";
                }
            }
            case C2SOilAdmin.ADD_SITE -> {
                if (zone == null || zone.type != ZoneType.PETROLE) msg = "§cZone introuvable.";
                else {
                    BlockPos at = p.blockPosition();
                    if (!zone.contains(at)) msg = "§cVous devez vous tenir DANS la zone pour y ajouter un cheval.";
                    else if (data.sitesIn(zone).size() >= 60) msg = "§cTrop de chevaux dans cette zone (60 max).";
                    else {
                        boolean ok = PumpSites.add(level, zone, at, p.getDirection().getOpposite());
                        msg = ok ? "§aCheval à bascule posé à votre position." : "§eEmplacement enregistré, mais la place n'est pas libre (3×3×3) : il sera posé dès que possible.";
                    }
                }
            }
            case C2SOilAdmin.REMOVE_SITE -> msg = PumpSites.remove(level, m.pos()) ? "§aCheval retiré." : "§cEmplacement introuvable.";
            case C2SOilAdmin.RESTORE -> {
                if (zone == null) msg = "§cZone introuvable.";
                else msg = "§a" + PumpSites.restoreNow(level, zone) + " cheval(aux) remis en place.";
            }
            case C2SOilAdmin.SHOW -> {
                if (zone != null) { ZoneManager.show(p, zone, 15); msg = "§7Contour affiché 15 s."; }
            }
            default -> { }
        }
        send(p, msg);
    }

    private static String create(ServerPlayer p, ZoneData data, String raw) {
        String name = raw.trim().replaceAll("[^A-Za-z0-9_-]", "");
        if (name.length() > MAX_NAME) name = name.substring(0, MAX_NAME);
        if (name.isEmpty()) return "§cDonnez un nom (lettres, chiffres, - et _).";
        if (data.byName(name) != null) return "§cUne zone porte déjà ce nom.";
        ZoneSelection.Sel sel = ZoneSelection.get(p);
        if (sel == null || !sel.complete()) return "§cSélectionnez d'abord deux coins avec la baguette (clic gauche = coin 1, clic droit = coin 2).";
        Zone z = new Zone(name, ZoneType.PETROLE, sel.pos1, sel.pos2);
        z.seeded = true;   // l'admin place lui-même les chevaux
        data.add(z);
        ZoneManager.show(p, z, 10);
        return "§aZone pétrole « " + name + " » créée (" + z.volume() + " blocs). Ajoutez-y des chevaux.";
    }

    private static void send(ServerPlayer p, String message) {
        ServerLevel level = p.serverLevel();
        ZoneData data = ZoneData.get(level);
        List<S2COilAdmin.ZoneView> zones = new ArrayList<>();
        for (Zone z : data.zones()) {
            if (z.type != ZoneType.PETROLE) continue;
            List<S2COilAdmin.SiteView> sites = new ArrayList<>();
            for (ZoneData.Site s : data.sitesIn(z)) {
                boolean present = level.hasChunkAt(s.pos) && level.getBlockState(s.pos).is(ModBlocks.PUMPJACK.get());
                sites.add(new S2COilAdmin.SiteView(s.pos, s.facing.get3DDataValue(), present));
                if (sites.size() >= 64) break;
            }
            zones.add(new S2COilAdmin.ZoneView(z.name, z.regrow, z.volume(), sites));
            if (zones.size() >= 64) break;
        }
        ModNetwork.sendTo(p, new S2COilAdmin(zones, message));
    }
}
