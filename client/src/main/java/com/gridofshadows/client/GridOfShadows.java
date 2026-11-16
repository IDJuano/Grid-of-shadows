package com.gridofshadows.client;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.gridofshadows.common.Block;
import com.gridofshadows.common.Const;
import com.gridofshadows.common.Inventory;
import com.gridofshadows.common.Predictor;
import com.gridofshadows.common.Protocol;
import com.gridofshadows.common.Vision;
import com.gridofshadows.common.VoxelWorld;
import com.gridofshadows.common.WorldGen;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Cliente. Vista cenital 2D: el mundo se reconstruye desde la semilla, el movimiento se predice
 * localmente a 20 Hz y se corrige con el estado del servidor (reconciliación). Solo se dibuja lo que
 * el servidor envía: no existe en memoria nada de los jugadores que no ves.
 */
public final class GridOfShadows extends ApplicationAdapter {
    private enum Phase { CONNECTING, PLAYING, CLOSED }

    private static final Color FOG = new Color(0.047f, 0.063f, 0.082f, 1f);
    private static final Color GROUND_A = new Color(0x1d2a33ff), GROUND_B = new Color(0x223039ff);
    private static final Color LANTERN = new Color(0xf2d48aff), ENEMY = new Color(0xd4574eff), HIT_SOUND = new Color(0xe8793aff);
    private static final Color[] TILE = new Color[8];

    static {
        TILE[Block.STONE] = new Color(0x5a6475ff);
        TILE[Block.TREE] = new Color(0x2f6b4fff);
        TILE[Block.ROCK] = new Color(0x8a93a3ff);
        TILE[Block.BEDROCK] = new Color(0x12161cff);
        TILE[Block.WOOD] = new Color(0xa8743dff);
    }

    private static final class Remote {
        float fx, fy, tx, ty, rx, ry, angle, t;   // desde, hasta y posición dibujada
        int stamp;
    }

    private static final class Ping {
        final float angle, vol, born;
        final int kind;

        Ping(float angle, float vol, float born, int kind) { this.angle = angle; this.vol = vol; this.born = born; this.kind = kind; }
    }

    private final NetClient net;
    private final String url;
    private final NetClient.Listener listener = new NetClient.Listener() {
        @Override public void onOpen() { net.send(Protocol.hello("Jugador")); }

        @Override public void onClose() { phase = Phase.CLOSED; }

        @Override public void onMessage(byte[] data) { handle(Protocol.decodeServer(data)); }
    };

    private ShapeRenderer sr;
    private OrthographicCamera cam, hudCam;
    private ExtendViewport view;
    private final Vector2 tmp = new Vector2();
    private int screenH = 1;

    private Phase phase = Phase.CONNECTING;
    private VoxelWorld world;
    private float fov = Const.DEFAULT_FOV, range = Const.DEFAULT_RANGE;

    private Predictor pred;                                // predicción local y reconciliación
    private float renderX, renderY, aim, acc, time;
    private int stamp, hoverX, hoverY;
    private boolean hoverInReach;
    private float swingUntil;
    private Inventory inv = new Inventory();
    private final Map<Integer, Remote> remotes = new HashMap<>();
    private final ArrayList<Ping> pings = new ArrayList<>();
    private float[] dist = new float[0];
    private int[] hit = new int[0];
    private final Color cIn = new Color(FOG.r, FOG.g, FOG.b, 0f);
    private final Color cOut0 = new Color(FOG.r, FOG.g, FOG.b, 0f), cOut1 = new Color(FOG.r, FOG.g, FOG.b, 0f);

    public GridOfShadows(NetClient net, String url) {
        this.net = net;
        this.url = url;
    }

    @Override
    public void create() {
        sr = new ShapeRenderer();
        cam = new OrthographicCamera();
        hudCam = new OrthographicCamera();
        view = new ExtendViewport(40f, 22.5f, cam);
        net.connect(url, listener);
    }

    @Override
    public void resize(int width, int height) {
        view.update(width, height, false);
        screenH = height;
        hudCam.setToOrtho(false, width, height);
    }

    @Override
    public void dispose() {
        sr.dispose();
        net.close();
    }

    // ---------------------------------------------------------------- red

    private void handle(Object msg) {
        if (msg instanceof Protocol.Welcome w) {
            world = WorldGen.generate(w.seed, w.worldW, w.worldH);
            fov = w.fov;
            range = w.range;
            pred = new Predictor(world, w.x, w.y);
            renderX = w.x;
            renderY = w.y;
            inv = new Inventory();
            phase = Phase.PLAYING;
        } else if (msg instanceof Protocol.Blocks b && world != null) {
            for (int i = 0; i < b.count; i++) world.set(b.x[i], b.y[i], Const.LAYER_OBSTACLE, b.id[i]);
        } else if (msg instanceof Protocol.State s && world != null) {
            onState(s);
        } else if (msg instanceof Inventory mine && pred != null) {
            inv = mine;                  // el servidor es la autoridad sobre lo que llevas
            pred.setLoad(mine.load());   // y la carga te frena igual en la predicción
        }
    }

