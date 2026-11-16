package com.gridofshadows.web;

import com.github.xpenatan.gdx.teavm.backends.web.WebApplication;
import com.github.xpenatan.gdx.teavm.backends.web.WebApplicationConfiguration;
import com.gridofshadows.client.GridOfShadows;
import org.teavm.jso.JSBody;

/** Punto de entrada del cliente web (TeaVM). */
public final class WebLauncher {
    /** Servidor de producción: el dominio que sirve Caddy con TLS. Cámbialo antes de publicar en itch.io. */
    private static final String PRODUCTION_SERVER = "wss://play.example.com/ws";
    private static final String LOCAL_SERVER = "ws://localhost:8080/ws";

    private WebLauncher() {}

    public static void main(String[] args) {
        blockContextMenu();                               // el clic derecho coloca bloques
        String url = queryParam("server");                // ?server=ws://... para probar contra otro servidor
        if (url.isEmpty()) url = isLocalhost() ? LOCAL_SERVER : PRODUCTION_SERVER;

        WebApplicationConfiguration config = new WebApplicationConfiguration();
        config.width = 0;                                 // 0 = ocupar toda la ventana
        config.height = 0;
        new WebApplication(new GridOfShadows(new WebSocketClient(), url), config);
    }

    @JSBody(script = "document.addEventListener('contextmenu', function (e) { e.preventDefault(); });")
    private static native void blockContextMenu();

    @JSBody(params = "name", script = "var v = new URLSearchParams(window.location.search).get(name); return v ? v : '';")
    private static native String queryParam(String name);

    @JSBody(script = "var h = window.location.hostname; return h === 'localhost' || h === '127.0.0.1';")
    private static native boolean isLocalhost();
}
