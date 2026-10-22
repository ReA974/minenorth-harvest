package com.minenorth_harvest.network;

import com.minenorth_harvest.zone.PumpGame;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client vers serveur, mini-jeu du cheval à bascule : 0 = appui (ms depuis le début de la partie), 1 = fin de partie. Le serveur revérifie tout. */
public record C2SPump(int kind, int ms) {
    public static void encode(C2SPump m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.writeVarInt(m.ms);
    }

    public static C2SPump decode(FriendlyByteBuf buf) {
        return new C2SPump(buf.readByte(), Math.max(0, Math.min(600_000, buf.readVarInt())));
    }

    public static void handle(C2SPump msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        if (p != null) {
            if (msg.kind == 0) PumpGame.press(p, msg.ms);
            else if (msg.kind == 1) PumpGame.end(p);
        }
        ctx.get().setPacketHandled(true);
    }
}
