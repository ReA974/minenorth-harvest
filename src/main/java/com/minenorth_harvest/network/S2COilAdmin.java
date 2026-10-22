package com.minenorth_harvest.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** Données du menu admin des zones pétrole : les zones de la dimension du joueur, leurs emplacements de chevaux et leur état. */
public record S2COilAdmin(List<ZoneView> zones, String message) {
    public record SiteView(BlockPos pos, int facing, boolean present) {}

    public record ZoneView(String name, boolean regrow, long volume, List<SiteView> sites) {}

    public static void encode(S2COilAdmin m, FriendlyByteBuf buf) {
        buf.writeUtf(m.message, 200);
        buf.writeVarInt(m.zones.size());
        for (ZoneView z : m.zones) {
            buf.writeUtf(z.name, 64);
            buf.writeBoolean(z.regrow);
            buf.writeVarLong(z.volume);
            buf.writeVarInt(z.sites.size());
            for (SiteView s : z.sites) {
                buf.writeBlockPos(s.pos);
                buf.writeByte(s.facing);
                buf.writeBoolean(s.present);
            }
        }
    }

    public static S2COilAdmin decode(FriendlyByteBuf buf) {
        String message = buf.readUtf(200);
        int n = Math.min(64, buf.readVarInt());
        List<ZoneView> zones = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            String name = buf.readUtf(64);
            boolean regrow = buf.readBoolean();
            long volume = buf.readVarLong();
            int k = Math.min(64, buf.readVarInt());
            List<SiteView> sites = new ArrayList<>();
            for (int j = 0; j < k; j++) sites.add(new SiteView(buf.readBlockPos(), buf.readByte(), buf.readBoolean()));
            zones.add(new ZoneView(name, regrow, volume, sites));
        }
        return new S2COilAdmin(zones, message);
    }

    public static void handle(S2COilAdmin msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.minenorth_harvest.client.OilAdminScreen.show(msg));
        ctx.get().setPacketHandled(true);
    }
}
