package com.minenorth_harvest.zone;

import com.minenorth_harvest.block.PumpjackBlock;
import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * Chevaux à bascule des zones pétrole : chaque emplacement défini par l'admin (menu de la baguette de zone) porte toujours un cheval. S'il est retiré
 * (admin en créatif), il réapparaît après oilRegenDelaySeconds quand la repousse de la zone est active. Aucun joueur ne peut en poser ni en casser.
 */
public final class PumpSites {
    private PumpSites() {}

    /** Appelé une fois par seconde et par dimension. */
    public static void tick(ServerLevel level, ZoneData data) {
        long now = level.getGameTime();
        boolean changed = seedZones(level, data);
        if (data.sites().isEmpty()) { if (changed) data.setDirty(); return; }
        for (ZoneData.Site s : data.sites()) {
            Zone z = data.byName(s.zone);
            if (z == null || z.type != ZoneType.PETROLE || !level.hasChunkAt(s.pos)) continue;
            if (level.getBlockState(s.pos).is(ModBlocks.PUMPJACK.get())) {
                if (s.respawnAt != 0) { s.respawnAt = 0; changed = true; }
                continue;
            }
            if (s.placedOnce && !z.regrow) continue;   // repousse désactivée : un cheval retiré ne revient pas
            if (s.respawnAt == 0) {
                s.respawnAt = s.placedOnce ? now + HarvestConfig.OIL_REGEN_DELAY.get() * 20L : now;
                changed = true;
            }
            if (now >= s.respawnAt) {
                if (PumpjackBlock.build(level, s.pos, s.facing)) {
                    s.placedOnce = true;
                    s.respawnAt = 0;
                } else {
                    s.respawnAt = now + 1200;   // la place est prise : on réessaie dans une minute
                }
                changed = true;
            }
        }
        if (changed) data.setDirty();
    }

    /** Zones pétrole sans cheval (créées avant la refonte) : un premier cheval est posé près du centre de la zone, au sol. */
    private static boolean seedZones(ServerLevel level, ZoneData data) {
        boolean changed = false;
        for (Zone z : data.zones()) {
            if (z.type != ZoneType.PETROLE || z.seeded) continue;
            if (!data.sitesIn(z).isEmpty()) { z.seeded = true; changed = true; continue; }
            int cx = (z.min.getX() + z.max.getX()) / 2, cz = (z.min.getZ() + z.max.getZ()) / 2;
            int[][] tries = {{0, 0}, {4, 0}, {-4, 0}, {0, 4}, {0, -4}, {8, 8}, {-8, 8}, {8, -8}, {-8, -8}};
            for (int[] t : tries) {
                int x = cx + t[0], zz = cz + t[1];
                if (x < z.min.getX() || x > z.max.getX() || zz < z.min.getZ() || zz > z.max.getZ()) continue;
                BlockPos at = new BlockPos(x, 0, zz);
                if (!level.hasChunkAt(at)) break;
                int y = Math.max(z.min.getY(), Math.min(z.max.getY(), level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, zz)));
                BlockPos pos = new BlockPos(x, y, zz);
                if (!z.contains(pos)) continue;
                ZoneData.Site site = new ZoneData.Site(pos, net.minecraft.core.Direction.SOUTH, z.name);
                if (com.minenorth_harvest.block.PumpjackBlock.build(level, pos, site.facing)) {
                    site.placedOnce = true;
                    data.addSite(site);
                    z.seeded = true;
                    changed = true;
                    break;
                }
            }
        }
        return changed;
    }

    /** Remet tout de suite en place les chevaux manquants d'une zone. Renvoie le nombre de chevaux posés. */
    public static int restoreNow(ServerLevel level, Zone zone) {
        ZoneData data = ZoneData.get(level);
        int placed = 0;
        for (ZoneData.Site s : data.sitesIn(zone)) {
            if (!level.hasChunkAt(s.pos) || level.getBlockState(s.pos).is(ModBlocks.PUMPJACK.get())) continue;
            if (PumpjackBlock.build(level, s.pos, s.facing)) {
                s.placedOnce = true;
                s.respawnAt = 0;
                placed++;
            }
        }
        data.setDirty();
        return placed;
    }

    /** Ajoute un emplacement et pose le cheval. Renvoie faux si la place n'est pas libre (l'emplacement est quand même gardé et réessayé). */
    public static boolean add(ServerLevel level, Zone zone, BlockPos pos, net.minecraft.core.Direction facing) {
        ZoneData data = ZoneData.get(level);
        ZoneData.Site site = new ZoneData.Site(pos, facing, zone.name);
        data.addSite(site);
        boolean ok = PumpjackBlock.build(level, pos, facing);
        if (ok) site.placedOnce = true; else site.respawnAt = level.getGameTime() + 1200;
        data.setDirty();
        return ok;
    }

    /** Retire l'emplacement et démonte le cheval. */
    public static boolean remove(ServerLevel level, BlockPos pos) {
        ZoneData data = ZoneData.get(level);
        ZoneData.Site s = data.siteAt(pos);
        if (s == null) return false;
        data.removeSite(pos);
        PumpjackBlock.demolish(level, pos);
        return true;
    }
}
