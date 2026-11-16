package com.gridofshadows.common;

import java.util.ArrayDeque;

/**
 * Predicción del lado del cliente (TDD 2): mueve al jugador al instante con los mismos pasos que el
 * servidor y, cuando llega el estado autoritativo, parte de él y re-aplica los inputs aún sin confirmar.
 * Si el servidor coincide, la corrección es nula; si discrepa (un muro nuevo, un descarte), se ajusta sola.
 */
public final class Predictor {
    public final Body me = new Body();       // posición predicha tras el último paso
    public float prevX, prevY;               // posición del paso anterior, para interpolar al dibujar

    private final VoxelWorld world;
    private final ArrayDeque<Protocol.Input> pending = new ArrayDeque<>();
    private int seq, load;

    public Predictor(VoxelWorld world, float x, float y) {
        this.world = world;
        me.x = prevX = x;
        me.y = prevY = y;
    }

    /** Carga actual (la comunica el servidor con el inventario): frena igual que en el servidor. */
    public void setLoad(int load) { this.load = load; }

    private float speed() { return Const.PLAYER_SPEED * Inventory.speedFactor(load); }

    /** Un paso fijo de 20 Hz: lo aplica localmente y devuelve el input que hay que enviar. */
    public Protocol.Input step(int mx, int my, float angle) {
        Protocol.Input in = new Protocol.Input(seq++, mx, my, Protocol.angleToShort(angle));
        prevX = me.x;
        prevY = me.y;
        me.angle = angle;
        Movement.move(world, me, mx, my, speed(), Const.TICK_DT);
        pending.addLast(in);
        if (pending.size() > 200) pending.pollFirst();   // el servidor dejó de responder
        return in;
    }

    public void reconcile(float serverX, float serverY, int ackSeq) {
        me.x = serverX;
        me.y = serverY;
        while (!pending.isEmpty() && pending.peekFirst().seq <= ackSeq) pending.pollFirst();
        for (Protocol.Input in : pending) Movement.move(world, me, in.mx, in.my, speed(), Const.TICK_DT);
    }

    public int pendingCount() { return pending.size(); }
}
