package com.gridofshadows.common;

/** Pruebas del núcleo compartido, sin dependencias: ./gradlew :common:selfTest */
public final class SelfTest {
    private static int checks, failures;

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) { failures++; System.out.println("FALLO: " + what); }
    }

    private static boolean near(float a, float b, float eps) { return Math.abs(a - b) <= eps; }

    private static int hash(VoxelWorld w) {
        int h = 17;
        for (int y = 0; y < w.height; y++) for (int x = 0; x < w.width; x++) h = h * 31 + w.get(x, y, Const.LAYER_OBSTACLE);
        return h;
    }

    public static void main(String[] args) {
        worldGen();
        dda();
        visibility();
        movement();
        protocol();
        System.out.println(checks + " comprobaciones, " + failures + " fallos");
        if (failures > 0) System.exit(1);
    }

    static void worldGen() {
        VoxelWorld a = WorldGen.generate(1337, 128, 96), b = WorldGen.generate(1337, 128, 96), c = WorldGen.generate(42, 128, 96);
        check(hash(a) == hash(b), "misma semilla => mismo mundo");
        check(hash(a) != hash(c), "otra semilla => otro mundo");
        check(a.solid(0, 0) && a.solid(127, 95) && a.solid(-5, 3), "borde y exterior son sólidos");
    }

    static void dda() {
        VoxelWorld w = new VoxelWorld(32, 32);
        for (int y = 0; y < 32; y++) w.set(10, y, Const.LAYER_OBSTACLE, Block.STONE);
        int[] hit = new int[1];
        float d = Vision.cast(w, 5.5f, 5.5f, 0f, 20f, hit);
        check(near(d, 4.5f, 1e-3f) && hit[0] == 5 * 32 + 10, "DDA hacia +x");
        d = Vision.cast(w, 5.5f, 5.5f, 0f, 3f, hit);
        check(near(d, 3f, 1e-6f) && hit[0] == -1, "sin impacto dentro del alcance");
        d = Vision.cast(w, 5.5f, 5.5f, (float) Math.PI, 20f, hit);
        check(near(d, 5.5f, 1e-3f), "DDA hacia -x llega al borde del mundo");
        d = Vision.cast(w, 5.5f, 5.5f, (float) (Math.PI / 4), 20f, hit);
        check(near(d, 4.5f / (float) Math.cos(Math.PI / 4), 1e-2f), "DDA en diagonal");
        d = Vision.cast(w, 5.5f, 5.5f, (float) (Math.PI / 2), 40f, hit);
        check(near(d, 26.5f, 1e-2f), "DDA hacia +y");
    }

    static void visibility() {
        VoxelWorld w = new VoxelWorld(32, 32);
        float fov = Const.DEFAULT_FOV, range = Const.DEFAULT_RANGE;
        check(Vision.canSee(w, 5.5f, 5.5f, 0f, fov, range, 9.5f, 5.5f), "visible en campo abierto");
        check(!Vision.canSee(w, 5.5f, 5.5f, 0f, fov, range, 1.5f, 5.5f), "a la espalda: fuera del cono");
        check(!Vision.canSee(w, 5.5f, 5.5f, 0f, fov, range, 20.5f, 5.5f), "fuera de alcance");
        for (int y = 3; y <= 8; y++) w.set(7, y, Const.LAYER_OBSTACLE, Block.STONE);
        check(!Vision.canSee(w, 5.5f, 5.5f, 0f, fov, range, 9.5f, 5.5f), "oculto tras un muro");
        w.set(7, 5, Const.LAYER_OBSTACLE, Block.AIR);
        check(Vision.canSee(w, 5.5f, 5.5f, 0f, fov, range, 9.5f, 5.5f), "visible al abrir un hueco");
    }

    static void movement() {
        VoxelWorld w = new VoxelWorld(32, 32);
        for (int y = 0; y < 32; y++) w.set(10, y, Const.LAYER_OBSTACLE, Block.STONE);
        Body b = new Body();
        b.x = 8.5f; b.y = 5.5f;
        for (int i = 0; i < 100; i++) Movement.move(w, b, 1, 0, Const.PLAYER_SPEED, Const.TICK_DT);
        check(b.x >= 9.4f && b.x < 9.7f, "el muro detiene el avance");
        float y0 = b.y;
        for (int i = 0; i < 10; i++) Movement.move(w, b, 1, 1, Const.PLAYER_SPEED, Const.TICK_DT);
        check(b.y > y0 + 1f, "desliza a lo largo del muro");
        Body c = new Body();
        c.x = 3.5f; c.y = 20.5f;
        float moved = Movement.move(w, c, 5, 5, Const.PLAYER_SPEED, Const.TICK_DT);
        check(near(moved, Const.PLAYER_SPEED * Const.TICK_DT, 1e-4f), "la velocidad no depende de la magnitud del input");
    }

    static void protocol() {
        Object o = Protocol.decodeClient(Protocol.input(new Protocol.Input(77, 1, -1, Protocol.angleToShort(-3.1f))));
        check(o instanceof Protocol.Input i && i.seq == 77 && i.mx == 1 && i.my == -1
            && near(Protocol.shortToAngle(i.angle), -3.1f, 1e-3f), "Input ida y vuelta");
        o = Protocol.decodeClient(Protocol.input(new Protocol.Input(1, 100, -100, (short) 0)));
        check(o instanceof Protocol.Input j && j.mx == 1 && j.my == -1, "el servidor acota el input a -1..1");
        o = Protocol.decodeClient(Protocol.hello("Ñandú, el sigiloso y muy largo"));
        check(o instanceof Protocol.Hello h && h.name.length() > 0 && h.name.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= Protocol.MAX_NAME, "Hello truncado");
        o = Protocol.decodeClient(Protocol.action(Protocol.ACT_PLACE, 12, 99));
        check(o instanceof Protocol.Action a && a.kind == Protocol.ACT_PLACE && a.x == 12 && a.y == 99, "Action");
        check(Protocol.decodeClient(new byte[0]) == null && Protocol.decodeClient(new byte[]{2, 0}) == null
            && Protocol.decodeClient(new byte[]{99}) == null, "mensajes inválidos se descartan");

        Protocol.Welcome w = new Protocol.Welcome();
        w.id = 5; w.seed = -123456; w.worldW = 128; w.worldH = 96; w.fov = 2.4f; w.range = 12f; w.x = 3.5f; w.y = 40.25f;
        o = Protocol.decodeServer(Protocol.welcome(w));
        check(o instanceof Protocol.Welcome r && r.id == 5 && r.seed == -123456 && r.worldW == 128 && r.y == 40.25f, "Welcome");

        o = Protocol.decodeServer(Protocol.blocks(2, new int[]{3, 100}, new int[]{4, 90}, new byte[]{Block.AIR, Block.WOOD}));
        check(o instanceof Protocol.Blocks bl && bl.count == 2 && bl.x[1] == 100 && bl.y[1] == 90 && bl.id[1] == Block.WOOD, "Blocks");

        Protocol.State s = new Protocol.State(255, 255);
        s.tick = 9; s.ackSeq = 41; s.x = 1.5f; s.y = 2.5f; s.nEnt = 1; s.nSnd = 1;
        s.entId[0] = 7; s.entX[0] = 8.5f; s.entY[0] = 9.5f; s.entAngle[0] = Protocol.angleToShort(1f);
        s.sndKind[0] = Protocol.SND_STEP; s.sndAngle[0] = Protocol.angleToShort(-0.5f); s.sndVol[0] = 200;
        o = Protocol.decodeServer(Protocol.state(s));
        check(o instanceof Protocol.State t && t.tick == 9 && t.ackSeq == 41 && t.nEnt == 1 && t.entId[0] == 7
            && t.entX[0] == 8.5f && t.nSnd == 1 && t.sndVol[0] == 200, "State");
    }
}
