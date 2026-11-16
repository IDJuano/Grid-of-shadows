package com.gridofshadows.web;

import com.gridofshadows.client.NetClient;
import org.teavm.jso.typedarrays.ArrayBuffer;
import org.teavm.jso.typedarrays.Int8Array;
import org.teavm.jso.websocket.WebSocket;

/** WebSocket del navegador. JS es monohilo, así que los callbacks ya llegan entre frames. */
final class WebSocketClient implements NetClient {
    private WebSocket ws;

    @Override
    public void connect(String url, Listener l) {
        ws = new WebSocket(url);
        ws.setBinaryType("arraybuffer");
        ws.onOpen(e -> l.onOpen());
        ws.onClose(e -> l.onClose());
        ws.onError(e -> l.onClose());
        ws.onMessage(e -> {
            ArrayBuffer buffer = e.getDataAsArray();
            l.onMessage(new Int8Array(buffer).copyToJavaArray());
        });
    }

    @Override
    public void send(byte[] data) {
        if (isOpen()) ws.send(Int8Array.copyFromJavaArray(data));
    }

    @Override
    public boolean isOpen() { return ws != null && ws.getReadyState() == 1; }

    @Override
    public void close() {
        if (ws != null) ws.close();
    }
}
