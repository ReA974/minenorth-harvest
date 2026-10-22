package com.minenorth_harvest.network;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Serveur vers client, mini-jeu du cheval à bascule : 0 = début (graine, manches, réserve), 1 = note d'un appui, 2 = résultat, 3 = partie interrompue. */
public record S2CPump(int kind, BlockPos pos, long seed, int a, int b, int c, int d) {
    public static final int BEGIN = 0, ACK = 1, RESULT = 2, ABORT = 3;

    public static S2CPump begin(BlockPos pos, long seed, int rounds, int reserve) { return new S2CPump(BEGIN, pos, seed, rounds, reserve, 0, 0); }
    public static S2CPump ack(int round, int rating) { return new S2CPump(ACK, BlockPos.ZERO, 0, round, rating, 0, 0); }
    public static S2CPump result(int oil, int perfect, int good, int miss) { return new S2CPump(RESULT, BlockPos.ZERO, 0, oil, perfect, good, miss); }
    public static S2CPump abort() { return new S2CPump(ABORT, BlockPos.ZERO, 0, 0, 0, 0, 0); }

    public static void encode(S2CPump m, FriendlyByteBuf buf) {
        buf.writeByte(m.kind);
        buf.writeBlockPos(m.pos);
        buf.writeLong(m.seed);
        buf.writeVarInt(m.a);
        buf.writeVarInt(m.b);
        buf.writeVarInt(m.c);
        buf.writeVarInt(m.d);
    }

    public static S2CPump decode(FriendlyByteBuf buf) {
        return new S2CPump(buf.readByte(), buf.readBlockPos(), buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(S2CPump msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.minenorth_harvest.client.PumpScreen.onPacket(msg));
        ctx.get().setPacketHandled(true);
    }
}
