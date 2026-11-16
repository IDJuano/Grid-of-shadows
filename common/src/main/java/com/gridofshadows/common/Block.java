package com.gridofshadows.common;

public final class Block {
    private Block() {}

    public static final byte AIR = 0, GRASS = 1, STONE = 2, TREE = 3, ROCK = 4, BEDROCK = 5, WOOD = 6;

    /** Golpes a mano para destruirlo. 0 = no se puede con herramientas de Tier 1 (piedra, lecho de roca). */
    public static int hitsToBreak(byte b) {
        switch (b) {
            case TREE: return 4;
            case ROCK: return 5;
            case WOOD: return 6;
            default: return 0;
        }
    }

    /** Qué suelta cada golpe (nada para la madera construida) y cuánto. */
    public static byte dropItem(byte b) {
        switch (b) {
            case TREE: return Items.WOOD;
            case ROCK: return Items.STONE;
            default: return Items.NONE;
        }
    }

    public static int dropPerHit(byte b) {
        switch (b) {
            case TREE: return 6;
            case ROCK: return 5;
            default: return 0;
        }
    }
}
