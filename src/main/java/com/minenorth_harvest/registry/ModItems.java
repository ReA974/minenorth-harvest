package com.minenorth_harvest.registry;

import com.minenorth_harvest.MineNorthHarvest;
import com.minenorth_harvest.block.FruitType;
import com.minenorth_harvest.item.ZoneWandItem;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.EnumMap;
import java.util.Map;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MineNorthHarvest.MODID);

    public static final RegistryObject<Item> ORANGE = ITEMS.register("orange",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(0.4f).build())));
    public static final RegistryObject<Item> LEMON = ITEMS.register("lemon",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationMod(0.3f).build())));
    public static final RegistryObject<Item> CHERRIES = ITEMS.register("cherries",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(2).saturationMod(0.2f).fast().build())));
    public static final RegistryObject<Item> PEAR = ITEMS.register("pear",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(4).saturationMod(0.4f).build())));

    public static final Map<FruitType, RegistryObject<Item>> LEAVES = new EnumMap<>(FruitType.class);
    public static final Map<FruitType, RegistryObject<Item>> SAPLINGS = new EnumMap<>(FruitType.class);

    static {
        for (FruitType type : FruitType.values()) {
            LEAVES.put(type, ITEMS.register(type.id() + "_leaves",
                    () -> new BlockItem(ModBlocks.LEAVES.get(type).get(), new Item.Properties())));
            SAPLINGS.put(type, ITEMS.register(type.id() + "_sapling",
                    () -> new BlockItem(ModBlocks.SAPLINGS.get(type).get(), new Item.Properties())));
        }
    }

    public static final RegistryObject<Item> TREE_STUMP = ITEMS.register("tree_stump",
            () -> new BlockItem(ModBlocks.TREE_STUMP.get(), new Item.Properties()));

    // ---- Boulangerie : moulin -> farine -> pâte -> baguette, tartes aux fruits ----
    public static final RegistryObject<Item> MILL = ITEMS.register("mill",
            () -> new BlockItem(ModBlocks.MILL.get(), new Item.Properties()));
    public static final RegistryObject<Item> FLOUR = ITEMS.register("flour", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> DOUGH = ITEMS.register("dough", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BAGUETTE = ITEMS.register("baguette",
            () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(6).saturationMod(0.6f).build())));

    /** Une tarte par fruit : tarte aux pommes, à l'orange, au citron, aux cerises, aux poires. */
    public static final Map<FruitType, RegistryObject<Item>> PIES = new EnumMap<>(FruitType.class);

    static {
        for (FruitType type : FruitType.values()) {
            PIES.put(type, ITEMS.register(type.id() + "_pie",
                    () -> new Item(new Item.Properties().food(new FoodProperties.Builder().nutrition(8).saturationMod(0.3f).build()))));
        }
    }

    public static final RegistryObject<Item> ZONE_WAND = ITEMS.register("zone_wand",
            () -> new ZoneWandItem(new Item.Properties().stacksTo(1)));

    private ModItems() {}
}
