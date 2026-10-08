package com.minenorth_harvest.zone;

public enum ZoneType {
    /** Zone de bûcheronnage : le mini-jeu d'abattage y est autorisé. */
    BUCHERON("bucheron"),
    /** Verger : les fruits y poussent plus vite dès qu'un joueur est dans la zone. */
    VERGER("verger"),
    /** Zone de chasse : des animaux apparaissent autour des joueurs présents. Détection 2D (hauteur ignorée). */
    CHASSE("chasse"),
    /** Gisement de pétrole : des gisements sont générés dans la roche de la zone et se régénèrent une fois minés. */
    PETROLE("petrole");

    private final String id;

    ZoneType(String id) { this.id = id; }

    public String id() { return id; }

    public static ZoneType byId(String id) {
        for (ZoneType t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return null;
    }
}
