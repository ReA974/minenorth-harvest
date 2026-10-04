package com.minenorth_harvest.block;

import com.minenorth_harvest.registry.ModItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.function.Supplier;

/**
 * Les différentes essences d'arbres fruitiers.
 * Pour ajouter un arbre : ajouter une entrée ici + textures/modèles/loot table.
 */
public enum FruitType {
    APPLE("apple", () -> Blocks.OAK_LOG, () -> Items.APPLE),
    ORANGE("orange", () -> Blocks.JUNGLE_LOG, () -> ModItems.ORANGE.get()),
    LEMON("lemon", () -> Blocks.BIRCH_LOG, () -> ModItems.LEMON.get()),
    CHERRY("cherry", () -> Blocks.CHERRY_LOG, () -> ModItems.CHERRIES.get()),
    PEAR("pear", () -> Blocks.SPRUCE_LOG, () -> ModItems.PEAR.get());

    private final String id;
    private final Supplier<Block> log;
    private final Supplier<Item> fruit;

    FruitType(String id, Supplier<Block> log, Supplier<Item> fruit) {
        this.id = id;
        this.log = log;
        this.fruit = fruit;
    }

    public String id() { return id; }
    public Block log() { return log.get(); }
    public Item fruit() { return fruit.get(); }
}
