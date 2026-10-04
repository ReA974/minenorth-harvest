package com.minenorth_harvest.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CChopState(boolean active, int progress, int required, int combo,
                           float zoneCenter, float zoneWidth, float perfectWidth,
                           int period, double startTick, byte lastResult, int resultSeq, boolean ecoMalus) {

    public static S2CChopState inactive() {
        return new S2CChopState(false, 0, 0, 0, 0f, 0f, 0f, 0, 0.0, (byte) 0, 0, false);
    }

    public static void encode(S2CChopState m, FriendlyByteBuf buf) {
        buf.writeBoolean(m.active);
        buf.writeVarInt(m.progress);
        buf.writeVarInt(m.required);
        buf.writeVarInt(m.combo);
        buf.writeFloat(m.zoneCenter);
        buf.writeFloat(m.zoneWidth);
        buf.writeFloat(m.perfectWidth);
        buf.writeVarInt(m.period);
        buf.writeDouble(m.startTick);
        buf.writeByte(m.lastResult);
        buf.writeVarInt(m.resultSeq);
        buf.writeBoolean(m.ecoMalus);
    }

    public static S2CChopState decode(FriendlyByteBuf buf) {
        return new S2CChopState(buf.readBoolean(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readVarInt(), buf.readDouble(),
                buf.readByte(), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(S2CChopState msg, Supplier<NetworkEvent.Context> ctx) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.minenorth_harvest.client.ClientChopState.apply(msg));
        ctx.get().setPacketHandled(true);
    }
}
