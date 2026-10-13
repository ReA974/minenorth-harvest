package com.minenorth_harvest.block;

/**
 * Moulin à meule 3 x 3 x 2 : le maître est la cellule centrale du bas (la cuve) ; les 8 cellules de la couronne puis les 9 cellules
 * de l'étage sont des cellules annexes. Le décor est dessiné par le moteur de rendu (la meule tourne autour de l'axe central).
 */
final class MillstoneLayout {
    private MillstoneLayout() {}

    static final int[][] PARTS = {
            {-1, 0, -1}, {-1, 0, 0}, {-1, 0, 1}, {0, 0, -1}, {0, 0, 1}, {1, 0, -1}, {1, 0, 0}, {1, 0, 1},
            {-1, 1, -1}, {-1, 1, 0}, {-1, 1, 1}, {0, 1, -1}, {0, 1, 0}, {0, 1, 1}, {1, 1, -1}, {1, 1, 0}, {1, 1, 1}};

    private static final float[][] WALL = {{0, 0, 0, 16, 12, 16}};
    private static final float[][] NONE = {};
    private static final float[][] AXLE = {{6, 0, 6, 10, 16, 10}};
    private static final float[][] POST_NW = {{0, 0, 0, 3, 16, 3}}, POST_NE = {{13, 0, 0, 16, 16, 3}},
            POST_SW = {{0, 0, 13, 3, 16, 16}}, POST_SE = {{13, 0, 13, 16, 16, 16}};

    static final float[][] MASTER = {{0, 0, 0, 16, 12, 16}, {6, 12, 6, 10, 16, 10}};

    /** Boîtes de collision par cellule annexe (même ordre que PARTS) : murs de la cuve, poteaux d'angle et axe central. */
    static final float[][][] BOXES = {
            WALL, WALL, WALL, WALL, WALL, WALL, WALL, WALL,
            POST_NW, NONE, POST_SW, NONE, AXLE, NONE, POST_NE, NONE, POST_SE};
}
