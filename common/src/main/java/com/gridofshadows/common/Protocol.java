package com.gridofshadows.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Protocolo binario (big-endian) sobre WebSocket. Solo usa ByteBuffer, compatible con TeaVM.
 * El servidor nunca envía coordenadas de lo que el jugador no puede ver: los sonidos llegan
 * como dirección en 16 sectores y volumen, no como posición.
 */
public final class Protocol {
    private Protocol() {}

    public static final byte C_HELLO = 1, C_INPUT = 2, C_ACTION = 3;
    public static final byte S_WELCOME = (byte) 0x81, S_BLOCKS = (byte) 0x82, S_STATE = (byte) 0x83;
    public static final int ACT_BREAK = 1, ACT_PLACE = 2;
    public static final int SND_STEP = 1;
    public static final int MAX_NAME = 16, MAX_ENTITIES = 255, MAX_SOUNDS = 255;

    public static short angleToShort(float a) { return (short) Math.round(a * 10000f); }

    public static float shortToAngle(short s) { return s / 10000f; }

    // ---------------------------------------------------------------- cliente -> servidor

    public static final class Hello {
        public final String name;
        public Hello(String name) { this.name = name; }
    }

    public static final class Input {
        public final int seq, mx, my;
        public final short angle;
        public Input(int seq, int mx, int my, short angle) {
            this.seq = seq; this.mx = mx; this.my = my; this.angle = angle;
        }
    }

    public static final class Action {
        public final int kind, x, y;
        public Action(int kind, int x, int y) { this.kind = kind; this.x = x; this.y = y; }
    }

    public static byte[] hello(String name) {
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        int len = Math.min(n.length, MAX_NAME);
        ByteBuffer b = ByteBuffer.allocate(2 + len);
        b.put(C_HELLO).put((byte) len).put(n, 0, len);
        return b.array();
    }

    public static byte[] input(Input in) {
        ByteBuffer b = ByteBuffer.allocate(1 + 4 + 1 + 1 + 2);
        b.put(C_INPUT).putInt(in.seq).put((byte) in.mx).put((byte) in.my).putShort(in.angle);
        return b.array();
    }

    public static byte[] action(int kind, int x, int y) {
        ByteBuffer b = ByteBuffer.allocate(1 + 1 + 2 + 2);
        b.put(C_ACTION).put((byte) kind).putShort((short) x).putShort((short) y);
        return b.array();
    }

    private static int clamp1(int v) { return v > 1 ? 1 : (v < -1 ? -1 : v); }

    /** Devuelve Hello, Input o Action; null si el mensaje es inválido (nunca lanza). */
    public static Object decodeClient(byte[] data) {
        try {
            ByteBuffer b = ByteBuffer.wrap(data);
            switch (b.get()) {
                case C_HELLO: {
                    int len = b.get() & 0xFF;
                    if (len > MAX_NAME) return null;
                    byte[] n = new byte[len];
                    b.get(n);
                    return new Hello(new String(n, StandardCharsets.UTF_8));
                }
                case C_INPUT: {
                    int seq = b.getInt();
                    int mx = clamp1(b.get()), my = clamp1(b.get());
                    return new Input(seq, mx, my, b.getShort());
                }
                case C_ACTION: {
                    int kind = b.get(), x = b.getShort(), y = b.getShort();
                    return new Action(kind, x, y);
                }
                default:
                    return null;
            }
        } catch (RuntimeException e) {
            return null;
        }
    }

    // ---------------------------------------------------------------- servidor -> cliente

    public static final class Welcome {
        public int id, seed, worldW, worldH;
        public float fov, range, x, y;
    }

    public static final class Blocks {
        public int count;
        public int[] x, y;
        public byte[] id;
    }

    public static final class State {
        public int tick, ackSeq;
        public float x, y;
        public int nEnt, nSnd;
        public final int[] entId;
        public final float[] entX, entY;
        public final short[] entAngle;
        public final int[] sndKind, sndVol;
        public final short[] sndAngle;

