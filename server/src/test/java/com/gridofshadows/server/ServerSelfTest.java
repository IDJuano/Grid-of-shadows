package com.gridofshadows.server;

import com.gridofshadows.common.Block;
import com.gridofshadows.common.Const;
import com.gridofshadows.common.Inventory;
import com.gridofshadows.common.Items;
import com.gridofshadows.common.Predictor;
import com.gridofshadows.common.Protocol;
import com.gridofshadows.common.VoxelWorld;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** Pruebas de la simulación autoritativa, sin Netty ni JUnit: ./gradlew :server:selfTest */
public final class ServerSelfTest {
    private static int checks, failures;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FALLO: " + what); }
    }

    private static boolean near(float a, float b, float eps) { return Math.abs(a - b) <= eps; }

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

        Inventory lastInventory() {
            byte[] raw = lastRaw(Protocol.S_INV);
            return raw == null ? null : (Inventory) Protocol.decodeServer(raw);
        }

        int count(byte type) {
            int n = 0;
            for (byte[] d : out) if (d[0] == type) n++;
            return n;
        }
    }

    static Player spawn(GameLoop g, Fake f, float x, float y) {
        g.join(f, "T");
        g.tick();
        Player p = g.playerOf(f);
        p.x = x;
        p.y = y;
        return p;
    }

    static void idle(GameLoop g, int ticks) { for (int i = 0; i < ticks; i++) g.tick(); }

    static void hit(GameLoop g, Fake f, int x, int y) {
        g.action(f, new Protocol.Action(Protocol.ACT_HIT, x, y));
        g.tick();
    }

    /** Una acción y la espera del enfriamiento, para encadenarlas sin que se descarten. */
    static void swing(GameLoop g, Fake f, int kind, int x, int y) {
        g.action(f, new Protocol.Action(kind, x, y));
        g.tick();
        idle(g, Const.ACTION_COOLDOWN_TICKS - 1);
    }

    public static void main(String[] args) {
        wallhack();
        speedhack();
        harvest();
        placing();
        fullInventory();
        weight();
        hitSound();
        prediction(0);
        prediction(Const.LOAD_CAPACITY);
        divergence();
        System.out.println(checks + " comprobaciones, " + failures + " fallos");
        if (failures > 0) System.exit(1);
    }

    static void wallhack() {
        VoxelWorld w = new VoxelWorld(64, 64);
        for (int y = 8; y <= 13; y++) w.set(12, y, Const.LAYER_OBSTACLE, Block.STONE);
        w.set(12, 10, Const.LAYER_OBSTACLE, Block.TREE);   // el único punto débil del muro
        GameLoop g = new GameLoop(w, 1, 0);
        Fake fa = new Fake(), fb = new Fake();
        Player a = spawn(g, fa, 10.5f, 10.5f), b = spawn(g, fb, 14.5f, 10.5f);
        a.angle = 0f;
        b.angle = (float) Math.PI;

        g.tick();
        check(fa.lastState().nEnt == 0, "A no recibe a B tras un muro");
        check(fa.lastRaw(Protocol.S_STATE).length == 19, "el paquete de A no lleva ni un byte sobre B");

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
        check(heard, "A oye los pasos de B hacia el este");
        check(!leaked, "oír a B nunca revela su posición");

        a.angle = (float) Math.PI;
        g.tick();
        check(fa.lastState().nEnt == 0, "B fuera del cono no se envía");

        // Mundo destructible: A tala el árbol del muro y B pasa a ser visible
        a.angle = 0f;
        for (int h = 0; h < 4; h++) swing(g, fa, Protocol.ACT_HIT, 12, 10);
        check(w.get(12, 10, Const.LAYER_OBSTACLE) == Block.AIR, "tras 4 golpes el árbol cae");
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
        Player p = spawn(g, f, 20.5f, 20.5f);
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

    static void harvest() {
        VoxelWorld w = new VoxelWorld(64, 64);
        w.set(12, 10, Const.LAYER_OBSTACLE, Block.TREE);
        w.set(10, 12, Const.LAYER_OBSTACLE, Block.ROCK);
        w.set(9, 10, Const.LAYER_OBSTACLE, Block.STONE);
        w.set(30, 10, Const.LAYER_OBSTACLE, Block.TREE);
        w.set(10, 0, Const.LAYER_OBSTACLE, Block.BEDROCK);
        GameLoop g = new GameLoop(w, 1, 0);
        Fake f = new Fake(), fo = new Fake();
        Player a = spawn(g, f, 10.5f, 10.5f);
        spawn(g, fo, 40.5f, 40.5f);

        hit(g, f, 12, 10);
        check(a.inv.count(Items.WOOD) == 6, "un golpe a un árbol da 6 de madera");
        check(f.lastInventory().count(Items.WOOD) == 6, "el servidor envía el inventario a su dueño");
        hit(g, f, 12, 10);
        check(a.inv.count(Items.WOOD) == 6, "el enfriamiento descarta el golpe inmediato");
        idle(g, Const.ACTION_COOLDOWN_TICKS);
        for (int i = 0; i < 3; i++) swing(g, f, Protocol.ACT_HIT, 12, 10);
        check(a.inv.count(Items.WOOD) == 24 && w.get(12, 10, Const.LAYER_OBSTACLE) == Block.AIR, "4 golpes talan el árbol: 24 de madera");
        for (int i = 0; i < 5; i++) swing(g, f, Protocol.ACT_HIT, 10, 12);
        check(a.inv.count(Items.STONE) == 25 && w.get(10, 12, Const.LAYER_OBSTACLE) == Block.AIR, "5 golpes agotan la roca: 25 de piedra");
        swing(g, f, Protocol.ACT_HIT, 9, 10);
        check(w.get(9, 10, Const.LAYER_OBSTACLE) == Block.STONE && a.inv.count(Items.STONE) == 25, "la piedra no se rompe a mano");
        swing(g, f, Protocol.ACT_HIT, 30, 10);
        check(w.get(30, 10, Const.LAYER_OBSTACLE) == Block.TREE && a.inv.count(Items.WOOD) == 24, "fuera de alcance no hay golpe");
        a.y = 2.5f;
        swing(g, f, Protocol.ACT_HIT, 10, 0);
        check(w.get(10, 0, Const.LAYER_OBSTACLE) == Block.BEDROCK, "el borde del mundo no se toca");
        check(fo.count(Protocol.S_INV) == 1, "el inventario solo se envía a su dueño");
        check(fo.count(Protocol.S_BLOCKS) >= 2, "los demás sí reciben los bloques que desaparecen");
    }

    static void placing() {
        VoxelWorld w = new VoxelWorld(64, 64);
        GameLoop g = new GameLoop(w, 1, 0);
        Fake f = new Fake(), fo = new Fake();
        Player a = spawn(g, f, 10.5f, 10.5f), o = spawn(g, fo, 40.5f, 40.5f);

        swing(g, f, Protocol.ACT_PLACE, 11, 10);
        check(w.get(11, 10, Const.LAYER_OBSTACLE) == Block.AIR, "sin madera no se construye");
        a.inv.add(Items.WOOD, 12);
        swing(g, f, Protocol.ACT_PLACE, 11, 10);
        check(w.get(11, 10, Const.LAYER_OBSTACLE) == Block.WOOD && a.inv.count(Items.WOOD) == 7, "construir cuesta 5 de madera");
        o.x = 12.5f;
        o.y = 10.5f;
        swing(g, f, Protocol.ACT_PLACE, 12, 10);
        check(w.get(12, 10, Const.LAYER_OBSTACLE) == Block.AIR && a.inv.count(Items.WOOD) == 7, "no se construye encima de alguien ni se cobra");
        o.x = 40.5f;
        swing(g, f, Protocol.ACT_PLACE, 12, 10);
        swing(g, f, Protocol.ACT_PLACE, 11, 12);
        check(a.inv.count(Items.WOOD) == 2 && w.get(11, 12, Const.LAYER_OBSTACLE) == Block.AIR, "con 2 de madera ya no alcanza");
        for (int i = 0; i < 6; i++) swing(g, f, Protocol.ACT_HIT, 11, 10);
        check(w.get(11, 10, Const.LAYER_OBSTACLE) == Block.AIR && a.inv.count(Items.WOOD) == 2, "un bloque de madera cae en 6 golpes y no devuelve nada");
    }

    static void fullInventory() {
        VoxelWorld w = new VoxelWorld(64, 64);
        w.set(12, 10, Const.LAYER_OBSTACLE, Block.TREE);
        GameLoop g = new GameLoop(w, 1, 0);
        Fake f = new Fake();
        Player a = spawn(g, f, 10.5f, 10.5f);
        a.inv.add(Items.WOOD, Const.INV_SLOTS * 500);
        for (int i = 0; i < 6; i++) swing(g, f, Protocol.ACT_HIT, 12, 10);
        check(w.get(12, 10, Const.LAYER_OBSTACLE) == Block.TREE && a.inv.count(Items.WOOD) == Const.INV_SLOTS * 500,
            "con el inventario lleno el golpe se rechaza");
        a.inv.remove(Items.WOOD, 100);
        for (int i = 0; i < 3; i++) swing(g, f, Protocol.ACT_HIT, 12, 10);
        check(w.get(12, 10, Const.LAYER_OBSTACLE) == Block.TREE, "los golpes rechazados no acumularon daño");
        swing(g, f, Protocol.ACT_HIT, 12, 10);
        check(w.get(12, 10, Const.LAYER_OBSTACLE) == Block.AIR, "con sitio de nuevo, el cuarto golpe tala el árbol");
    }

    static void weight() {
        GameLoop g = new GameLoop(new VoxelWorld(64, 64), 1, 0);
        Fake f = new Fake();
        Player p = spawn(g, f, 20.5f, 20.5f);
        float step = Const.PLAYER_SPEED * Const.TICK_DT;
        g.input(f, new Protocol.Input(0, 1, 0, (short) 0));
        g.tick();
        check(near(p.x - 20.5f, step, 1e-4f), "sin carga se avanza a velocidad completa");
        p.inv.add(Items.STONE, Const.LOAD_CAPACITY);
        float x1 = p.x;
        g.input(f, new Protocol.Input(1, 1, 0, (short) 0));
        g.tick();
        check(near(p.x - x1, step * Const.MIN_SPEED_FACTOR, 1e-4f), "con carga máxima se avanza más despacio");
    }

    static void hitSound() {
        VoxelWorld w = new VoxelWorld(64, 64);
        for (int y = 8; y <= 13; y++) w.set(12, y, Const.LAYER_OBSTACLE, Block.STONE);
        w.set(15, 10, Const.LAYER_OBSTACLE, Block.ROCK);
        w.set(41, 40, Const.LAYER_OBSTACLE, Block.ROCK);
        GameLoop g = new GameLoop(w, 1, 0);
        Fake fa = new Fake(), fb = new Fake();
        Player a = spawn(g, fa, 10.5f, 10.5f), b = spawn(g, fb, 14.5f, 10.5f);
        a.angle = 0f;
        hit(g, fb, 15, 10);
        Protocol.State s = fa.lastState();
        boolean heard = false;
        for (int k = 0; k < s.nSnd; k++) {
            heard |= s.sndKind[k] == Protocol.SND_HIT && Math.abs(Protocol.shortToAngle(s.sndAngle[k])) < 0.1f
                && near(s.sndVol[k], 255 * (1 - 4f / Const.HIT_HEARING_RANGE), 2f);
        }
        check(s.nEnt == 0 && heard, "A oye el golpe de B tras el muro (dirección y volumen) sin verlo");

        b.x = 40.5f;
        b.y = 40.5f;
        idle(g, Const.ACTION_COOLDOWN_TICKS);
        hit(g, fb, 41, 40);
        check(fa.lastState().nSnd == 0, "un golpe fuera del alcance de audición no se oye");
    }

    /** Cliente real (Predictor) contra el servidor real, con 3 ticks de latencia en las respuestas. */
    static void prediction(int load) {
        VoxelWorld sw = new VoxelWorld(64, 64), cw = new VoxelWorld(64, 64);
        for (int y = 0; y < 64; y++) {
            sw.set(30, y, Const.LAYER_OBSTACLE, Block.STONE);
            cw.set(30, y, Const.LAYER_OBSTACLE, Block.STONE);
        }
        GameLoop g = new GameLoop(sw, 1, 0);
        Fake f = new Fake();
        Player p = spawn(g, f, 20.5f, 20.5f);
        Predictor pred = new Predictor(cw, 20.5f, 20.5f);
        if (load > 0) {
            p.inv.add(Items.STONE, load);
            pred.setLoad(load);
        }

        ArrayDeque<Protocol.State> inFlight = new ArrayDeque<>();
        boolean stable = true;
        for (int t = 0; t < 150; t++) {
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
        check(stable, "con latencia y sin discrepancias, la reconciliación no mueve la predicción (carga " + load + ")");
        for (int t = 0; t < 5; t++) { g.tick(); inFlight.addLast(f.lastState()); }
        Protocol.State last = inFlight.peekLast();
        pred.reconcile(last.x, last.y, last.ackSeq);
        check(Math.abs(pred.me.x - p.x) < 1e-4f && Math.abs(pred.me.y - p.y) < 1e-4f && pred.pendingCount() == 0,
            "tras drenar la cola, cliente y servidor coinciden (carga " + load + ")");
        check(p.x < 30f - Const.BODY_RADIUS + 1e-3f && p.x > 29f, "ambos se detienen en el muro (carga " + load + ")");
    }

    /** El cliente no conoce un muro que el servidor sí tiene: el servidor manda y el cliente se corrige. */
    static void divergence() {
        VoxelWorld sw = new VoxelWorld(64, 64), cw = new VoxelWorld(64, 64);
        for (int y = 0; y < 64; y++) sw.set(25, y, Const.LAYER_OBSTACLE, Block.STONE);
        GameLoop g = new GameLoop(sw, 1, 0);
        Fake f = new Fake();
        Player p = spawn(g, f, 20.5f, 20.5f);
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
