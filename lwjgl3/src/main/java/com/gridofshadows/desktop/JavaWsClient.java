package com.gridofshadows.desktop;

import com.badlogic.gdx.Gdx;
import com.gridofshadows.client.NetClient;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** WebSocket del JDK (java.net.http). Los callbacks se reenvían al hilo de render de libGDX. */
final class JavaWsClient implements NetClient {
    private volatile WebSocket ws;
    private CompletableFuture<?> tail = CompletableFuture.completedFuture(null);

    @Override
    public void connect(String url, Listener l) {
        HttpClient.newHttpClient().newWebSocketBuilder().buildAsync(URI.create(url), new WebSocket.Listener() {
            private final ByteArrayOutputStream partial = new ByteArrayOutputStream();

            @Override
            public void onOpen(WebSocket w) {
                ws = w;
                w.request(1);
                Gdx.app.postRunnable(l::onOpen);
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
                byte[] chunk = new byte[data.remaining()];
                data.get(chunk);
                partial.write(chunk, 0, chunk.length);
                if (last) {
                    byte[] full = partial.toByteArray();
                    partial.reset();
                    Gdx.app.postRunnable(() -> l.onMessage(full));
                }
                w.request(1);
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
                Gdx.app.postRunnable(l::onClose);
                return null;
            }

            @Override
            public void onError(WebSocket w, Throwable error) {
                Gdx.app.postRunnable(l::onClose);
            }
        }).exceptionally(t -> {
            Gdx.app.postRunnable(l::onClose);
            return null;
        });
    }

    /** El JDK no admite dos envíos simultáneos: se encadenan. */
    @Override
    public synchronized void send(byte[] data) {
        WebSocket w = ws;
        if (w == null) return;
        tail = tail.thenCompose(x -> w.sendBinary(ByteBuffer.wrap(data), true));
    }

    @Override
    public boolean isOpen() { return ws != null && !ws.isOutputClosed(); }

    @Override
    public void close() {
        WebSocket w = ws;
        if (w != null) w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
    }
}
