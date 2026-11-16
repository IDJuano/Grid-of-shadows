package com.gridofshadows.common;

/**
 * Cuadrícula de ranuras (objeto + cantidad). La autoritativa vive en el servidor, que la envía solo a
 * su dueño; el cliente guarda una copia para dibujarla y para predecir su velocidad.
 */
public final class Inventory {
    public final byte[] item = new byte[Const.INV_SLOTS];   // Items.NONE = ranura vacía
    public final int[] count = new int[Const.INV_SLOTS];

    /** Cuántas unidades más de este objeto caben ahora mismo. */
    public int space(byte it) {
        int max = Items.stackMax(it), free = 0;
        for (int i = 0; i < item.length; i++) {
            if (item[i] == it) free += max - count[i];
            else if (item[i] == Items.NONE) free += max;
        }
        return free;
    }

    /** Añade n unidades (rellena pilas y luego ranuras vacías). Devuelve cuántas NO cupieron. */
    public int add(byte it, int n) {
        int max = Items.stackMax(it);
        for (int i = 0; i < item.length && n > 0; i++) {
            if (item[i] == it && count[i] < max) {
                int k = Math.min(n, max - count[i]);
                count[i] += k;
                n -= k;
            }
        }
        for (int i = 0; i < item.length && n > 0; i++) {
            if (item[i] == Items.NONE) {
                int k = Math.min(n, max);
                item[i] = it;
                count[i] = k;
                n -= k;
            }
        }
        return n;
    }

    public int count(byte it) {
        int total = 0;
        for (int i = 0; i < item.length; i++) if (item[i] == it) total += count[i];
        return total;
    }

    /** Quita n unidades, todo o nada. */
    public boolean remove(byte it, int n) {
        if (count(it) < n) return false;
        for (int i = item.length - 1; i >= 0 && n > 0; i--) {   // vacía primero las últimas pilas
            if (item[i] == it) {
                int k = Math.min(n, count[i]);
                count[i] -= k;
                n -= k;
                if (count[i] == 0) item[i] = Items.NONE;
            }
        }
        return true;
    }

    /** Peso total transportado. */
    public int load() {
        int w = 0;
        for (int i = 0; i < item.length; i++) if (item[i] != Items.NONE) w += count[i] * Items.weight(item[i]);
        return w;
    }

    /** Multiplicador de velocidad: 1 sin carga, MIN_SPEED_FACTOR con LOAD_CAPACITY o más. */
    public static float speedFactor(int load) {
        float t = Math.min(1f, Math.max(0f, load / (float) Const.LOAD_CAPACITY));
        return 1f - (1f - Const.MIN_SPEED_FACTOR) * t;
    }
}
