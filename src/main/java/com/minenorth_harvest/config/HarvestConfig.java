package com.minenorth_harvest.config;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Configuration : config/minenorth_harvest-common.toml (générée au démarrage, rechargée à chaud).
 * Les valeurs utiles au client (vitesse de casse...) lui sont envoyées par le serveur (voir SyncedConfig).
 */
public final class HarvestConfig {
    public static final ForgeConfigSpec SPEC;

    // ---- Fruits ----
    public static final ForgeConfigSpec.DoubleValue FRUIT_GROWTH_CHANCE;
    public static final ForgeConfigSpec.DoubleValue FRUIT_FALL_CHANCE;
    public static final ForgeConfigSpec.IntValue HARVEST_MIN;
    public static final ForgeConfigSpec.IntValue HARVEST_MAX;
    public static final ForgeConfigSpec.BooleanValue BONEMEAL_FRUITS;

    // ---- Bûcheron ----
    public static final ForgeConfigSpec.BooleanValue CHOP_ENABLED;
    public static final ForgeConfigSpec.IntValue MAX_TREE_LOGS;
    public static final ForgeConfigSpec.IntValue MIN_LEAVES;
    public static final ForgeConfigSpec.IntValue BASE_HITS;
    public static final ForgeConfigSpec.IntValue LOGS_PER_EXTRA_HIT;
    public static final ForgeConfigSpec.IntValue MAX_HITS;
    public static final ForgeConfigSpec.DoubleValue ZONE_WIDTH;
    public static final ForgeConfigSpec.DoubleValue PERFECT_WIDTH;
    public static final ForgeConfigSpec.IntValue CURSOR_PERIOD;
    public static final ForgeConfigSpec.IntValue MIN_CURSOR_PERIOD;
    public static final ForgeConfigSpec.IntValue SWING_COOLDOWN;
    public static final ForgeConfigSpec.IntValue SESSION_TIMEOUT;
    public static final ForgeConfigSpec.DoubleValue BONUS_LOG_PER_PERFECT;
    public static final ForgeConfigSpec.IntValue MISS_EXTRA_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue MANUAL_LOG_SPEED;
    public static final ForgeConfigSpec.BooleanValue FAST_LEAF_DECAY;
    public static final ForgeConfigSpec.BooleanValue LEAVE_STUMP;
    public static final ForgeConfigSpec.IntValue LOGS_PER_TICK;

    // ---- Zones ----
    public static final ForgeConfigSpec.BooleanValue REQUIRE_ZONE;
    public static final ForgeConfigSpec.BooleanValue ZONES_BYPASS_PROTECTION;
    public static final ForgeConfigSpec.IntValue REGROW_DELAY;
    public static final ForgeConfigSpec.IntValue OIL_REGEN_DELAY;
    public static final ForgeConfigSpec.DoubleValue ORCHARD_SPEED;
    public static final ForgeConfigSpec.IntValue ORCHARD_MAX_CHECKS;
    public static final ForgeConfigSpec.BooleanValue ZONE_ENTER_MESSAGE;

    // ---- Chasse ----
    public static final ForgeConfigSpec.BooleanValue HUNT_ENABLED;
    public static final ForgeConfigSpec.IntValue HUNT_INTERVAL;
    public static final ForgeConfigSpec.IntValue HUNT_ATTEMPTS;
    public static final ForgeConfigSpec.IntValue HUNT_SPAWN_RADIUS;
    public static final ForgeConfigSpec.IntValue HUNT_MIN_DISTANCE;
    public static final ForgeConfigSpec.IntValue HUNT_COUNT_RADIUS;
    public static final ForgeConfigSpec.IntValue HUNT_MAX_ANIMALS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> HUNT_ANIMALS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> HUNT_GROUND;
    public static final ForgeConfigSpec.BooleanValue HUNT_TITLE;

    // ---- Moulin ----
    public static final ForgeConfigSpec.IntValue MILL_TURNS;
    public static final ForgeConfigSpec.IntValue MILL_COOLDOWN;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> MILL_RECIPES;
    public static final ForgeConfigSpec.IntValue REFINERY_TURNS;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> REFINERY_RECIPES;

