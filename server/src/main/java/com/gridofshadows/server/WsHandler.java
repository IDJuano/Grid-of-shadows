package com.gridofshadows.server;

import com.gridofshadows.common.Protocol;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;

/** Una instancia por conexión: traduce frames binarios a comandos de la simulación. */
final class WsHandler extends SimpleChannelInboundHandler<WebSocketFrame> {
    private static final int MAX_MSGS_PER_SECOND = 100;   // el cliente legítimo envía ~20

    private final GameLoop loop;
    private Conn conn;
    private long windowStart = System.nanoTime();
    private int msgs;

    WsHandler(GameLoop loop) { this.loop = loop; }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) {
        Channel ch = ctx.channel();
        conn = new Conn() {
            @Override
            public void send(byte[] data) {
                if (!ch.isActive()) return;
                // Cliente lento: los estados se descartan (el siguiente los reemplaza), los bloques no.
                if (!ch.isWritable() && data[0] == Protocol.S_STATE) return;
                ch.writeAndFlush(new BinaryWebSocketFrame(Unpooled.wrappedBuffer(data)));
            }

            @Override
            public void close() { ch.close(); }
        };
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, WebSocketFrame frame) {
        if (!(frame instanceof BinaryWebSocketFrame)) return;   // ping, pong y close los gestiona Netty
        if (overLimit()) { ctx.close(); return; }
        ByteBuf buf = frame.content();
        byte[] data = new byte[buf.readableBytes()];
        buf.readBytes(data);
        Object msg = Protocol.decodeClient(data);
        if (msg instanceof Protocol.Hello h) loop.join(conn, h.name);
        else if (msg instanceof Protocol.Input i) loop.input(conn, i);
        else if (msg instanceof Protocol.Action a) loop.action(conn, a);
    }

    private boolean overLimit() {
        long now = System.nanoTime();
        if (now - windowStart > 1_000_000_000L) { windowStart = now; msgs = 0; }
        return ++msgs > MAX_MSGS_PER_SECOND;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        loop.leave(conn);
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        ctx.close();
    }
}
