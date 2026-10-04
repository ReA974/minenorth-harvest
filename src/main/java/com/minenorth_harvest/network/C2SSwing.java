package com.minenorth_harvest.network;

import com.minenorth_harvest.chop.ChopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SSwing(float cursor) {
    public static void encode(C2SSwing msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.cursor);
    }

    public static C2SSwing decode(FriendlyByteBuf buf) {
        return new C2SSwing(buf.readFloat());
    }

    public static void handle(C2SSwing msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) ChopManager.swing(player, msg.cursor);
        ctx.get().setPacketHandled(true);
    }
}
