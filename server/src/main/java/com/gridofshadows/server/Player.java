package com.gridofshadows.server;

import com.gridofshadows.common.Const;
import com.gridofshadows.common.Protocol;

import java.util.ArrayDeque;

final class Player extends Entity {
    final Conn conn;
    final String name;
    final ArrayDeque<Protocol.Input> pending = new ArrayDeque<>();
    final Protocol.State out = new Protocol.State(Protocol.MAX_ENTITIES, Protocol.MAX_SOUNDS); // se reutiliza cada tick
    int ackSeq = -1, lastSeq = -1, lastActionTick = -1000;
    float fov = Const.DEFAULT_FOV, range = Const.DEFAULT_RANGE;

    Player(int id, Conn conn, String name) {
        super(id);
        this.conn = conn;
        this.name = name;
    }
}
