package com.minenorth_harvest.event;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.chop.ChopManager;
import com.minenorth_harvest.chop.TreeScanner;
import com.minenorth_harvest.config.SyncedConfig;
import com.minenorth_harvest.network.ModNetwork;
import com.minenorth_harvest.network.S2CSyncConfig;
import com.minenorth_harvest.zone.ZoneCommand;
import com.minenorth_harvest.zone.ZoneManager;
import com.minenorth_harvest.zone.ZoneSelection;
import com.minenorth_harvest.registry.ModItems;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MineNorthHarvest.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CommonEvents {
    private CommonEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ZoneCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            ChopManager.tick();
            ZoneManager.tick();
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) {
            ChopManager.onLogout(sp);
            ZoneManager.onLogout(sp);
            ZoneSelection.clear(sp.getUUID());
        }
    }

    /** Baguette de zone : clic gauche = point 1 (annulé des deux côtés pour ne rien casser). */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!event.getItemStack().is(ModItems.ZONE_WAND.get())) return;
        event.setCanceled(true);
        if (event.getEntity() instanceof ServerPlayer sp && sp.hasPermissions(2)) {
            ZoneSelection.set(sp, event.getPos(), true);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) ModNetwork.sendTo(sp, S2CSyncConfig.fromConfig());
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) ChopManager.cancel(sp, false);
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ChopManager.clearAll();
        ZoneManager.clearAll();
    }

    /**
     * - Pendant le mini-jeu : impossible de casser à la main (serveur).
     * - Hors mini-jeu : casser une bûche d'arbre naturel à la main est plus lent (client + serveur, même calcul).
     */
    @SubscribeEvent
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (event.getEntity().getMainHandItem().is(ModItems.ZONE_WAND.get())) {
            event.setNewSpeed(0f);
            return;
        }
        if (event.getEntity() instanceof ServerPlayer sp && ChopManager.inSession(sp)) {
            event.setNewSpeed(0f);
            return;
        }
        var level = event.getEntity().level();
        double mult = SyncedConfig.manualLogSpeed(level);
        if (mult >= 1.0 || !SyncedConfig.chopEnabled(level)) return;
        if (!event.getState().is(BlockTags.LOGS)) return;
        event.getPosition().ifPresent(pos -> {
            if (TreeScanner.looksNatural(level, pos)) {
                event.setNewSpeed((float) (event.getNewSpeed() * mult));
            }
        });
    }
}
