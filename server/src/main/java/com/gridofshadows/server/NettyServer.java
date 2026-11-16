package com.gridofshadows.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;

/** WebSocket (ws://host:puerto/ws). El TLS (wss://) lo termina Caddy delante de este proceso. */
final class NettyServer {
    private final String host;
    private final int port;
    private final GameLoop loop;

    NettyServer(String host, int port, GameLoop loop) {
        this.host = host;
        this.port = port;
        this.loop = loop;
    }

    void run() throws InterruptedException {
        EventLoopGroup boss = new NioEventLoopGroup(1);
        EventLoopGroup workers = new NioEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(boss, workers)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel ch) {
                        ch.pipeline().addLast(
                            new HttpServerCodec(),
                            new HttpObjectAggregator(65536),
                            new WebSocketServerProtocolHandler("/ws", null, false, 65536),
                            new WsHandler(loop));
                    }
                });
            Channel ch = b.bind(host, port).sync().channel();
            System.out.println("Grid of Shadows escuchando en ws://" + host + ":" + port + "/ws");
            ch.closeFuture().sync();
        } finally {
            boss.shutdownGracefully();
            workers.shutdownGracefully();
        }
    }
}
