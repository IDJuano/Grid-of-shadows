package com.gridofshadows.common;

public final class Block {
    private Block() {}

    public static final byte AIR = 0, GRASS = 1, STONE = 2, TREE = 3, ROCK = 4, BEDROCK = 5, WOOD = 6;

    public static boolean breakable(byte b) {
        return b == STONE || b == TREE || b == ROCK || b == WOOD;
    }
}
