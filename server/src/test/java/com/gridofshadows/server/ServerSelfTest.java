package com.gridofshadows.server;

import com.gridofshadows.common.Block;
import com.gridofshadows.common.Const;
import com.gridofshadows.common.Predictor;
import com.gridofshadows.common.Protocol;
import com.gridofshadows.common.VoxelWorld;

import java.util.ArrayList;
import java.util.List;

/** Pruebas de la simulación autoritativa, sin Netty ni JUnit: ./gradlew :server:selfTest */
public final class ServerSelfTest {
    private static int checks, failures;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FALLO: " + what); }
    }

    static final class Fake implements Conn {
        final List<byte[]> out = new ArrayList<>();

        @Override public void send(byte[] d) { out.add(d); }
        @Override public void close() {}

        byte[] lastRaw(byte type) {
            for (int i = out.size() - 1; i >= 0; i--) if (out.get(i)[0] == type) return out.get(i);
            return null;
        }

        Protocol.State lastState() {
            byte[] raw = lastRaw(Protocol.S_STATE);
            return raw == null ? null : (Protocol.State) Protocol.decodeServer(raw);
        }

        int count(byte type) {
            int n = 0;
            for (byte[] d : out) if (d[0] == type) n++;
            return n;
        }
    }

    public static void main(String[] args) {
        wallhack();
        speedhack();
        actions();
        prediction();
        divergence();
        System.out.println(checks + " comprobaciones, " + failures + " fallos");
        if (failures > 0) System.exit(1);
    }

    static void wallhack() {
        VoxelWorld w = new VoxelWorld(64, 64);
        for (int y = 8; y <= 13; y++) w.set(12, y, Const.LAYER_OBSTACLE, Block.STONE);
        GameLoop g = new GameLoop(w, 1, 0);
        Fake fa = new Fake(), fb = new Fake();
        g.join(fa, "A");
        g.join(fb, "B");
        g.tick();
        Player a = g.playerOf(fa), b = g.playerOf(fb);
        a.x = 10.5f; a.y = 10.5f; a.angle = 0f;
        b.x = 14.5f; b.y = 10.5f; b.angle = (float) Math.PI;

        g.tick();
        Protocol.State sa = fa.lastState();
        check(sa.nEnt == 0, "A no recibe a B tras un muro");
        check(fa.out.get(fa.out.size() - 1).length == 19, "el paquete de A no lleva ni un byte sobre B");

        // B camina hacia el muro: A lo oye (dirección y volumen), pero sigue sin verlo
        boolean heard = false, leaked = false;
        for (int i = 0; i < 6; i++) {
            g.input(fb, new Protocol.Input(i, -1, 0, Protocol.angleToShort((float) Math.PI)));
            g.tick();
            Protocol.State s = fa.lastState();
            leaked |= s.nEnt != 0;
            for (int k = 0; k < s.nSnd; k++) {
                heard |= s.sndKind[k] == Protocol.SND_STEP && Math.abs(Protocol.shortToAngle(s.sndAngle[k])) < 0.1f && s.sndVol[k] > 0;
            }
        }
        check(heard, "A oye los pasos de B hacia el este (sector 0)");
        check(!leaked, "oír a B nunca revela su posición");

        // Fuera del cono: A mira hacia el oeste, B queda a su espalda aunque no haya muro
        a.angle = (float) Math.PI;
        g.tick();
        check(fa.lastState().nEnt == 0, "B fuera del cono no se envía");

        // Mundo destructible: A rompe el muro y B pasa a ser visible
        a.angle = 0f;
        g.action(fa, new Protocol.Action(Protocol.ACT_BREAK, 12, 10));
        g.tick();
        check(w.get(12, 10, Const.LAYER_OBSTACLE) == Block.AIR, "el servidor rompe el bloque");
        Protocol.State after = fa.lastState();
        check(after.nEnt == 1 && after.entId[0] == b.id, "al abrirse el hueco, A ve a B");
        check(fb.count(Protocol.S_BLOCKS) >= 1, "B recibe el cambio de bloque");

        Fake fc = new Fake();
        g.join(fc, "C");
        g.tick();
        Protocol.Blocks late = (Protocol.Blocks) Protocol.decodeServer(fc.lastRaw(Protocol.S_BLOCKS));
        check(late != null && late.count == 1 && late.x[0] == 12 && late.y[0] == 10 && late.id[0] == Block.AIR,
            "quien entra tarde recibe los cambios acumulados");
    }

    static void speedhack() {
        GameLoop g = new GameLoop(new VoxelWorld(64, 64), 1, 0);
        Fake f = new Fake();
        g.join(f, "S");
        g.tick();
        Player p = g.playerOf(f);
        p.x = 20.5f; p.y = 20.5f;
        for (int i = 0; i < 100; i++) g.input(f, new Protocol.Input(i, 1, 0, (short) 0));
        g.tick();
        float step = Const.PLAYER_SPEED * Const.TICK_DT;
        check(p.x - 20.5f <= 2 * step + 1e-4f, "100 inputs de golpe avanzan como mucho 2 pasos por tick");
        for (int i = 0; i < 40; i++) g.tick();
        check(p.x - 20.5f <= (Const.MAX_PENDING_INPUTS + 0.01f) * step, "el exceso de inputs se descarta");
        g.input(f, new Protocol.Input(5, 1, 0, (short) 0));   // seq repetido
        float before = p.x;
        g.tick();
        check(p.x == before, "un seq repetido se ignora");
    }

    static void actions() {
        VoxelWorld w = new VoxelWorld(64, 64);
        w.set(30, 10, Const.LAYER_OBSTACLE, Block.STONE);
        w.set(12, 10, Const.LAYER_OBSTACLE, Block.STONE);
        for (int x = 0; x < 64; x++) { w.set(x, 0, Const.LAYER_OBSTACLE, Block.BEDROCK); }
        GameLoop g = new GameLoop(w, 1, 0);
        Fake f = new Fake(), fo = new Fake();
        g.join(f, "A");
        g.join(fo, "O");
        g.tick();
        Player a = g.playerOf(f), o = g.playerOf(fo);
        a.x = 10.5f; a.y = 10.5f;
        o.x = 40.5f; o.y = 40.5f;

        g.action(f, new Protocol.Action(Protocol.ACT_BREAK, 30, 10));
        g.tick();
        check(w.get(30, 10, Const.LAYER_OBSTACLE) == Block.STONE, "romper fuera de alcance se rechaza");

        g.action(f, new Protocol.Action(Protocol.ACT_BREAK, 12, 10));
        g.tick();
        check(w.get(12, 10, Const.LAYER_OBSTACLE) == Block.AIR, "romper al alcance funciona");

        g.action(f, new Protocol.Action(Protocol.ACT_PLACE, 13, 10));
        g.tick();
        check(w.get(13, 10, Const.LAYER_OBSTACLE) == Block.AIR, "el cooldown de 200 ms bloquea la acción inmediata");
        for (int i = 0; i < 4; i++) g.tick();
        g.action(f, new Protocol.Action(Protocol.ACT_PLACE, 13, 10));
        g.tick();
        check(w.get(13, 10, Const.LAYER_OBSTACLE) == Block.WOOD, "colocar madera tras el cooldown");

        o.x = 11.5f; o.y = 12.5f;
        for (int i = 0; i < 5; i++) g.tick();
        g.action(f, new Protocol.Action(Protocol.ACT_PLACE, 11, 12));
        g.tick();
        check(w.get(11, 12, Const.LAYER_OBSTACLE) == Block.AIR, "no se puede construir encima de alguien");

        for (int i = 0; i < 5; i++) g.tick();
        a.y = 2.5f;
        g.action(f, new Protocol.Action(Protocol.ACT_BREAK, 10, 0));
        g.tick();
        check(w.get(10, 0, Const.LAYER_OBSTACLE) == Block.BEDROCK, "el lecho de roca es indestructible");
    }

    /** Cliente real (Predictor) contra el servidor real, con 3 ticks de latencia en las respuestas. */
    static void prediction() {
        VoxelWorld sw = new VoxelWorld(64, 64), cw = new VoxelWorld(64, 64);
        for (int y = 0; y < 64; y++) {
            sw.set(30, y, Const.LAYER_OBSTACLE, Block.STONE);
            cw.set(30, y, Const.LAYER_OBSTACLE, Block.STONE);
        }
        GameLoop g = new GameLoop(sw, 1, 0);
        Fake f = new Fake();
        g.join(f, "P");
        g.tick();
        Player p = g.playerOf(f);
        p.x = 20.5f; p.y = 20.5f;
        Predictor pred = new Predictor(cw, 20.5f, 20.5f);

        java.util.ArrayDeque<Protocol.State> inFlight = new java.util.ArrayDeque<>();
        boolean stable = true;
        for (int t = 0; t < 60; t++) {
            g.input(f, pred.step(1, t % 20 < 10 ? 0 : 1, 0f));
            g.tick();
            inFlight.addLast(f.lastState());
            if (inFlight.size() > 3) {
                Protocol.State s = inFlight.pollFirst();
                float bx = pred.me.x, by = pred.me.y;
                pred.reconcile(s.x, s.y, s.ackSeq);
                stable &= Math.abs(pred.me.x - bx) < 1e-4f && Math.abs(pred.me.y - by) < 1e-4f;
            }
        }
        check(stable, "con latencia y sin discrepancias, la reconciliación no mueve la predicción");
        for (int t = 0; t < 5; t++) { g.tick(); inFlight.addLast(f.lastState()); }
        Protocol.State last = inFlight.peekLast();
        pred.reconcile(last.x, last.y, last.ackSeq);
        check(Math.abs(pred.me.x - p.x) < 1e-4f && Math.abs(pred.me.y - p.y) < 1e-4f && pred.pendingCount() == 0,
            "tras drenar la cola, cliente y servidor coinciden");
        check(p.x < 30f - Const.BODY_RADIUS + 1e-3f && p.x > 29f, "ambos se detienen en el muro");
    }

    /** El cliente no conoce un muro que el servidor sí tiene: el servidor manda y el cliente se corrige. */
    static void divergence() {
        VoxelWorld sw = new VoxelWorld(64, 64), cw = new VoxelWorld(64, 64);
        for (int y = 0; y < 64; y++) sw.set(25, y, Const.LAYER_OBSTACLE, Block.STONE);
        GameLoop g = new GameLoop(sw, 1, 0);
        Fake f = new Fake();
        g.join(f, "D");
        g.tick();
        Player p = g.playerOf(f);
        p.x = 20.5f; p.y = 20.5f;
        Predictor pred = new Predictor(cw, 20.5f, 20.5f);
        for (int t = 0; t < 40; t++) {
            g.input(f, pred.step(1, 0, 0f));
            g.tick();
            Protocol.State s = f.lastState();
            pred.reconcile(s.x, s.y, s.ackSeq);
        }
        for (int t = 0; t < 3; t++) g.tick();
        Protocol.State s = f.lastState();
        pred.reconcile(s.x, s.y, s.ackSeq);
        check(p.x < 25f - Const.BODY_RADIUS + 1e-3f, "el servidor frena al jugador en el muro que el cliente no conocía");
        check(Math.abs(pred.me.x - p.x) < 1e-4f, "el cliente acaba en la posición del servidor");
    }
}
