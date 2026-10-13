package com.minenorth_harvest.block;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Recettes "entrée*quantité=sortie*quantité" de la config, partagées par la scierie et le four à minerai. */
public final class MachineRecipes {
    private MachineRecipes() {}

    public record Recipe(Item input, int inCount, Item output, int outCount) {}

    @Nullable
    public static Recipe find(ItemStack held, List<? extends String> lines) {
        if (held.isEmpty()) return null;
        for (String line : lines) {
            Recipe r = parse(line);
            if (r != null && held.is(r.input())) return r;
        }
        return null;
    }

    @Nullable
    public static Recipe parse(String line) {
        String[] sides = line.split("=");
        if (sides.length != 2) return null;
        Object[] in = side(sides[0]), out = side(sides[1]);
        if (in == null || out == null) return null;
        return new Recipe((Item) in[0], (int) in[1], (Item) out[0], (int) out[1]);
    }

    @Nullable
    private static Object[] side(String s) {
        String[] parts = s.trim().split("\\*");
        ResourceLocation id = ResourceLocation.tryParse(parts[0].trim());
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return null;
        int count = 1;
        if (parts.length > 1) {
            try { count = Math.max(1, Math.min(64, Integer.parseInt(parts[1].trim()))); } catch (NumberFormatException e) { return null; }
        }
        return new Object[]{ForgeRegistries.ITEMS.getValue(id), count};
    }
}
