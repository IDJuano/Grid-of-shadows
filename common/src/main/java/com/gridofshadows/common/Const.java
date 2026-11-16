package com.gridofshadows.common;

/** Constantes compartidas por servidor y cliente. */
public final class Const {
    private Const() {}

    public static final int TICK_RATE = 20;                      // Hz del servidor (TDD 2)
    public static final float TICK_DT = 1f / TICK_RATE;

    public static final int CHUNK_SHIFT = 5;                     // chunks de 32 x 32 x 16 (TDD 4)
    public static final int CHUNK_W = 1 << CHUNK_SHIFT, CHUNK_H = 1 << CHUNK_SHIFT, CHUNK_D = 16;
    public static final int LAYER_GROUND = 0, LAYER_OBSTACLE = 1; // capa 1 = lo que bloquea paso y vista

    public static final int WORLD_W = 128, WORLD_H = 96;         // casillas; múltiplos del chunk

    public static final float PLAYER_SPEED = 5f;                 // casillas por segundo
    public static final float BOT_SPEED = 2.2f;
    public static final float BODY_RADIUS = 0.3f;
    public static final float REACH = 3.5f;                      // alcance para romper o colocar
    public static final float DEFAULT_FOV = 2.4434610f;          // 140 grados en radianes
    public static final float DEFAULT_RANGE = 12f;
    public static final float HEARING_RANGE = 11f;
    public static final float STRIDE = 0.9f;                     // distancia entre pasos audibles
    public static final int MAX_PENDING_INPUTS = 6;
}
