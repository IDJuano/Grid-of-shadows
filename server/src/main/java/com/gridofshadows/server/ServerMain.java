package com.gridofshadows.server;

import com.gridofshadows.common.Const;
import com.gridofshadows.common.VoxelWorld;
import com.gridofshadows.common.WorldGen;

/** Uso: server [--host=127.0.0.1] [--port=8080] [--bots=4] [--seed=1337] */
public final class ServerMain {
    private ServerMain() {}

    public static void main(String[] args) throws Exception {
        String host = "127.0.0.1";   // detrás de Caddy basta con localhost
        int port = 8080, bots = 4, seed = 1337;
        for (String a : args) {
            if (a.startsWith("--host=")) host = a.substring(7);
            else if (a.startsWith("--port=")) port = Integer.parseInt(a.substring(7));
            else if (a.startsWith("--bots=")) bots = Integer.parseInt(a.substring(7));
            else if (a.startsWith("--seed=")) seed = Integer.parseInt(a.substring(7));
            else throw new IllegalArgumentException("Argumento desconocido: " + a);
        }
        VoxelWorld world = WorldGen.generate(seed, Const.WORLD_W, Const.WORLD_H);
        GameLoop loop = new GameLoop(world, seed, bots);
        loop.start();
        new NettyServer(host, port, loop).run();
    }
}
