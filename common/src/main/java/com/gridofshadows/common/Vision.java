package com.gridofshadows.common;

/**
 * Línea de visión sobre la retícula de vóxeles. La usa el servidor para decidir qué entidades
 * incluir en el paquete de cada jugador (anti-wallhack) y el cliente para dibujar la niebla.
 */
public final class Vision {
    private Vision() {}

    public static float wrapPi(float a) {
        final float pi = (float) Math.PI;
        while (a > pi) a -= 2 * pi;
        while (a < -pi) a += 2 * pi;
        return a;
    }

    /**
     * DDA (Amanatides y Woo). Devuelve la distancia hasta el primer bloque sólido, o max si no hay
     * ninguno antes. Si hit != null, hit[0] recibe el índice y*ancho+x de esa casilla (o -1).
     */
    public static float cast(VoxelWorld w, float ox, float oy, float angle, float max, int[] hit) {
        float dx = (float) Math.cos(angle), dy = (float) Math.sin(angle);
        if (dx == 0f) dx = 1e-9f;
        if (dy == 0f) dy = 1e-9f;
        int mx = (int) Math.floor(ox), my = (int) Math.floor(oy);
        int sx = dx > 0 ? 1 : -1, sy = dy > 0 ? 1 : -1;
        float ax = Math.abs(1f / dx), ay = Math.abs(1f / dy);
        float tx = (dx > 0 ? mx + 1 - ox : ox - mx) * ax;
        float ty = (dy > 0 ? my + 1 - oy : oy - my) * ay;
        if (hit != null) hit[0] = -1;
        for (;;) {
            float d;
            if (tx < ty) { d = tx; tx += ax; mx += sx; } else { d = ty; ty += ay; my += sy; }
            if (d >= max) return max;
            if (w.solid(mx, my)) {
                if (hit != null) hit[0] = my * w.width + mx;
                return d;
            }
        }
    }

    private static boolean clear(VoxelWorld w, float ox, float oy, float tx, float ty) {
        float dx = tx - ox, dy = ty - oy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 1e-4f) return true;
        return cast(w, ox, oy, (float) Math.atan2(dy, dx), d, null) >= d;
    }

    /**
     * ¿Ve el observador (ox, oy), que mira hacia facing con campo de visión fov, el punto (tx, ty)?
     * Comprueba alcance, ángulo y línea de visión. Prueba el centro y los dos costados del cuerpo,
     * así que asomar medio cuerpo por una esquina sí delata.
     */
    public static boolean canSee(VoxelWorld w, float ox, float oy, float facing, float fov, float range,
                                 float tx, float ty) {
        float dx = tx - ox, dy = ty - oy;
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d > range) return false;
        if (d < 0.05f) return true;
        float ang = (float) Math.atan2(dy, dx);
        if (Math.abs(wrapPi(ang - facing)) > fov * 0.5f) return false;
        float px = -dy / d * Const.BODY_RADIUS, py = dx / d * Const.BODY_RADIUS;
        return clear(w, ox, oy, tx, ty) || clear(w, ox, oy, tx + px, ty + py) || clear(w, ox, oy, tx - px, ty - py);
    }

    /** Abanico de n+1 rayos entre facing - fov/2 y facing + fov/2: dist[i] y hit[i] (casilla o -1). */
    public static void fan(VoxelWorld w, float ox, float oy, float facing, float fov, float range, int n,
                           float[] dist, int[] hit) {
        int[] h = new int[1];
        for (int i = 0; i <= n; i++) {
            float a = facing - fov * 0.5f + fov * i / n;
            dist[i] = cast(w, ox, oy, a, range, h);
            hit[i] = h[0];
        }
    }
}
