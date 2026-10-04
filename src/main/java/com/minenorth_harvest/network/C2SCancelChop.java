package com.minenorth_harvest.network;

import com.minenorth_harvest.chop.ChopManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SCancelChop() {
    public static void encode(C2SCancelChop msg, FriendlyByteBuf buf) {}

    public static C2SCancelChop decode(FriendlyByteBuf buf) {
        return new C2SCancelChop();
    }

    public static void handle(C2SCancelChop msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) ChopManager.cancel(player, true);
        ctx.get().setPacketHandled(true);
    }
}
