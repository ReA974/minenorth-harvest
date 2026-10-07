package com.minenorth_harvest.zone;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.minenorth_harvest.MineNorthHarvest;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Récompenses de chasse : config/minenorth_harvest-chasse.json
 * (créé avec les valeurs du script chasse.sk s'il n'existe pas, rechargeable avec /recolte chasse reload).
 */
public final class HuntRewards {
    private HuntRewards() {}

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public record RewardItem(ResourceLocation item, int count, String name, double chance) {}
    public record Reward(String message, List<RewardItem> items) {}

    /** true = récompenses seulement si l'animal meurt dans une zone de chasse. */
    public static boolean onlyInZones = false;
    /** true = les drops vanilla sont supprimés et remplacés par les récompenses. */
    public static boolean clearVanillaDrops = true;
    /** true = pas d'orbes d'expérience. */
    public static boolean noExperience = true;
    /** false = si l'inventaire est plein, l'objet est perdu (rien ne tombe au sol). */
    public static boolean dropIfInventoryFull = false;
    public static Map<ResourceLocation, Reward> rewards = new HashMap<>();

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(MineNorthHarvest.MODID + "-chasse.json");
    }

    /** @return message d'erreur, ou null si OK. */
    public static String load() {
        // Config côté serveur uniquement : le client ne crée ni ne lit aucun fichier.
        if (net.minecraftforge.fml.loading.FMLEnvironment.dist != net.minecraftforge.api.distmarker.Dist.DEDICATED_SERVER) return null;
        Path path = file();
        try {
            if (!Files.exists(path)) {
                try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(defaults(), w);
                }
            }
            JsonObject root;
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                root = JsonParser.parseReader(r).getAsJsonObject();
            }
            boolean only = root.has("onlyInHuntingZones") && root.get("onlyInHuntingZones").getAsBoolean();
            boolean clear = !root.has("clearVanillaDrops") || root.get("clearVanillaDrops").getAsBoolean();
            boolean noXp = !root.has("noExperience") || root.get("noExperience").getAsBoolean();
            boolean dropFull = root.has("dropIfInventoryFull") && root.get("dropIfInventoryFull").getAsBoolean();
            Map<ResourceLocation, Reward> map = new HashMap<>();
            JsonObject rw = root.getAsJsonObject("rewards");
            if (rw != null) {
                for (Map.Entry<String, JsonElement> e : rw.entrySet()) {
                    ResourceLocation entity = ResourceLocation.tryParse(e.getKey());
                    if (entity == null) continue;
                    JsonObject o = e.getValue().getAsJsonObject();
                    String message = o.has("message") ? o.get("message").getAsString() : "";
                    List<RewardItem> items = new ArrayList<>();
                    if (o.has("items")) {
                        for (JsonElement ie : o.getAsJsonArray("items")) {
                            JsonObject io = ie.getAsJsonObject();
                            ResourceLocation item = ResourceLocation.tryParse(io.get("item").getAsString());
                            if (item == null) continue;
                            int count = io.has("count") ? io.get("count").getAsInt() : 1;
                            String name = io.has("name") ? io.get("name").getAsString() : "";
                            double chance = io.has("chance") ? io.get("chance").getAsDouble() : 1.0;
                            items.add(new RewardItem(item, count, name, chance));
                        }
                    }
                    map.put(entity, new Reward(message, items));
                }
            }
            onlyInZones = only;
            clearVanillaDrops = clear;
            noExperience = noXp;
            dropIfInventoryFull = dropFull;
            rewards = map;
            LOGGER.info("[{}] {} récompense(s) de chasse chargée(s)", MineNorthHarvest.MODID, map.size());
            return null;
        } catch (Exception ex) {
            LOGGER.error("[{}] Erreur dans {} : {}", MineNorthHarvest.MODID, path, ex.toString());
            return ex.getMessage() == null ? ex.toString() : ex.getMessage();
        }
    }

    private static JsonObject defaults() {
        JsonObject root = new JsonObject();
        root.addProperty("_aide", "Codes couleur avec & (ex: &6). count = quantité, chance = 0.0 à 1.0 (optionnel). "
                + "Clés = id d'entité (minecraft:cow...). onlyInHuntingZones=true : récompenses uniquement dans une zone de chasse. "
                + "clearVanillaDrops : l'animal ne lâche rien au sol. noExperience : pas d'XP. "
                + "dropIfInventoryFull=false : inventaire plein = objet perdu (rien au sol).");
        root.addProperty("onlyInHuntingZones", false);
        root.addProperty("clearVanillaDrops", true);
        root.addProperty("noExperience", true);
        root.addProperty("dropIfInventoryFull", false);
        JsonObject r = new JsonObject();
        r.add("minecraft:cow", reward("&6&l[Chasse] &eVous avez chassé une vache et récupéré sa peau !",
                item("minecraft:leather", 1, "&6Peau de Vache"), item("minecraft:beef", 1, "&6Gibier de Vache")));
        r.add("minecraft:pig", reward("&6&l[Chasse] &eVous avez chassé un sanglier et récupéré sa viande !",
                item("minecraft:porkchop", 1, "&aViande de Sanglier")));
        r.add("minecraft:sheep", reward("&6&l[Chasse] &eVous avez chassé un mouton et récupéré de la laine de qualité !",
                item("minecraft:white_wool", 1, "&fLaine de Qualité"), item("minecraft:mutton", 1, "&aViande de Mouton")));
        r.add("minecraft:chicken", reward("&6&l[Chasse] &eVous avez chassé une poule et récupéré une plume rare !",
                item("minecraft:feather", 1, "&fPlume Rare"), item("minecraft:chicken", 1, "&aViande de Poule")));
        r.add("minecraft:rabbit", reward("&6&l[Chasse] &eVous avez chassé un lapin et récupéré sa patte porte-bonheur !",
                item("minecraft:rabbit_foot", 1, "&dPatte Porte-Bonheur"), item("minecraft:rabbit", 1, "&dViande de lapin")));
        r.add("minecraft:polar_bear", reward("&6&l[Chasse] &eVous avez chassé un ours et récupéré sa fourrure et une griffe !",
                item("minecraft:leather", 1, "&cFourrure d'Ours"), item("minecraft:bone", 1, "&6Griffe d'Ours")));
        root.add("rewards", r);
        return root;
    }

    private static JsonObject reward(String message, JsonObject... items) {
        JsonObject o = new JsonObject();
        o.addProperty("message", message);
        JsonArray arr = new JsonArray();
        for (JsonObject i : items) arr.add(i);
        o.add("items", arr);
        return o;
    }

    private static JsonObject item(String id, int count, String name) {
        JsonObject o = new JsonObject();
        o.addProperty("item", id);
        o.addProperty("count", count);
        o.addProperty("name", name);
        return o;
    }
}
