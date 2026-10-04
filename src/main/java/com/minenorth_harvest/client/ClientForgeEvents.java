package com.minenorth_harvest.client;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.chop.ChopManager;
import com.minenorth_harvest.network.C2SCancelChop;
import com.minenorth_harvest.network.C2SStartChop;
import com.minenorth_harvest.network.C2SSwing;
import com.minenorth_harvest.network.ModNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MineNorthHarvest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientForgeEvents {
    private ClientForgeEvents() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END) ClientChopState.clientTicks++;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) return;

        if (ClientChopState.active) {
            if (event.isAttack()) {
                event.setCanceled(true);
                event.setSwingHand(false);
                long now = ClientChopState.clientTicks;
                if (now - ClientChopState.lastSwingClientTick >= 3) {
                    ClientChopState.lastSwingClientTick = now;
                    player.swing(InteractionHand.MAIN_HAND);
                    double time = mc.level.getGameTime() + mc.getFrameTime();
                    ModNetwork.sendToServer(new C2SSwing(ClientChopState.cursor(time)));
                }
            } else if (event.isUseItem()) {
                event.setCanceled(true);
                event.setSwingHand(false);
                if (player.isShiftKeyDown() && event.getHand() == InteractionHand.MAIN_HAND) {
                    ModNetwork.sendToServer(new C2SCancelChop());
                }
            }
            return;
        }

        // Démarrage : accroupi + clic droit sur un tronc, hache en main
        if (event.isUseItem() && event.getHand() == InteractionHand.MAIN_HAND && player.isShiftKeyDown()
                && ChopManager.isAxe(player.getMainHandItem())
                && mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK
                && mc.hitResult instanceof BlockHitResult bhr
                && mc.level.getBlockState(bhr.getBlockPos()).is(BlockTags.LOGS)) {
            event.setCanceled(true);
            event.setSwingHand(true);
            ModNetwork.sendToServer(new C2SStartChop(bhr.getBlockPos()));
        }
    }

    /** Pendant le mini-jeu, on ne casse rien à la main. */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (ClientChopState.active && event.getEntity().level().isClientSide) {
            event.setNewSpeed(0f);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientChopState.reset();
    }
}
