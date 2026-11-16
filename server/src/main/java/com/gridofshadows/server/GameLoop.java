package com.gridofshadows.server;

import com.gridofshadows.common.Block;
import com.gridofshadows.common.Const;
import com.gridofshadows.common.Movement;
import com.gridofshadows.common.Protocol;
import com.gridofshadows.common.Vision;
import com.gridofshadows.common.VoxelWorld;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.LockSupport;

/**
 * Simulación autoritativa a 20 Hz. Los hilos de red solo encolan comandos (TDD 5.A); todo el estado
 * del juego lo toca únicamente el hilo del bucle, así no hay condiciones de carrera.
 */
public final class GameLoop {
    static final int MAX_PLAYERS = 64;
    private static final float TWO_PI = (float) (2 * Math.PI);

    private sealed interface Cmd permits Join, Leave, In, Act {}
    private record Join(Conn conn, String name) implements Cmd {}
    private record Leave(Conn conn) implements Cmd {}
    private record In(Conn conn, Protocol.Input input) implements Cmd {}
    private record Act(Conn conn, Protocol.Action action) implements Cmd {}

    private final VoxelWorld world;
    private final int seed;
    private final Random rng;
    private final ConcurrentLinkedQueue<Cmd> queue = new ConcurrentLinkedQueue<>();
    private final Map<Conn, Player> byConn = new HashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final Map<Integer, Byte> edits = new LinkedHashMap<>();   // cambios respecto al mundo generado
    private int nextId = 0, tick;
    private volatile boolean running;

    public GameLoop(VoxelWorld world, int seed, int bots) {
        this.world = world;
        this.seed = seed;
        this.rng = new Random(seed);
        for (int i = 0; i < bots; i++) {
            Bot b = new Bot(allocId());
            float[] s = findSpawn(true);
            b.x = s[0];
            b.y = s[1];
            b.heading = rng.nextFloat() * TWO_PI;
            entities.add(b);
        }
    }

    // ------------------------------------------------------------ API para la capa de red (cualquier hilo)

    public void join(Conn c, String name) { queue.add(new Join(c, name)); }

    public void leave(Conn c) { queue.add(new Leave(c)); }

    public void input(Conn c, Protocol.Input in) { queue.add(new In(c, in)); }

    public void action(Conn c, Protocol.Action a) { queue.add(new Act(c, a)); }

    public void start() {
        running = true;
        new Thread(this::run, "game-loop").start();
    }

    public void stop() { running = false; }

    private void run() {
        final long step = 1_000_000_000L / Const.TICK_RATE;
        long next = System.nanoTime();
        while (running) {
            try {
                tick();
            } catch (RuntimeException e) {
                e.printStackTrace();   // un fallo en un tick no debe tumbar el servidor
            }
            next += step;
            long wait = next - System.nanoTime();
            if (wait > 0) LockSupport.parkNanos(wait);
            else if (wait < -5 * step) next = System.nanoTime();   // no acumular retraso indefinidamente
        }
    }

    // ------------------------------------------------------------ un tick

    void tick() {
        tick++;
        for (Cmd c; (c = queue.poll()) != null; ) handle(c);
        for (Entity e : entities) {
            if (e instanceof Player p) applyInputs(p);
            else if (e instanceof Bot b) thinkBot(b);
        }
        for (Entity e : entities) if (e instanceof Player p) sendState(p);
        for (Entity e : entities) e.stepped = false;
    }

    private void handle(Cmd cmd) {
        switch (cmd) {
            case Join j -> doJoin(j.conn(), j.name());
            case Leave l -> doLeave(l.conn());
            case In i -> doInput(i.conn(), i.input());
            case Act a -> doAction(a.conn(), a.action());
        }
    }

    private int allocId() {
        boolean used;
        do {
            nextId = nextId >= 30000 ? 1 : nextId + 1;
            used = false;
            for (Entity e : entities) if (e.id == nextId) { used = true; break; }
        } while (used);
        return nextId;
    }

