package com.minenorth_harvest.data;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.config.HarvestConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

/**
 * Données joueur stockées dans le NBT persistant (conservé après la mort).
 */
public final class HarvestData {
    private HarvestData() {}

    private static CompoundTag root(Player player) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag mine = persisted.getCompound(MineNorthHarvest.MODID);
        persisted.put(MineNorthHarvest.MODID, mine);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        return mine;
    }

    public static int getEcoDebt(Player player) {
        return root(player).getInt("ecoDebt");
    }

    public static void setEcoDebt(Player player, int value) {
        root(player).putInt("ecoDebt", Math.max(0, value));
    }

    public static boolean isForestExhausted(Player player) {
        int threshold = HarvestConfig.ECO_DEBT_THRESHOLD.get();
        return threshold > 0 && getEcoDebt(player) >= threshold;
    }

    public static int getTreesFelled(Player player) { return root(player).getInt("treesFelled"); }
    public static int getTreesReplanted(Player player) { return root(player).getInt("treesReplanted"); }
    public static int getFruitsHarvested(Player player) { return root(player).getInt("fruitsHarvested"); }

    public static void onTreeFelled(Player player) {
        CompoundTag t = root(player);
        t.putInt("treesFelled", t.getInt("treesFelled") + 1);
        t.putInt("ecoDebt", t.getInt("ecoDebt") + 1);
    }

    public static void onReplant(Player player) {
        CompoundTag t = root(player);
        t.putInt("treesReplanted", t.getInt("treesReplanted") + 1);
        t.putInt("ecoDebt", Math.max(0, t.getInt("ecoDebt") - 1));
    }

    public static void addFruits(Player player, int amount) {
        CompoundTag t = root(player);
        t.putInt("fruitsHarvested", t.getInt("fruitsHarvested") + amount);
    }
}