        public State(int entCap, int sndCap) {
            entId = new int[entCap]; entX = new float[entCap]; entY = new float[entCap]; entAngle = new short[entCap];
            sndKind = new int[sndCap]; sndAngle = new short[sndCap]; sndVol = new int[sndCap];
        }
    }

    public static byte[] welcome(Welcome w) {
        ByteBuffer b = ByteBuffer.allocate(1 + 2 + 4 + 2 + 2 + 4 * 4);
        b.put(S_WELCOME).putShort((short) w.id).putInt(w.seed).putShort((short) w.worldW).putShort((short) w.worldH)
            .putFloat(w.fov).putFloat(w.range).putFloat(w.x).putFloat(w.y);
        return b.array();
    }

    public static byte[] blocks(int count, int[] x, int[] y, byte[] id) {
        ByteBuffer b = ByteBuffer.allocate(1 + 2 + count * 5);
        b.put(S_BLOCKS).putShort((short) count);
        for (int i = 0; i < count; i++) b.putShort((short) x[i]).putShort((short) y[i]).put(id[i]);
        return b.array();
    }

    public static byte[] state(State s) {
        ByteBuffer b = ByteBuffer.allocate(1 + 4 + 4 + 4 + 4 + 1 + s.nEnt * 12 + 1 + s.nSnd * 4);
        b.put(S_STATE).putInt(s.tick).putInt(s.ackSeq).putFloat(s.x).putFloat(s.y).put((byte) s.nEnt);
        for (int i = 0; i < s.nEnt; i++) {
            b.putShort((short) s.entId[i]).putFloat(s.entX[i]).putFloat(s.entY[i]).putShort(s.entAngle[i]);
        }
        b.put((byte) s.nSnd);
        for (int i = 0; i < s.nSnd; i++) b.put((byte) s.sndKind[i]).putShort(s.sndAngle[i]).put((byte) s.sndVol[i]);
        return b.array();
    }

    /** Devuelve Welcome, Blocks o State; null si el mensaje es inválido. */
    public static Object decodeServer(byte[] data) {
        try {
            ByteBuffer b = ByteBuffer.wrap(data);
            switch (b.get()) {
                case S_WELCOME: {
                    Welcome w = new Welcome();
                    w.id = b.getShort(); w.seed = b.getInt(); w.worldW = b.getShort(); w.worldH = b.getShort();
                    w.fov = b.getFloat(); w.range = b.getFloat(); w.x = b.getFloat(); w.y = b.getFloat();
                    return w;
                }
                case S_BLOCKS: {
                    int n = b.getShort() & 0xFFFF;
                    Blocks r = new Blocks();
                    r.count = n; r.x = new int[n]; r.y = new int[n]; r.id = new byte[n];
                    for (int i = 0; i < n; i++) { r.x[i] = b.getShort(); r.y[i] = b.getShort(); r.id[i] = b.get(); }
                    return r;
                }
                case S_STATE: {
                    int tick = b.getInt(), ack = b.getInt();
                    float x = b.getFloat(), y = b.getFloat();
                    int ne = b.get() & 0xFF;
                    int[] ids = new int[ne]; float[] ex = new float[ne], ey = new float[ne]; short[] ea = new short[ne];
                    for (int i = 0; i < ne; i++) { ids[i] = b.getShort(); ex[i] = b.getFloat(); ey[i] = b.getFloat(); ea[i] = b.getShort(); }
                    int ns = b.get() & 0xFF;
                    State s = new State(ne, ns);
                    s.tick = tick; s.ackSeq = ack; s.x = x; s.y = y; s.nEnt = ne; s.nSnd = ns;
                    for (int i = 0; i < ne; i++) { s.entId[i] = ids[i]; s.entX[i] = ex[i]; s.entY[i] = ey[i]; s.entAngle[i] = ea[i]; }
                    for (int i = 0; i < ns; i++) { s.sndKind[i] = b.get(); s.sndAngle[i] = b.getShort(); s.sndVol[i] = b.get() & 0xFF; }
                    return s;
                }
                default:
                    return null;
            }
        } catch (RuntimeException e) {
            return null;
        }
    }
}