    private void doJoin(Conn c, String name) {
        if (byConn.containsKey(c)) return;
        if (byConn.size() >= MAX_PLAYERS) { c.close(); return; }
        String n = name == null || name.isBlank() ? "Jugador" : name.strip();
        Player p = new Player(allocId(), c, n);
        float[] s = findSpawn(false);
        p.x = s[0];
        p.y = s[1];
        byConn.put(c, p);
        entities.add(p);

        Protocol.Welcome w = new Protocol.Welcome();
        w.id = p.id; w.seed = seed; w.worldW = world.width; w.worldH = world.height;
        w.fov = p.fov; w.range = p.range; w.x = p.x; w.y = p.y;
        c.send(Protocol.welcome(w));
        sendAllEdits(c);
    }

    private void doLeave(Conn c) {
        Player p = byConn.remove(c);
        if (p != null) entities.remove(p);
    }

    private void doInput(Conn c, Protocol.Input in) {
        Player p = byConn.get(c);
        if (p == null || in.seq <= p.lastSeq) return;   // repeticiones y desorden se ignoran
        p.lastSeq = in.seq;
        if (p.pending.size() >= Const.MAX_PENDING_INPUTS) p.pending.pollFirst();
        p.pending.addLast(in);
    }

    /** Anti-speedhack: cada input mueve exactamente velocidad * dt, y como mucho 2 por tick. */
    private void applyInputs(Player p) {
        int budget = p.pending.size() > 3 ? 2 : 1;   // se pone al día si hay cola, nunca más rápido
        while (budget-- > 0 && !p.pending.isEmpty()) {
            Protocol.Input in = p.pending.pollFirst();
            p.angle = Protocol.shortToAngle(in.angle);
            stride(p, Movement.move(world, p, in.mx, in.my, Const.PLAYER_SPEED, Const.TICK_DT));
            p.ackSeq = in.seq;
        }
    }

    private void thinkBot(Bot b) {
        b.timer -= Const.TICK_DT;
        if (b.timer <= 0) {
            b.heading = rng.nextFloat() * TWO_PI;
            b.timer = 1 + rng.nextFloat() * 2.5f;
        }
        float moved = Movement.move(world, b, (float) Math.cos(b.heading), (float) Math.sin(b.heading),
            Const.BOT_SPEED, Const.TICK_DT);
        if (moved < 1e-4f) b.timer = 0;   // chocó: elegir otra dirección
        b.angle = b.heading;
        stride(b, moved);
    }

    private static void stride(Entity e, float moved) {
        e.stride += moved;
        if (e.stride >= Const.STRIDE) {
            e.stride -= Const.STRIDE;
            e.stepped = true;
        }
    }

    private void doAction(Conn c, Protocol.Action a) {
        Player p = byConn.get(c);
        if (p == null || tick - p.lastActionTick < 4) return;                       // 200 ms entre acciones
        int tx = a.x, ty = a.y;
        if (tx < 1 || ty < 1 || tx >= world.width - 1 || ty >= world.height - 1) return;   // el borde no se toca
        float dx = tx + 0.5f - p.x, dy = ty + 0.5f - p.y, reach = Const.REACH + 0.5f;     // +0.5 por latencia
        if (dx * dx + dy * dy > reach * reach) return;
        byte cur = world.get(tx, ty, Const.LAYER_OBSTACLE), next;
        if (a.kind == Protocol.ACT_BREAK && Block.breakable(cur)) next = Block.AIR;
        else if (a.kind == Protocol.ACT_PLACE && cur == Block.AIR && !overlapsAnyone(tx, ty)) next = Block.WOOD;
        else return;
        p.lastActionTick = tick;
        setBlock(tx, ty, next);
    }

    private boolean overlapsAnyone(int tx, int ty) {
        float r = Const.BODY_RADIUS;
        for (Entity e : entities) {
            if (e.x > tx - r && e.x < tx + 1 + r && e.y > ty - r && e.y < ty + 1 + r) return true;
        }
        return false;
    }

