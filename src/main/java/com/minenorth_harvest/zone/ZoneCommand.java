package com.minenorth_harvest.zone;

import com.minenorth_harvest.data.HarvestData;
import com.minenorth_harvest.registry.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.Collection;

/**
 * Toutes les commandes sont op 2.
 *
 * /recolte baguette                                   -> donne la baguette de zone
 * /recolte zone creer <nom> <bucheron|verger>         -> crée la zone à partir de la sélection de la baguette
 * /recolte zone creer <nom> <type> <de> <a>           -> (optionnel) avec des coordonnées
 * /recolte zone redefinir <nom>                       -> remplace les limites par la sélection actuelle
 * /recolte zone supprimer|info|afficher <nom>
 * /recolte zone liste
 * /recolte zone repousse <nom> <true|false>           (zones bûcheron)
 * /recolte zone vitesse <nom> <multiplicateur>        (zones verger, 0 = valeur de la config)
 * /recolte ecoreset <joueurs>                         -> remet la dette écologique à 0
 * Les zones sont propres à chaque dimension (celle où la commande est lancée).
 */
public final class ZoneCommand {
    private ZoneCommand() {}

    private static final SuggestionProvider<CommandSourceStack> ZONE_NAMES = (c, b) ->
            SharedSuggestionProvider.suggest(ZoneData.get(c.getSource().getLevel()).zones().stream().map(z -> z.name), b);

