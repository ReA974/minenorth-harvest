package com.minenorth_harvest.network;

import com.minenorth_harvest.config.HarvestConfig;
import com.minenorth_harvest.config.SyncedConfig;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record S2CSyncConfig(double manualLogSpeed, boolean chopEnabled, boolean bonemealFruits) {
    public static S2CSyncConfig fromConfig() {
        return new S2CSyncConfig(HarvestConfig.MANUAL_LOG_SPEED.get(), HarvestConfig.CHOP_ENABLED.get(),
                HarvestConfig.BONEMEAL_FRUITS.get());
    }

    public static void encode(S2CSyncConfig m, FriendlyByteBuf buf) {
        buf.writeDouble(m.manualLogSpeed);
        buf.writeBoolean(m.chopEnabled);
        buf.writeBoolean(m.bonemealFruits);
    }

    public static S2CSyncConfig decode(FriendlyByteBuf buf) {
        return new S2CSyncConfig(buf.readDouble(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(S2CSyncConfig m, Supplier<NetworkEvent.Context> ctx) {
        SyncedConfig.clientManualLogSpeed = m.manualLogSpeed;
        SyncedConfig.clientChopEnabled = m.chopEnabled;
        SyncedConfig.clientBonemealFruits = m.bonemealFruits;
        ctx.get().setPacketHandled(true);
    }
}
