package com.gridofshadows.common;

/** Paso de movimiento con colisión, idéntico en el servidor y en la predicción del cliente. */
public final class Movement {
    private Movement() {}

    /** Mueve el cuerpo eje por eje (desliza por las paredes). Devuelve la distancia recorrida. */
    public static float move(VoxelWorld w, Body b, float dirX, float dirY, float speed, float dt) {
        float len = (float) Math.sqrt(dirX * dirX + dirY * dirY);
        if (len < 1e-6f) return 0f;
        float s = speed * dt / len;
        float ox = b.x, oy = b.y;
        float nx = b.x + dirX * s;
        if (w.free(nx, b.y, Const.BODY_RADIUS)) b.x = nx;
        float ny = b.y + dirY * s;
        if (w.free(b.x, ny, Const.BODY_RADIUS)) b.y = ny;
        float mx = b.x - ox, my = b.y - oy;
        return (float) Math.sqrt(mx * mx + my * my);
    }
}