    private void setBlock(int x, int y, byte b) {
        world.set(x, y, Const.LAYER_OBSTACLE, b);
        edits.put(y * world.width + x, b);
        byte[] msg = Protocol.blocks(1, new int[]{x}, new int[]{y}, new byte[]{b});
        for (Player q : byConn.values()) q.conn.send(msg);   // TODO: solo a quien tenga el chunk cargado
    }

    private void sendAllEdits(Conn c) {
        int n = edits.size();
        if (n == 0) return;
        int[] xs = new int[n], ys = new int[n];
        byte[] ids = new byte[n];
        int i = 0;
        for (Map.Entry<Integer, Byte> e : edits.entrySet()) {
            xs[i] = e.getKey() % world.width;
            ys[i] = e.getKey() / world.width;
            ids[i++] = e.getValue();
        }
        for (int off = 0; off < n; off += 2000) {
            int cnt = Math.min(2000, n - off);
            c.send(Protocol.blocks(cnt, Arrays.copyOfRange(xs, off, off + cnt),
                Arrays.copyOfRange(ys, off, off + cnt), Arrays.copyOfRange(ids, off, off + cnt)));
        }
    }

    /**
     * Anti-wallhack (TDD 3): el paquete de A solo contiene a quien A puede ver ahora mismo
     * (alcance, cono y línea de visión contra los vóxeles). Lo demás no sale del servidor.
     * Los pasos de los ocultos llegan como dirección en 16 sectores + volumen, nunca como posición.
     */
    private void sendState(Player p) {
        Protocol.State s = p.out;
        s.tick = tick; s.ackSeq = p.ackSeq; s.x = p.x; s.y = p.y;
        s.nEnt = 0;
        s.nSnd = 0;
        for (Entity e : entities) {
            if (e == p) continue;
            if (Vision.canSee(world, p.x, p.y, p.angle, p.fov, p.range, e.x, e.y)) {
                if (s.nEnt < Protocol.MAX_ENTITIES) {
                    int i = s.nEnt++;
                    s.entId[i] = e.id; s.entX[i] = e.x; s.entY[i] = e.y;
                    s.entAngle[i] = Protocol.angleToShort(e.angle);
                }
            } else if (e.stepped && s.nSnd < Protocol.MAX_SOUNDS) {
                float dx = e.x - p.x, dy = e.y - p.y, d = (float) Math.sqrt(dx * dx + dy * dy);
                if (d <= Const.HEARING_RANGE) {
                    float sector = TWO_PI / 16;
                    float a = Math.round((float) Math.atan2(dy, dx) / sector) * sector;
                    int i = s.nSnd++;
                    s.sndKind[i] = Protocol.SND_STEP;
                    s.sndAngle[i] = Protocol.angleToShort(a);
                    s.sndVol[i] = Math.round(255 * (1 - d / Const.HEARING_RANGE));
                }
            }
        }
        p.conn.send(Protocol.state(s));
    }

    /** Spawn en la periferia para jugadores; en cualquier sitio para bots. */
    private float[] findSpawn(boolean anywhere) {
        for (int i = 0; i < 500; i++) {
            float x, y;
            if (anywhere) {
                x = 2 + rng.nextFloat() * (world.width - 4);
                y = 2 + rng.nextFloat() * (world.height - 4);
            } else {
                int side = rng.nextInt(4);
                float band = 2 + rng.nextFloat() * 6;
                float along = 2 + rng.nextFloat() * ((side < 2 ? world.height : world.width) - 4);
                x = side == 0 ? band : side == 1 ? world.width - band : along;
                y = side == 2 ? band : side == 3 ? world.height - band : along;
            }
            if (world.free(x, y, Const.BODY_RADIUS + 0.2f)) return new float[]{x, y};
        }
        return new float[]{3.5f, 3.5f};
    }

    // ------------------------------------------------------------ para las pruebas

    Player playerOf(Conn c) { return byConn.get(c); }
}
