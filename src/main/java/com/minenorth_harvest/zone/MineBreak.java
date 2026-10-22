package com.minenorth_harvest.zone;

import com.minenorth_harvest.config.HarvestConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PickaxeItem;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.Tags;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Casse d'un minerai de zone mine, faite par le mod lui-même, insensible aux protections et aux filtres d'objets du serveur (WorldGuard, plugins Bukkit
 * d'un serveur hybride) : le bloc est retiré côté serveur, puis le minerai est DONNÉ directement dans l'inventaire du joueur (au pied s'il est plein), sans
 * passer par les loot tables des blocs ni par des objets au sol. Chaque étape est journalisée dans latest.log (préfixe « [Harvest/mine] »).
 * Tous les joueurs sont traités pareil (opérateurs et créatif compris) : le minerai tombe à tous les coups avec une pioche ou le marteau-piqueur.
 */
public final class MineBreak {
    private MineBreak() {}

    private static final Logger LOG = LogUtils.getLogger();
    private static final String P = "[Harvest/mine] ";

    /** Secours si la loot table du minerai ne donne rien (bloc modifié par un autre mod…) : chemin du bloc -> objet et quantité. */
    private static final Map<String, String> FALLBACK = Map.of(
            "coal_ore", "minecraft:coal*1", "iron_ore", "minecraft:raw_iron*1", "copper_ore", "minecraft:raw_copper*2", "gold_ore", "minecraft:raw_gold*1",
            "redstone_ore", "minecraft:redstone*4", "lapis_ore", "minecraft:lapis_lazuli*6", "diamond_ore", "minecraft:diamond*1", "emerald_ore", "minecraft:emerald*1");

    /** Ce bloc est-il un minerai d'une zone mine ? (sinon la casse reste vanilla) */
    public static boolean isMineOre(ServerLevel level, BlockPos pos, BlockState state) {
        return state.is(Tags.Blocks.ORES) && ZoneData.get(level).zoneAt(pos, ZoneType.MINE) != null;
    }

    /**
     * Prend en charge la casse d'un minerai de zone mine. Renvoie vrai si le mod s'en est occupé (bloc cassé OU casse refusée avec message au joueur) :
     * l'appelant doit alors annuler la casse vanilla. Faux = pas concerné (hors zone / pas un minerai) ou erreur interne : casse vanilla.
     */
    public static boolean handle(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, ItemStack tool, String source) {
        try {
            if (!state.is(Tags.Blocks.ORES)) return false;
            Zone zone = ZoneData.get(level).zoneAt(pos, ZoneType.MINE);
            String who = player.getGameProfile().getName();
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
            if (zone == null) {
                LOG.debug(P + "{} : {} en {} hors zone mine : casse vanilla ({})", who, id, pos.toShortString(), source);
                return false;
            }
            boolean pick = tool.getItem() instanceof PickaxeItem || tool.canPerformAction(ToolActions.PICKAXE_DIG)
                    || ForgeRegistries.ITEMS.getKey(tool.getItem()) != null && ForgeRegistries.ITEMS.getKey(tool.getItem()).getPath().equals("jackhammer");
            LOG.info(P + "{} casse {} en {} (zone « {} », outil {}, mode {}, via {})", who, id, pos.toShortString(), zone.name,
                    ForgeRegistries.ITEMS.getKey(tool.getItem()), player.isCreative() ? "créatif" : "survie", source);
            if (!pick) {
                LOG.info(P + "{} : refusé, il faut une pioche ou le marteau-piqueur (outil en main : {})", who, ForgeRegistries.ITEMS.getKey(tool.getItem()));
                player.displayClientMessage(Component.literal("§cIl faut une pioche (ou un marteau-piqueur) pour miner ici."), true);
                return true;
            }
            List<ItemStack> drops = drops(level, player, pos, state, tool);
            int mult = HarvestConfig.MINE_DROP_MULTIPLIER.get();
            if (mult > 1) for (ItemStack d : drops) d.setCount(Math.min(d.getMaxStackSize() * 4, d.getCount() * mult));
            LOG.info(P + "{} : minerai à donner : {}", who, drops);
            if (drops.isEmpty()) {
                LOG.warn(P + "{} : AUCUN objet calculé pour {} (loot table vide et pas de secours) : le bloc est cassé sans rien donner", who, id);
            }
            // usure de l'outil (et essence du marteau-piqueur) comme une vraie casse
            if (HarvestConfig.MINE_TOOL_WEAR.get() && !player.isCreative()) tool.mineBlock(level, state, pos, player);
            int exp = state.getExpDrop(level, level.random, pos, EnchantmentHelper.getTagEnchantmentLevel(net.minecraft.world.item.enchantment.Enchantments.BLOCK_FORTUNE, tool), 0);
            // le bloc disparaît (la zone le fera repousser) ; 3 = mise à jour des voisins et du client
            if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) {
                LOG.warn(P + "{} : setBlock(air) refusé en {} (un autre mod/plugin a bloqué le remplacement du bloc)", who, pos.toShortString());
            }
            level.levelEvent(2001, pos, Block.getId(state));
            if (zone.regrow && ZoneData.get(level).zoneAt(pos, ZoneType.MINE) != null && id != null) {
                ZoneData.get(level).addRegrow(pos, ZoneManager.ORE_REGEN + id, level.getGameTime() + HarvestConfig.MINE_REGEN_DELAY.get() * 20L);
            }
            int given = 0;
            StringBuilder names = new StringBuilder();
            for (ItemStack d : drops) {
                ItemStack c = d.copy();
                int n = c.getCount();
                String name = c.getHoverName().getString();
                boolean full = !player.getInventory().add(c);
                if (full && !c.isEmpty()) {
                    LOG.info(P + "{} : inventaire plein, {} x{} déposé(s) au pied", who, name, c.getCount());
                    player.drop(c, false);
                }
                given += n;
                if (names.length() > 0) names.append(", ");
                names.append(n).append(" ").append(name);
            }
            if (exp > 0) player.giveExperiencePoints(exp);
            if (given > 0) {
                player.displayClientMessage(Component.literal("§6+ " + names), true);
                LOG.info(P + "{} : {} donné(s) : {}", who, given, names);
            }
            return true;
        } catch (Exception e) {
            LOG.error(P + "erreur pendant la casse d'un minerai de zone mine : casse vanilla en repli", e);
            return false;
        }
    }

    /** Minerai du bloc : loot table avec un outil assez bon (palier ignoré, fortune de l'outil réel conservée), surcharges de la config, sinon table de secours. */
    private static List<ItemStack> drops(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state, ItemStack realTool) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        for (String line : HarvestConfig.MINE_DROP_OVERRIDES.get()) {
            ItemStack o = parse(line, id);
            if (o != null) return new ArrayList<>(List.of(o));
        }
        ItemStack tier = new ItemStack(Items.DIAMOND_PICKAXE);
        EnchantmentHelper.setEnchantments(EnchantmentHelper.getEnchantments(realTool), tier);
        List<ItemStack> out = new ArrayList<>();
        try {
            for (ItemStack s : Block.getDrops(state, level, pos, level.getBlockEntity(pos), player, tier)) if (!s.isEmpty()) out.add(s.copy());
        } catch (Exception e) {
            LOG.warn(P + "loot table illisible pour {} : {}", id, e.toString());
        }
        if (out.isEmpty() && id != null) {
            String key = id.getPath().startsWith("deepslate_") ? id.getPath().substring("deepslate_".length()) : id.getPath();
            String fb = FALLBACK.get(key);
            ItemStack f = fb == null ? null : parse(id + "=" + fb, id);
            if (f != null) out.add(f);
        }
        return out;
    }

    /** « bloc=objet*quantité » : renvoie l'objet si la ligne vise ce bloc. */
    private static ItemStack parse(String line, ResourceLocation block) {
        if (line == null || block == null) return null;
        String[] p = line.split("=", 2);
        if (p.length != 2 || !p[0].trim().equals(block.toString())) return null;
        String item = p[1].trim();
        int count = 1;
        int star = item.indexOf('*');
        if (star > 0) {
            try { count = Math.max(1, Integer.parseInt(item.substring(star + 1).trim())); } catch (NumberFormatException ignored) { }
            item = item.substring(0, star).trim();
        }
        ResourceLocation rl = ResourceLocation.tryParse(item);
        if (rl == null || !ForgeRegistries.ITEMS.containsKey(rl)) return null;
        return new ItemStack(ForgeRegistries.ITEMS.getValue(rl), count);
    }
}
