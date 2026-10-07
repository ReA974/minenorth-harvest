package com.minenorth_harvest.zone;

import com.minenorth_harvest.MineNorthHarvest;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Zones + souches en attente de repousse, sauvegardées par dimension (data/minenorth_harvest_zones.dat). */
public class ZoneData extends SavedData {
    private static final String NAME = MineNorthHarvest.MODID + "_zones";

    public record Regrow(BlockPos pos, String sapling, long due) {}

    private final Map<String, Zone> zones = new LinkedHashMap<>();
    private final List<Regrow> regrows = new ArrayList<>();

    public static ZoneData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ZoneData::load, ZoneData::new, NAME);
    }

    public Collection<Zone> zones() { return zones.values(); }

    @Nullable
    public Zone byName(String name) { return zones.get(name.toLowerCase()); }

    public void add(Zone zone) {
        zones.put(zone.name.toLowerCase(), zone);
        setDirty();
    }

    public boolean remove(String name) {
        boolean r = zones.remove(name.toLowerCase()) != null;
        if (r) setDirty();
        return r;
    }

    @Nullable
    public Zone zoneAt(BlockPos pos, ZoneType type) {
        for (Zone z : zones.values()) if (z.type == type && z.contains(pos)) return z;
        return null;
    }

    @Nullable
    public Zone zoneAt(double x, double y, double z) {
        for (Zone zone : zones.values()) if (zone.contains(x, y, z)) return zone;
        return null;
    }

    /** Toutes les zones contenant cette position (elles peuvent se chevaucher). */
    public List<Zone> zonesAt(double x, double y, double z) {
        List<Zone> list = new ArrayList<>();
        for (Zone zone : zones.values()) if (zone.contains(x, y, z)) list.add(zone);
        return list;
    }

    public List<Regrow> regrows() { return regrows; }

    public void addRegrow(BlockPos pos, String sapling, long due) {
        regrows.removeIf(r -> r.pos.equals(pos));
        regrows.add(new Regrow(pos.immutable(), sapling, due));
        setDirty();
    }

    // ---- NBT ----

    public static ZoneData load(CompoundTag tag) {
        ZoneData d = new ZoneData();
        ListTag list = tag.getList("zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            Zone z = Zone.load(list.getCompound(i));
            d.zones.put(z.name.toLowerCase(), z);
        }
        ListTag rl = tag.getList("regrows", Tag.TAG_COMPOUND);
        for (int i = 0; i < rl.size(); i++) {
            CompoundTag r = rl.getCompound(i);
            d.regrows.add(new Regrow(NbtUtils.readBlockPos(r.getCompound("pos")), r.getString("sapling"), r.getLong("due")));
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone z : zones.values()) list.add(z.save());
        tag.put("zones", list);
        ListTag rl = new ListTag();
        for (Regrow r : regrows) {
            CompoundTag t = new CompoundTag();
            t.put("pos", NbtUtils.writeBlockPos(r.pos));
            t.putString("sapling", r.sapling);
            t.putLong("due", r.due);
            rl.add(t);
        }
        tag.put("regrows", rl);
        return tag;
    }
}