    private void onState(Protocol.State s) {
        // Reconciliación: se parte de la posición autoritativa y se re-aplican los inputs sin confirmar
        pred.reconcile(s.x, s.y, s.ackSeq);

        stamp++;
        for (int i = 0; i < s.nEnt; i++) {
            Integer id = s.entId[i];
            Remote r = remotes.get(id);
            if (r == null) {
                r = new Remote();
                r.rx = r.fx = s.entX[i];
                r.ry = r.fy = s.entY[i];
                remotes.put(id, r);
            } else {
                r.fx = r.rx;
                r.fy = r.ry;
            }
            r.tx = s.entX[i];
            r.ty = s.entY[i];
            r.angle = Protocol.shortToAngle(s.entAngle[i]);
            r.t = 0;
            r.stamp = stamp;
        }
        // Lo que no viene en el paquete sale de tu vista y desaparece
        for (Iterator<Remote> it = remotes.values().iterator(); it.hasNext(); ) {
            if (it.next().stamp != stamp) it.remove();
        }
        for (int i = 0; i < s.nSnd; i++) {
            pings.add(new Ping(Protocol.shortToAngle(s.sndAngle[i]), s.sndVol[i] / 255f, time, s.sndKind[i]));
        }
    }

    // ---------------------------------------------------------------- simulación local

    @Override
    public void render() {
        float dt = Math.min(Gdx.graphics.getDeltaTime(), 0.1f);
        time += dt;
        if (phase == Phase.PLAYING) update(dt);
        draw();
    }

    private void update(float dt) {
        // Apuntado: ratón en coordenadas de mundo (con la cámara del frame anterior)
        view.unproject(tmp.set(Gdx.input.getX(), Gdx.input.getY()));
        float mouseX = tmp.x, mouseY = tmp.y;
        aim = (float) Math.atan2(mouseY - renderY, mouseX - renderX);

        // Pasos fijos a 20 Hz: predicción local y envío del input
        acc += dt;
        while (acc >= Const.TICK_DT) {
            acc -= Const.TICK_DT;
            fixedStep();
        }
        float f = acc / Const.TICK_DT;
        renderX = pred.prevX + (pred.me.x - pred.prevX) * f;
        renderY = pred.prevY + (pred.me.y - pred.prevY) * f;

        // Casilla bajo el cursor y si está al alcance (el servidor lo valida de nuevo)
        hoverX = (int) Math.floor(mouseX);
        hoverY = (int) Math.floor(mouseY);
        float hx = hoverX + 0.5f - renderX, hy = hoverY + 0.5f - renderY;
        hoverInReach = hx * hx + hy * hy <= Const.REACH * Const.REACH;

        // Clic izquierdo golpea (árboles y rocas dan recursos), clic derecho construye con madera
        boolean left = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        boolean right = Gdx.input.isButtonJustPressed(Input.Buttons.RIGHT);
        if (left) swingUntil = time + 0.15f;
        if ((left || right) && hoverInReach) {
            net.send(Protocol.action(left ? Protocol.ACT_HIT : Protocol.ACT_PLACE, hoverX, hoverY));
        }

        for (Remote r : remotes.values()) {
            r.t += dt;
            float k = Math.min(1f, r.t / Const.TICK_DT);
            r.rx = r.fx + (r.tx - r.fx) * k;
            r.ry = r.fy + (r.ty - r.fy) * k;
        }
        for (Iterator<Ping> it = pings.iterator(); it.hasNext(); ) {
            if (time - it.next().born > 0.9f) it.remove();
        }
    }

    private void fixedStep() {
        int mx = (Gdx.input.isKeyPressed(Input.Keys.D) ? 1 : 0) - (Gdx.input.isKeyPressed(Input.Keys.A) ? 1 : 0);
        int my = (Gdx.input.isKeyPressed(Input.Keys.W) ? 1 : 0) - (Gdx.input.isKeyPressed(Input.Keys.S) ? 1 : 0);
        net.send(Protocol.input(pred.step(mx, my, aim)));
    }

    // ---------------------------------------------------------------- dibujo

