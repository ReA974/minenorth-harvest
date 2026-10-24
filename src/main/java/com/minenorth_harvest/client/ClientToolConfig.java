package com.minenorth_harvest.client;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Réglages d'affichage en première personne du marteau-piqueur et de la tronçonneuse (fichier config/minenorth_harvest-client.toml, créé côté client).
 * Le fichier est relu à chaud : on peut le modifier jeu ouvert et voir le résultat tout de suite.
 * Repère : x = droite, y = haut, z = devant (négatif = loin de la caméra), en blocs ; rotations en degrés (appliquées dans l'ordre X, Y, Z).
 */
public final class ClientToolConfig {
    private ClientToolConfig() {}

    public static final class Tool {
        public final ForgeConfigSpec.DoubleValue scale, rotX, rotY, rotZ, posX, posY, posZ, handDrop;

        Tool(ForgeConfigSpec.Builder b, String section, String label, double scale, double rx, double ry, double rz, double x, double y, double z, double drop) {
            b.comment("Affichage en 1re personne : " + label).push(section);
            this.scale = b.comment("Taille de l'outil (1 = taille du modèle)").defineInRange("echelle", scale, 0.2, 4.0);
            this.rotX = b.comment("Rotation X (positif = pointe vers le haut, négatif = vers le bas)").defineInRange("rotationX", rx, -180.0, 180.0);
            this.rotY = b.comment("Rotation Y (positif = pointe vers la gauche, négatif = vers la droite)").defineInRange("rotationY", ry, -180.0, 180.0);
            this.rotZ = b.comment("Rotation Z (roulis)").defineInRange("rotationZ", rz, -180.0, 180.0);
            this.posX = b.comment("Décalage droite (+) / gauche (-)").defineInRange("positionX", x, -2.0, 2.0);
            this.posY = b.comment("Décalage haut (+) / bas (-)").defineInRange("positionY", y, -2.0, 2.0);
            this.posZ = b.comment("Distance devant la caméra (négatif ; plus c'est petit, plus l'outil est loin)").defineInRange("positionZ", z, -4.0, -0.1);
            this.handDrop = b.comment("Abaisse les mains par rapport aux poignées (blocs)").defineInRange("abaisserMains", drop, -0.5, 0.5);
            b.pop();
        }
    }

    public static final ForgeConfigSpec SPEC;
    public static final Tool JACKHAMMER, CHAINSAW;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        JACKHAMMER = new Tool(b, "marteau_piqueur", "marteau-piqueur", 0.95, 15, -40, 0, 0.06, -0.30, -1.2, 0.0);
        CHAINSAW = new Tool(b, "tronconneuse", "tronçonneuse", 1.15, -12, 6, 0, 0.12, -0.42, -0.95, 0.0);
        SPEC = b.build();
    }
}
