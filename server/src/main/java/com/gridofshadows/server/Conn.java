package com.gridofshadows.server;

/** Canal de salida hacia un cliente. La capa de red lo implementa; la simulación no conoce Netty. */
public interface Conn {
    void send(byte[] data);

    void close();
}
