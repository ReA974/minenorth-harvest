package com.minenorth_harvest.event;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.chop.ChopManager;
import com.minenorth_harvest.chop.TreeScanner;
import com.minenorth_harvest.config.SyncedConfig;
import com.minenorth_harvest.network.ModNetwork;
import com.minenorth_harvest.network.S2CSyncConfig;
import com.minenorth_harvest.zone.ZoneCommand;
import com.minenorth_harvest.zone.HuntManager;
import com.minenorth_harvest.zone.HuntRewards;
import com.minenorth_harvest.zone.ZoneManager;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.event.server.ServerStartingEvent;
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

    /** Le pétrole brut brûle longtemps dans un four (un peu plus que le charbon). */
    @SubscribeEvent
    public static void onFurnaceFuel(net.minecraftforge.event.furnace.FurnaceFuelBurnTimeEvent event) {
        if (event.getItemStack().is(ModItems.CRUDE_OIL.get())) event.setBurnTime(2400);
        else if (event.getItemStack().is(ModItems.GASOLINE.get())) event.setBurnTime(6400);
    }

    /** Gisement miné dans une zone pétrole avec régénération : il reviendra après le délai configuré. */
    @SubscribeEvent
    public static void onOilBroken(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (!event.getState().is(com.minenorth_harvest.registry.ModBlocks.OIL_DEPOSIT.get())) return;
        com.minenorth_harvest.zone.ZoneData data = com.minenorth_harvest.zone.ZoneData.get(level);
        com.minenorth_harvest.zone.Zone zone = data.zoneAt(event.getPos(), com.minenorth_harvest.zone.ZoneType.PETROLE);
        if (zone == null || !zone.regrow) return;
        data.addRegrow(event.getPos(), ZoneManager.OIL_REGEN,
                level.getGameTime() + com.minenorth_harvest.config.HarvestConfig.OIL_REGEN_DELAY.get() * 20L);
    }

    /** Minerai miné dans une zone mine avec régénération : il reviendra après le délai configuré. */
    @SubscribeEvent
    public static void onOreBroken(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        if (!event.getState().is(net.minecraftforge.common.Tags.Blocks.ORES)) return;
        com.minenorth_harvest.zone.ZoneData data = com.minenorth_harvest.zone.ZoneData.get(level);
        com.minenorth_harvest.zone.Zone zone = data.zoneAt(event.getPos(), com.minenorth_harvest.zone.ZoneType.MINE);
        if (zone == null || !zone.regrow) return;
        net.minecraft.resources.ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(event.getState().getBlock());
        if (id == null) return;
        data.addRegrow(event.getPos(), ZoneManager.ORE_REGEN + id,
                level.getGameTime() + com.minenorth_harvest.config.HarvestConfig.MINE_REGEN_DELAY.get() * 20L);
    }

    /**
     * Minerai d'une zone mine : le joueur reçoit TOUJOURS ses drops (calculés avec l'outil en main : palier, fortune), déposés directement dans son
     * inventaire (au sol s'il est plein), sans dépendre de la casse vanilla ni des objets au sol. Passe en dernier : les protections (zones, claims) qui
     * annulent la casse ont déjà parlé. Création / spectateur : comportement vanilla (aucun drop).
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onOreDrops(net.minecraftforge.event.level.BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof net.minecraft.server.level.ServerLevel sl) || !(event.getPlayer() instanceof ServerPlayer p)) return;
        if (p.isCreative() || p.isSpectator()) return;
        net.minecraft.world.level.block.state.BlockState state = event.getState();
        if (!state.is(net.minecraftforge.common.Tags.Blocks.ORES)) return;
        net.minecraft.core.BlockPos pos = event.getPos();
        if (com.minenorth_harvest.zone.ZoneData.get(sl).zoneAt(pos, com.minenorth_harvest.zone.ZoneType.MINE) == null) return;
        net.minecraft.world.item.ItemStack tool = p.getMainHandItem();
        boolean harvest = !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
        java.util.List<net.minecraft.world.item.ItemStack> drops = harvest
                ? net.minecraft.world.level.block.Block.getDrops(state, sl, pos, sl.getBlockEntity(pos), p, tool) : java.util.List.of();
        event.setCanceled(true);   // on prend la casse en charge (le bloc est retiré ici, le client est resynchronisé par Forge)
        tool.mineBlock(sl, state, pos, p);
        if (!sl.removeBlock(pos, false)) return;
        state.getBlock().destroy(sl, pos, state);
        for (net.minecraft.world.item.ItemStack d : drops) {
            net.minecraft.world.item.ItemStack c = d.copy();
            if (!p.getInventory().add(c) && !c.isEmpty()) net.minecraft.world.level.block.Block.popResource(sl, pos, c);
        }
        state.spawnAfterBreak(sl, pos, tool, true);
        sl.levelEvent(2001, pos, net.minecraft.world.level.block.Block.getId(state));
    }

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

    /** Récompenses de chasse (port de chasse.sk). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(LivingDropsEvent event) {
        HuntManager.onDrops(event);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingExperience(LivingExperienceDropEvent event) {
        HuntManager.onExperience(event);
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        HuntRewards.load();
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
