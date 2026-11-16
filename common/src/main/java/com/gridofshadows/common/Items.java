package com.gridofshadows.common;

/** Objetos que se pueden llevar en el inventario. Más adelante: hierro, azufre y herramientas. */
public final class Items {
    private Items() {}

    public static final byte NONE = 0, WOOD = 1, STONE = 2;

    public static int stackMax(byte item) { return 500; }

    /** Peso de una unidad; la carga total frena al jugador (GDD 5, penalización por peso). */
    public static int weight(byte item) { return 1; }
}
