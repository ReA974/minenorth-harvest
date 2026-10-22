package com.minenorth_harvest.network;

import com.minenorth_harvest.zone.OilAdmin;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Actions du menu admin des zones pétrole (le serveur exige le niveau d'opérateur 2 à chaque action). */
public record C2SOilAdmin(int action, String zone, BlockPos pos) {
    public static final int REFRESH = 0, CREATE = 1, DELETE = 2, TOGGLE_REGROW = 3, ADD_SITE = 4, REMOVE_SITE = 5, RESTORE = 6, SHOW = 7;

    public static void encode(C2SOilAdmin m, FriendlyByteBuf buf) {
        buf.writeByte(m.action);
        buf.writeUtf(m.zone, 64);
        buf.writeBlockPos(m.pos);
    }

    public static C2SOilAdmin decode(FriendlyByteBuf buf) {
        return new C2SOilAdmin(buf.readByte(), buf.readUtf(64), buf.readBlockPos());
    }

    public static void handle(C2SOilAdmin msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) OilAdmin.handle(p, msg);
        ctx.get().setPacketHandled(true);
    }
}