    private static RequiredArgumentBuilder<CommandSourceStack, String> zoneArg() {
        return Commands.argument("nom", StringArgumentType.word()).suggests(ZONE_NAMES);
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("recolte")
                .requires(s -> s.hasPermission(2))
                .then(Commands.literal("baguette").executes(ZoneCommand::giveWand))
                .then(Commands.literal("ecoreset")
                        .then(Commands.argument("joueurs", EntityArgument.players()).executes(ZoneCommand::ecoReset)))
                .then(Commands.literal("zone")
                        .then(Commands.literal("creer")
                                .then(Commands.argument("nom", StringArgumentType.word())
                                        .then(Commands.argument("type", StringArgumentType.word())
                                                .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                                        Arrays.stream(ZoneType.values()).map(ZoneType::id), b))
                                                .executes(ZoneCommand::createFromSelection)
                                                .then(Commands.argument("de", BlockPosArgument.blockPos())
                                                        .then(Commands.argument("a", BlockPosArgument.blockPos())
                                                                .executes(ZoneCommand::createFromCoords))))))
                        .then(Commands.literal("redefinir").then(zoneArg().executes(ZoneCommand::redefine)))
                        .then(Commands.literal("supprimer").then(zoneArg().executes(ZoneCommand::delete)))
                        .then(Commands.literal("liste").executes(ZoneCommand::list))
                        .then(Commands.literal("info").then(zoneArg().executes(ZoneCommand::info)))
                        .then(Commands.literal("afficher").then(zoneArg().executes(ZoneCommand::show)))
                        .then(Commands.literal("repousse").then(zoneArg()
                                .then(Commands.argument("valeur", BoolArgumentType.bool()).executes(ZoneCommand::setRegrow))))
                        .then(Commands.literal("vitesse").then(zoneArg()
                                .then(Commands.argument("multiplicateur", DoubleArgumentType.doubleArg(0, 100)).executes(ZoneCommand::setSpeed))))));
    }

    // ------------------------------------------------------------ baguette / sélection

    private static int giveWand(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        ItemStack wand = new ItemStack(ModItems.ZONE_WAND.get());
        if (!p.getInventory().add(wand)) p.drop(wand, false);
        c.getSource().sendSuccess(() -> Component.translatable("command.minenorth_harvest.wand_given"), false);
        return 1;
    }

    private static ZoneSelection.Sel selection(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ZoneSelection.Sel sel = ZoneSelection.get(c.getSource().getPlayerOrException());
        if (sel == null || !sel.complete()) {
            c.getSource().sendFailure(Component.translatable("command.minenorth_harvest.zone_no_selection"));
            return null;
        }
        return sel;
    }

    // ------------------------------------------------------------ création

    private static int createFromSelection(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ZoneSelection.Sel sel = selection(c);
        if (sel == null) return 0;
        return create(c, sel.pos1, sel.pos2);
    }

    private static int createFromCoords(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return create(c, BlockPosArgument.getLoadedBlockPos(c, "de"), BlockPosArgument.getLoadedBlockPos(c, "a"));
    }

    private static int create(CommandContext<CommandSourceStack> c, BlockPos a, BlockPos b) {
        String name = StringArgumentType.getString(c, "nom");
        String typeId = StringArgumentType.getString(c, "type");
        ZoneType type = ZoneType.byId(typeId);
        if (type == null) {
            c.getSource().sendFailure(Component.translatable("command.minenorth_harvest.zone_bad_type", typeId));
            return 0;
        }
        ServerLevel level = c.getSource().getLevel();
        ZoneData data = ZoneData.get(level);
        if (data.byName(name) != null) {
            c.getSource().sendFailure(Component.translatable("command.minenorth_harvest.zone_exists", name));
            return 0;
        }
        Zone zone = new Zone(name, type, a, b);
        data.add(zone);
        c.getSource().sendSuccess(() -> Component.translatable("command.minenorth_harvest.zone_created",
                name, Component.translatable("zone.minenorth_harvest." + type.id()), zone.volume()), true);
        if (c.getSource().getPlayer() != null) ZoneManager.show(c.getSource().getPlayer(), zone, 10);
        return 1;
    }

    private static int redefine(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Zone old = find(c);
        if (old == null) return 0;
        ZoneSelection.Sel sel = selection(c);
        if (sel == null) return 0;
        Zone zone = new Zone(old.name, old.type, sel.pos1, sel.pos2);
        zone.regrow = old.regrow;
        zone.speed = old.speed;
        ZoneData data = ZoneData.get(c.getSource().getLevel());
        data.remove(old.name);
        data.add(zone);
        c.getSource().sendSuccess(() -> Component.translatable("command.minenorth_harvest.zone_redefined", zone.name, zone.volume()), true);
        ZoneManager.show(c.getSource().getPlayerOrException(), zone, 10);
        return 1;
    }

    // ------------------------------------------------------------ gestion

    private static Zone find(CommandContext<CommandSourceStack> c) {
        String name = StringArgumentType.getString(c, "nom");
        Zone z = ZoneData.get(c.getSource().getLevel()).byName(name);
        if (z == null) c.getSource().sendFailure(Component.translatable("command.minenorth_harvest.zone_unknown", name));
        return z;
    }

    private static int delete(CommandContext<CommandSourceStack> c) {
        Zone z = find(c);
        if (z == null) return 0;
        ZoneData.get(c.getSource().getLevel()).remove(z.name);
        c.getSource().sendSuccess(() -> Component.translatable("command.minenorth_harvest.zone_deleted", z.name), true);
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        ZoneData data = ZoneData.get(c.getSource().getLevel());
        if (data.zones().isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("command.minenorth_harvest.zone_none"), false);
            return 0;
        }
        for (Zone z : data.zones()) sendInfo(c, z);
        return data.zones().size();
    }

    private static int info(CommandContext<CommandSourceStack> c) {
        Zone z = find(c);
        if (z == null) return 0;
        sendInfo(c, z);
        return 1;
    }

    private static void sendInfo(CommandContext<CommandSourceStack> c, Zone z) {
        c.getSource().sendSuccess(() -> Component.translatable("command.minenorth_harvest.zone_info",
                z.name, Component.translatable("zone.minenorth_harvest." + z.type.id()),
                z.min.toShortString(), z.max.toShortString(),
                z.type == ZoneType.BUCHERON ? (z.regrow ? "oui" : "non") : "-",
                z.type == ZoneType.VERGER ? (z.speed > 0 ? "x" + z.speed : "config") : "-"), false);
    }

    private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Zone z = find(c);
        if (z == null) return 0;
        ZoneManager.show(c.getSource().getPlayerOrException(), z, 15);
        return 1;
    }

    private static int setRegrow(CommandContext<CommandSourceStack> c) {
        Zone z = find(c);
        if (z == null) return 0;
        z.regrow = BoolArgumentType.getBool(c, "valeur");
        ZoneData.get(c.getSource().getLevel()).setDirty();
        sendInfo(c, z);
        return 1;
    }

    private static int setSpeed(CommandContext<CommandSourceStack> c) {
        Zone z = find(c);
        if (z == null) return 0;
        z.speed = DoubleArgumentType.getDouble(c, "multiplicateur");
        ZoneData.get(c.getSource().getLevel()).setDirty();
        sendInfo(c, z);
        return 1;
    }

    private static int ecoReset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(c, "joueurs");
        for (ServerPlayer p : players) HarvestData.setEcoDebt(p, 0);
        c.getSource().sendSuccess(() -> Component.translatable("command.minenorth_harvest.eco_reset", players.size()), true);
        return players.size();
    }
}
