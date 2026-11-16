package com.gridofshadows.common;

/**
 * Mundo de vóxeles dividido en chunks de 32 x 32 x 16. Cada chunk es un byte[] plano
 * (localidad de caché, TDD 4). La capa z=1 es la de obstáculos: lo sólido bloquea paso y vista.
 */
public final class VoxelWorld {
    public static final class Chunk {
        public final byte[] blocks = new byte[Const.CHUNK_W * Const.CHUNK_H * Const.CHUNK_D];
    }

    public final int width, height;
    private final int chunksX;
    private final Chunk[] chunks;

    public VoxelWorld(int width, int height) {
        if (width % Const.CHUNK_W != 0 || height % Const.CHUNK_H != 0) {
            throw new IllegalArgumentException("El mundo debe ser múltiplo del tamaño de chunk");
        }
        this.width = width;
        this.height = height;
        this.chunksX = width >> Const.CHUNK_SHIFT;
        this.chunks = new Chunk[chunksX * (height >> Const.CHUNK_SHIFT)];
        for (int i = 0; i < chunks.length; i++) chunks[i] = new Chunk();
    }

    public boolean inBounds(int x, int y) {
        return x >= 0 && y >= 0 && x < width && y < height;
    }

    private static int index(int x, int y, int z) {
        int lx = x & (Const.CHUNK_W - 1), ly = y & (Const.CHUNK_H - 1);
        return (z * Const.CHUNK_H + ly) * Const.CHUNK_W + lx;
    }

    private Chunk chunkAt(int x, int y) {
        return chunks[(y >> Const.CHUNK_SHIFT) * chunksX + (x >> Const.CHUNK_SHIFT)];
    }

    /** Fuera del mundo todo es lecho de roca. */
    public byte get(int x, int y, int z) {
        if (!inBounds(x, y) || z < 0 || z >= Const.CHUNK_D) return Block.BEDROCK;
        return chunkAt(x, y).blocks[index(x, y, z)];
    }

    public void set(int x, int y, int z, byte block) {
        if (!inBounds(x, y) || z < 0 || z >= Const.CHUNK_D) return;
        chunkAt(x, y).blocks[index(x, y, z)] = block;
    }

    public boolean solid(int x, int y) {
        return get(x, y, Const.LAYER_OBSTACLE) != Block.AIR;
    }

    /** ¿Cabe un cuerpo circular (aproximado por su cuadrado) en (x, y)? */
    public boolean free(float x, float y, float r) {
        return !solid((int) Math.floor(x - r), (int) Math.floor(y - r))
            && !solid((int) Math.floor(x + r), (int) Math.floor(y - r))
            && !solid((int) Math.floor(x - r), (int) Math.floor(y + r))
            && !solid((int) Math.floor(x + r), (int) Math.floor(y + r));
    }
}