    // ---- Écologie ----
    public static final ForgeConfigSpec.IntValue ECO_DEBT_THRESHOLD;
    public static final ForgeConfigSpec.IntValue ECO_EXTRA_HITS;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("Arbres fruitiers").push("fruits");
        FRUIT_GROWTH_CHANCE = b.comment("Chance qu'un tick aléatoire fasse avancer le fruit d'un stade (fleur -> vert -> mûr)")
                .defineInRange("growthChance", 0.25, 0.0, 1.0);
        FRUIT_FALL_CHANCE = b.comment("Chance qu'un fruit mûr non cueilli tombe tout seul au sol (s'il y a de l'air dessous)")
                .defineInRange("fallChance", 0.04, 0.0, 1.0);
        HARVEST_MIN = b.comment("Nombre minimum de fruits par cueillette").defineInRange("harvestMin", 1, 0, 64);
        HARVEST_MAX = b.comment("Nombre maximum de fruits par cueillette").defineInRange("harvestMax", 2, 0, 64);
        BONEMEAL_FRUITS = b.comment("La poudre d'os fait mûrir les fruits").define("bonemealFruits", true);
        b.pop();

        b.comment("Mini-jeu d'abattage").push("lumberjack");
        CHOP_ENABLED = b.comment("Active le mini-jeu (accroupi + clic droit sur un tronc avec une hache)").define("enabled", true);
        MAX_TREE_LOGS = b.comment("Nombre max de bûches pour qu'un arbre soit abattable").defineInRange("maxTreeLogs", 256, 1, 4096);
        MIN_LEAVES = b.comment("Nombre min de feuilles naturelles autour des bûches (évite d'abattre les constructions)").defineInRange("minLeaves", 4, 0, 256);
        BASE_HITS = b.comment("Coups réussis de base nécessaires").defineInRange("baseHits", 2, 1, 50);
        LOGS_PER_EXTRA_HIT = b.comment("Un coup de plus nécessaire toutes les X bûches").defineInRange("logsPerExtraHit", 8, 1, 100);
        MAX_HITS = b.comment("Plafond de coups nécessaires").defineInRange("maxHits", 7, 1, 100);
        ZONE_WIDTH = b.comment("Largeur de la zone verte (fraction de la barre)").defineInRange("zoneWidth", 0.24, 0.02, 1.0);
        PERFECT_WIDTH = b.comment("Largeur de la zone dorée 'parfait' (fraction de la barre)").defineInRange("perfectWidth", 0.07, 0.0, 1.0);
        CURSOR_PERIOD = b.comment("Durée d'un aller-retour du curseur, en ticks (plus petit = plus dur)").defineInRange("cursorPeriodTicks", 22, 6, 200);
        MIN_CURSOR_PERIOD = b.comment("Le curseur accélère à chaque coup réussi jusqu'à cette période minimale").defineInRange("minCursorPeriodTicks", 14, 6, 200);
        SWING_COOLDOWN = b.comment("Délai minimal entre deux coups, en ticks").defineInRange("swingCooldownTicks", 2, 0, 40);
        SESSION_TIMEOUT = b.comment("Abandon automatique après X secondes sans coup").defineInRange("sessionTimeoutSeconds", 20, 5, 600);
        BONUS_LOG_PER_PERFECT = b.comment("Chance de bûche bonus par coup 'parfait'").defineInRange("bonusLogPerPerfectChance", 0.35, 0.0, 1.0);
        MISS_EXTRA_DAMAGE = b.comment("Usure supplémentaire de la hache sur un coup raté").defineInRange("missExtraDamage", 2, 0, 50);
        MANUAL_LOG_SPEED = b.comment("Multiplicateur de vitesse pour casser à la main une bûche d'arbre naturel (1.0 = vanilla). Incite à utiliser le mini-jeu.")
                .defineInRange("manualLogBreakSpeed", 0.4, 0.01, 1.0);
        FAST_LEAF_DECAY = b.comment("Les feuilles orphelines tombent vite après l'abattage").define("fastLeafDecay", true);
        LEAVE_STUMP = b.comment("Laisser une souche à replanter").define("leaveStump", true);
        LOGS_PER_TICK = b.comment("Bûches retirées par tick pendant la chute (effet visuel)").defineInRange("logsPerTick", 8, 1, 64);
        b.pop();

        b.comment("Zones de bûcheronnage (/bucheron zone ...)").push("zones");
        REQUIRE_ZONE = b.comment("true = le mini-jeu ne marche QUE dans une zone de bûcheronnage créée par un admin")
                .define("requireZone", true);
        ZONES_BYPASS_PROTECTION = b.comment("true = dans une zone, on ignore les protections (claims) pour l'abattage : la zone fait foi")
                .define("zonesBypassProtection", true);
        REGROW_DELAY = b.comment("Zones avec repousse : délai (secondes) avant qu'une pousse soit replantée automatiquement sur une souche oubliée")
                .defineInRange("regrowDelaySeconds", 600, 5, 86400);
        OIL_REGEN_DELAY = b.comment("Zones pétrole avec régénération : délai (secondes) avant qu'un gisement miné réapparaisse")
                .defineInRange("oilRegenDelaySeconds", 900, 5, 86400);
        ORCHARD_SPEED = b.comment("Zones verger : multiplicateur de vitesse de pousse des fruits quand un joueur est dans la zone (modifiable par zone)")
                .defineInRange("orchardSpeedMultiplier", 5.0, 1.0, 100.0);
        ORCHARD_MAX_CHECKS = b.comment("Zones verger : nombre max de blocs testés par seconde et par zone (limite de perf)")
                .defineInRange("orchardMaxChecksPerSecond", 6000, 100, 100000);
        ZONE_ENTER_MESSAGE = b.comment("Afficher un message quand un joueur entre / sort d'une zone").define("enterMessage", true);
        b.pop();

