package com.minenorth_harvest.network;

import com.minenorth_harvest.chop.ChopManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record C2SStartChop(BlockPos pos) {
    public static void encode(C2SStartChop msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
    }

    public static C2SStartChop decode(FriendlyByteBuf buf) {
        return new C2SStartChop(buf.readBlockPos());
    }

    public static void handle(C2SStartChop msg, Supplier<NetworkEvent.Context> ctx) {
        ServerPlayer player = ctx.get().getSender();
        if (player != null) ChopManager.tryStart(player, msg.pos);
        ctx.get().setPacketHandled(true);
    }
}
