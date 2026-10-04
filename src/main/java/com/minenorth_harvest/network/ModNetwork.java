package com.minenorth_harvest.network;

import com.minenorth_harvest.MineNorthHarvest;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

public final class ModNetwork {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(MineNorthHarvest.MODID, "main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static boolean registered;

    private ModNetwork() {}

    public static synchronized void register() {
        if (registered) return;
        registered = true;
        int id = 0;
        CHANNEL.messageBuilder(C2SStartChop.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SStartChop::encode).decoder(C2SStartChop::decode)
                .consumerMainThread(C2SStartChop::handle).add();
        CHANNEL.messageBuilder(C2SSwing.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SSwing::encode).decoder(C2SSwing::decode)
                .consumerMainThread(C2SSwing::handle).add();
        CHANNEL.messageBuilder(C2SCancelChop.class, id++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(C2SCancelChop::encode).decoder(C2SCancelChop::decode)
                .consumerMainThread(C2SCancelChop::handle).add();
        CHANNEL.messageBuilder(S2CChopState.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CChopState::encode).decoder(S2CChopState::decode)
                .consumerMainThread(S2CChopState::handle).add();
        CHANNEL.messageBuilder(S2CSyncConfig.class, id++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(S2CSyncConfig::encode).decoder(S2CSyncConfig::decode)
                .consumerMainThread(S2CSyncConfig::handle).add();
    }

    public static void sendTo(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void sendToAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }

    public static void sendToServer(Object msg) {
        CHANNEL.sendToServer(msg);
    }
}
