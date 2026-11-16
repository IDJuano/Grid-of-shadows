package com.gridofshadows.client;

/** Transporte del cliente. Cada plataforma aporta el suyo; los callbacks llegan en el hilo de render. */
public interface NetClient {
    interface Listener {
        void onOpen();

        void onMessage(byte[] data);

        void onClose();
    }

    void connect(String url, Listener listener);

    void send(byte[] data);

    boolean isOpen();

    void close();
}