    private void draw() {
        Gdx.gl.glClearColor(FOG.r, FOG.g, FOG.b, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        if (phase != Phase.PLAYING || world == null) {
            drawStatus();
            return;
        }
        view.apply();
        cam.position.set(renderX, renderY, 0f);
        cam.update();
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);

        int n = Math.max(24, (int) Math.ceil(Math.toDegrees(fov) * 1.5));
        if (dist.length < n + 1) {
            dist = new float[n + 1];
            hit = new int[n + 1];
        }
        Vision.fan(world, renderX, renderY, aim, fov, range, n, dist, hit);

        drawTiles();
        drawRemotes();
        drawFalloff(n);
        drawFog(n);
        drawWalls(n);
        drawCursor();
        drawSelf();
        drawSwing();
        drawPings();
        sr.end();

        sr.setProjectionMatrix(hudCam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        Hud.draw(sr, inv, screenH);
        sr.end();
    }

    private void drawStatus() {
        view.apply();
        cam.position.set(0f, 0f, 0f);
        cam.update();
        sr.setProjectionMatrix(cam.combined);
        sr.begin(ShapeRenderer.ShapeType.Filled);
        if (phase == Phase.CONNECTING) {
            float p = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(time * 4f));   // latido ámbar: conectando
            sr.setColor(LANTERN.r * p, LANTERN.g * p, LANTERN.b * p, 1f);
        } else {
            sr.setColor(ENEMY);                                                       // rojo: sin conexión
        }
        sr.rect(-0.5f, -0.5f, 1f, 1f);
        sr.end();
    }

    private void tile(int x, int y, byte b, float light) {
        Color c = TILE[b];
        sr.setColor(c.r * 0.6f * light, c.g * 0.6f * light, c.b * 0.6f * light, 1f);
        sr.rect(x, y, 1f, 1f);
        sr.setColor(c.r * light, c.g * light, c.b * light, 1f);
        sr.rect(x + 0.07f, y + 0.07f, 0.86f, 0.86f);
    }

    private void drawTiles() {
        int x0 = Math.max(0, (int) Math.floor(renderX - range)), x1 = Math.min(world.width - 1, (int) Math.ceil(renderX + range));
        int y0 = Math.max(0, (int) Math.floor(renderY - range)), y1 = Math.min(world.height - 1, (int) Math.ceil(renderY + range));
        for (int y = y0; y <= y1; y++) {
            for (int x = x0; x <= x1; x++) {
                byte b = world.get(x, y, Const.LAYER_OBSTACLE);
                if (b == Block.AIR) {
                    sr.setColor(((x + y) & 1) == 0 ? GROUND_A : GROUND_B);
                    sr.rect(x, y, 1f, 1f);
                } else {
                    tile(x, y, b, 1f);
                }
            }
        }
    }

    private void drawRemotes() {
        for (Remote r : remotes.values()) {
            sr.setColor(ENEMY);
            sr.circle(r.rx, r.ry, 0.32f, 12);
            sr.setColor(FOG);
            sr.rectLine(r.rx, r.ry, r.rx + (float) Math.cos(r.angle) * 0.5f, r.ry + (float) Math.sin(r.angle) * 0.5f, 0.08f);
        }
    }

    private float shade(float d) {
        float t = (d / range - 0.35f) / 0.65f;
        return 0.85f * Math.max(0f, Math.min(1f, t));
    }

    /** Oscurece el cono según la distancia, hasta el color de la niebla en el borde del alcance. */
    private void drawFalloff(int n) {
        float ox = renderX, oy = renderY;
        for (int i = 0; i < n; i++) {
            float a0 = aim - fov * 0.5f + fov * i / n, a1 = aim - fov * 0.5f + fov * (i + 1) / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float d0 = dist[i], d1 = dist[i + 1];
            cOut0.a = shade(d0);
            cOut1.a = shade(d1);
            float ix0 = ox + c0 * d0 * 0.35f, iy0 = oy + s0 * d0 * 0.35f, ix1 = ox + c1 * d1 * 0.35f, iy1 = oy + s1 * d1 * 0.35f;
            float px0 = ox + c0 * d0, py0 = oy + s0 * d0, px1 = ox + c1 * d1, py1 = oy + s1 * d1;
            sr.triangle(ix0, iy0, px0, py0, px1, py1, cIn, cOut0, cOut1);
            sr.triangle(ix0, iy0, px1, py1, ix1, iy1, cIn, cOut1, cIn);
        }
    }

