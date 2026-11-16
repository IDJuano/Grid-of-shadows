package com.gridofshadows.common;

/**
 * Generación determinista: el cliente reconstruye el mismo mundo a partir de la semilla,
 * así el servidor solo envía los cambios (bloques rotos o colocados).
 * Solo aritmética entera: el resultado es idéntico en la JVM y en JS (TeaVM).
 */
public final class WorldGen {
    private WorldGen() {}

    /** Mulberry32. */
    private static final class Rng {
        private int s;

        Rng(int seed) { s = seed; }

        int next() {
            s += 0x6D2B79F5;
            int t = s;
            t = (t ^ (t >>> 15)) * (t | 1);
            t ^= t + (t ^ (t >>> 7)) * (t | 61);
            return t ^ (t >>> 14);
        }

        int below(int n) { return (next() >>> 1) % n; }
    }

    public static VoxelWorld generate(int seed, int w, int h) {
        VoxelWorld world = new VoxelWorld(w, h);
        Rng r = new Rng(seed);

        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                world.set(x, y, Const.LAYER_GROUND, Block.GRASS);
                byte b = Block.AIR;
                if (x == 0 || y == 0 || x == w - 1 || y == h - 1) {
                    b = Block.BEDROCK;
                } else {
                    int v = r.below(100);
                    if (v < 7) b = Block.TREE; else if (v < 10) b = Block.ROCK;
                }
                world.set(x, y, Const.LAYER_OBSTACLE, b);
            }
        }

        // Ruinas de piedra con una puerta: esquinas y habitaciones donde emboscar
        int ruins = (w * h) / 600;
        for (int k = 0; k < ruins; k++) {
            int rw = 6 + r.below(4), rh = 5 + r.below(3);
            int x0 = 6 + r.below(w - rw - 12), y0 = 3 + r.below(h - rh - 6);
            for (int y = y0; y < y0 + rh; y++) {
                for (int x = x0; x < x0 + rw; x++) {
                    boolean edge = x == x0 || y == y0 || x == x0 + rw - 1 || y == y0 + rh - 1;
                    world.set(x, y, Const.LAYER_OBSTACLE, edge ? Block.STONE : Block.AIR);
                }
            }
            int side = r.below(4), gx, gy;
            if (side < 2) { gx = x0 + 1 + r.below(rw - 2); gy = side == 0 ? y0 : y0 + rh - 1; }
            else { gx = side == 2 ? x0 : x0 + rw - 1; gy = y0 + 1 + r.below(rh - 2); }
            world.set(gx, gy, Const.LAYER_OBSTACLE, Block.AIR);
        }
        return world;
    }
}
