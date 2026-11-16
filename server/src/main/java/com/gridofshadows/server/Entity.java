package com.gridofshadows.server;

import com.gridofshadows.common.Body;

/** Todo lo que se mueve en el mundo y puede ser visto u oído: jugadores y bots. */
class Entity extends Body {
    final int id;
    float stride;
    boolean stepped;   // dio un paso audible en este tick
    boolean hitSound;  // golpeó algo en este tick (se oye más lejos que un paso)

    Entity(int id) { this.id = id; }
}