    /**
     * Niebla de guerra sin stencil ni framebuffers: el complemento del polígono de visión (que es
     * una estrella desde el jugador) se cubre con cuadriláteros desde cada punto de impacto hacia fuera
     * y con un abanico para lo que queda fuera del cono.
     */
    private void drawFog(int n) {
        float ox = renderX, oy = renderY, far = range * 1.5f + 3f;
        sr.setColor(FOG);
        for (int i = 0; i < n; i++) {
            float a0 = aim - fov * 0.5f + fov * i / n, a1 = aim - fov * 0.5f + fov * (i + 1) / n;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float hx0 = ox + c0 * dist[i], hy0 = oy + s0 * dist[i], hx1 = ox + c1 * dist[i + 1], hy1 = oy + s1 * dist[i + 1];
            float fx0 = ox + c0 * far, fy0 = oy + s0 * far, fx1 = ox + c1 * far, fy1 = oy + s1 * far;
            sr.triangle(hx0, hy0, fx0, fy0, fx1, fy1);
            sr.triangle(hx0, hy0, fx1, fy1, hx1, hy1);
        }
        float start = aim + fov * 0.5f, span = (float) (2 * Math.PI) - fov;
        int segs = 24;
        for (int i = 0; i < segs; i++) {
            float a = start + span * i / segs, b = start + span * (i + 1) / segs;
            sr.triangle(ox, oy, ox + (float) Math.cos(a) * far, oy + (float) Math.sin(a) * far,
                ox + (float) Math.cos(b) * far, oy + (float) Math.sin(b) * far);
        }
    }

    /** Las caras de los bloques que tocan los rayos se ven sólidas aunque el polígono acabe en su borde. */
    private void drawWalls(int n) {
        int last = -1;
        for (int i = 0; i <= n; i++) {
            int h = hit[i];
            if (h < 0 || h == last) continue;
            last = h;
            int x = h % world.width, y = h / world.width;
            byte b = world.get(x, y, Const.LAYER_OBSTACLE);
            if (b == Block.AIR) continue;
            float dx = x + 0.5f - renderX, dy = y + 0.5f - renderY;
            tile(x, y, b, Math.max(0.25f, 1f - (float) Math.sqrt(dx * dx + dy * dy) / range));
        }
    }

    private void drawSelf() {
        sr.setColor(LANTERN);
        sr.circle(renderX, renderY, 0.32f, 16);
        sr.setColor(FOG);
        sr.rectLine(renderX, renderY, renderX + (float) Math.cos(aim) * 0.55f, renderY + (float) Math.sin(aim) * 0.55f, 0.1f);
    }

    private void drawCursor() {
        if (!hoverInReach) return;
        float t = 0.06f;
        sr.setColor(1f, 1f, 1f, 0.55f);
        sr.rect(hoverX, hoverY, 1f, t);
        sr.rect(hoverX, hoverY + 1f - t, 1f, t);
        sr.rect(hoverX, hoverY + t, t, 1f - 2 * t);
        sr.rect(hoverX + 1f - t, hoverY + t, t, 1f - 2 * t);
    }

    /** Destello corto delante del jugador al golpear. */
    private void drawSwing() {
        if (time >= swingUntil) return;
        sr.setColor(1f, 1f, 1f, 0.5f * (swingUntil - time) / 0.15f);
        arc(0.95f, 0.05f, aim - 0.5f, aim + 0.5f, 8);
    }

    /** Sonidos fuera de la vista: arcos alrededor del jugador hacia donde suenan. Ámbar = pasos, naranja = golpes. */
    private void drawPings() {
        for (Ping p : pings) {
            float age = time - p.born, alpha = (1f - age / 0.9f) * (0.3f + 0.7f * p.vol);
            if (alpha <= 0f) continue;
            boolean hitKind = p.kind == Protocol.SND_HIT;
            Color c = hitKind ? HIT_SOUND : LANTERN;
            sr.setColor(c.r, c.g, c.b, alpha);
            float spread = hitKind ? 0.42f : 0.28f;
            arc(1.6f + age * 0.5f, 0.08f + 0.12f * p.vol, p.angle - spread, p.angle + spread, 6);
        }
    }

    /** Arco relleno centrado en el jugador, de radio r y semi-grosor w, entre los ángulos a0 y a1. */
    private void arc(float r, float w, float a0, float a1, int seg) {
        float ri = r - w, ro = r + w;
        for (int i = 0; i < seg; i++) {
            float s = a0 + (a1 - a0) * i / seg, e = a0 + (a1 - a0) * (i + 1) / seg;
            float cs = (float) Math.cos(s), ss = (float) Math.sin(s), ce = (float) Math.cos(e), se = (float) Math.sin(e);
            sr.triangle(renderX + cs * ri, renderY + ss * ri, renderX + cs * ro, renderY + ss * ro, renderX + ce * ro, renderY + se * ro);
            sr.triangle(renderX + cs * ri, renderY + ss * ri, renderX + ce * ro, renderY + se * ro, renderX + ce * ri, renderY + se * ri);
        }
    }
}
