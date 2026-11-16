package com.gridofshadows.desktop;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.gridofshadows.client.GridOfShadows;

/** Cliente de escritorio para desarrollar sin compilar a JS. Uso: lwjgl3:run --args="ws://localhost:8080/ws" */
public final class DesktopLauncher {
    private DesktopLauncher() {}

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : "ws://localhost:8080/ws";
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Grid of Shadows");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        new Lwjgl3Application(new GridOfShadows(new JavaWsClient(), url), config);
    }
}