        b.comment("Chasse : zones /recolte zone creer <nom> chasse. Les récompenses sont dans config/minenorth_harvest-chasse.json").push("hunting");
        HUNT_ENABLED = b.comment("Active l'apparition d'animaux dans les zones de chasse").define("enabled", true);
        HUNT_INTERVAL = b.comment("Intervalle entre deux vagues d'apparition (secondes)").defineInRange("spawnIntervalSeconds", 10, 1, 3600);
        HUNT_ATTEMPTS = b.comment("Tentatives d'apparition par joueur et par vague").defineInRange("attemptsPerPlayer", 1, 1, 20);
        HUNT_SPAWN_RADIUS = b.comment("Distance max (X/Z) autour du joueur où un animal peut apparaître").defineInRange("spawnRadius", 20, 4, 64);
        HUNT_MIN_DISTANCE = b.comment("Distance min autour du joueur (évite les apparitions sous son nez)").defineInRange("minSpawnDistance", 6, 0, 60);
        HUNT_COUNT_RADIUS = b.comment("Rayon de comptage des animaux déjà présents autour du joueur").defineInRange("countRadius", 20, 4, 64);
        HUNT_MAX_ANIMALS = b.comment("Plus d'apparition si au moins autant d'animaux (de la liste) autour du joueur").defineInRange("maxAnimalsAroundPlayer", 6, 0, 100);
        HUNT_ANIMALS = b.comment("Animaux qui peuvent apparaître, format \"id=poids\" (poids = probabilité relative)")
                .defineList("animals", java.util.List.of("minecraft:cow=1", "minecraft:pig=1", "minecraft:sheep=1",
                        "minecraft:chicken=1", "minecraft:rabbit=1", "minecraft:polar_bear=1"), o -> o instanceof String);
        HUNT_GROUND = b.comment("Blocs au sol sur lesquels un animal peut apparaître")
                .defineList("groundBlocks", java.util.List.of("minecraft:grass_block"), o -> o instanceof String);
        HUNT_TITLE = b.comment("Afficher un titre \"Zone de Chasse\" à l'entrée").define("titleOnEnter", true);
        b.pop();

        b.comment("Moulin : objet en main, maintenir clic droit sur le moulin pour moudre").push("mill");
        MILL_TURNS = b.comment("Tours de meule pour obtenir un produit (un clic droit maintenu = environ 5 tours par seconde)")
                .defineInRange("turnsPerProduct", 8, 1, 200);
        MILL_COOLDOWN = b.comment("Délai minimal entre deux tours, en ticks (anti auto-clic)").defineInRange("turnCooldownTicks", 3, 0, 40);
        MILL_RECIPES = b.comment("Recettes du moulin, format \"entrée*quantité=sortie*quantité\"")
                .defineList("recipes", java.util.List.of("minecraft:wheat*3=minenorth_harvest:baguette*1"), o -> o instanceof String);
        b.pop();

        b.comment("Raffinerie : objet en main, maintenir clic droit sur la raffinerie pour raffiner").push("refinery");
        REFINERY_TURNS = b.comment("Tours de pompe pour obtenir un produit (un clic droit maintenu = environ 5 tours par seconde)")
                .defineInRange("turnsPerProduct", 12, 1, 200);
        REFINERY_RECIPES = b.comment("Recettes de la raffinerie, format \"entrée*quantité=sortie*quantité\"")
                .defineList("recipes", java.util.List.of("minenorth_harvest:crude_oil*3=minenorth_harvest:gasoline*2"), o -> o instanceof String);
        b.pop();

        b.comment("Écologie : chaque arbre abattu ajoute 1 'dette', chaque replantation sur une souche en retire 1").push("ecology");
        ECO_DEBT_THRESHOLD = b.comment("À partir de cette dette, la forêt est 'épuisée' : plus de bonus et abattage plus dur (0 = désactivé)").defineInRange("debtThreshold", 3, 0, 100);
        ECO_EXTRA_HITS = b.comment("Coups supplémentaires quand la forêt est épuisée").defineInRange("extraHits", 2, 0, 50);
        b.pop();

        SPEC = b.build();
    }

    private HarvestConfig() {}
}
