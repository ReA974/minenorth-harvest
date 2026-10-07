package com.minenorth_harvest.zone;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;

/** Zone cuboïde définie par un admin. */
public class Zone {
    public final String name;
    public final ZoneType type;
    public final BlockPos min;
    public final BlockPos max;
    /** BUCHERON : replantation automatique des souches oubliées. */
    public boolean regrow = true;
    /** VERGER : multiplicateur propre à la zone (<= 0 = valeur de la config). */
    public double speed = 0;

    public Zone(String name, ZoneType type, BlockPos a, BlockPos b) {
        this.name = name;
        this.type = type;
        this.min = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        this.max = new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()));
    }

    /** Les zones de chasse ignorent la hauteur (comme le script Skript d'origine). */
    public boolean ignoresHeight() {
        return type == ZoneType.CHASSE;
    }

    public boolean contains(BlockPos p) {
        return p.getX() >= min.getX() && p.getX() <= max.getX()
                && (ignoresHeight() || (p.getY() >= min.getY() && p.getY() <= max.getY()))
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ();
    }

    public boolean contains(double x, double y, double z) {
        return x >= min.getX() && x < max.getX() + 1
                && (ignoresHeight() || (y >= min.getY() && y < max.getY() + 1))
                && z >= min.getZ() && z < max.getZ() + 1;
    }

    public long volume() {
        return (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putString("name", name);
        t.putString("type", type.id());
        t.put("min", NbtUtils.writeBlockPos(min));
        t.put("max", NbtUtils.writeBlockPos(max));
        t.putBoolean("regrow", regrow);
        t.putDouble("speed", speed);
        return t;
    }

    public static Zone load(CompoundTag t) {
        ZoneType type = ZoneType.byId(t.getString("type"));
        if (type == null) type = ZoneType.BUCHERON;
        Zone z = new Zone(t.getString("name"), type, NbtUtils.readBlockPos(t.getCompound("min")), NbtUtils.readBlockPos(t.getCompound("max")));
        z.regrow = !t.contains("regrow") || t.getBoolean("regrow");
        z.speed = t.getDouble("speed");
        return z;
    }
}
