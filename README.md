# Grid of Shadows

Supervivencia PvP con visión limitada, mundo de vóxeles destructible y servidor autoritativo en Java.
Esto es el corte vertical jugable de las **Fases 1 y 2** del TDD más el **primer corte de la Fase 3**: dos o más
jugadores (y bots) en el mismo mundo, con cono de visión, niebla, oclusión por vóxeles filtrada **en el servidor**,
sonido de pasos y golpes direccional, recolección de recursos, inventario y construcción con madera.

## Arranque rápido

Requisitos: JDK 21. El wrapper de Gradle (9.6.1) va incluido; la primera vez descarga dependencias.

```bash
./gradlew :server:run --args="--bots=4"      # servidor en ws://127.0.0.1:8080/ws
./gradlew :lwjgl3:run                        # cliente de escritorio (arranca otro para ver PvP)
./gradlew :teavm:gdx_teavm_web_js_run        # cliente en el navegador: http://localhost:8000
./gradlew check                              # pruebas del núcleo y de la simulación (sin JUnit)
```

Controles: **WASD** mover, **ratón** apuntar, **clic** golpear (árboles y rocas dan recursos), **clic derecho** construir con madera.
Un cuadrado ámbar que late es "conectando"; uno rojo, "sin conexión".

## Módulos

| Módulo | Qué contiene | Target |
|---|---|---|
| `common` | Mundo en chunks 32x32x16, generación determinista, DDA/visión, movimiento, protocolo binario, `Predictor` | Java 17, sin dependencias (se compila a JS) |
| `server` | `GameLoop` (20 Hz, autoritativa) + capa Netty/WebSocket | Java 21 |
| `client` | Cliente libGDX: render, niebla, input, predicción | Java 17 |
| `lwjgl3` | Lanzador de escritorio para iterar sin compilar a JS | Java 17 |
| `teavm` | Lanzador web (gdx-teavm 1.6.1, libGDX 1.14.2, TeaVM 0.15.0) | JS |

## Cómo se cumple el TDD

- **Anti-wallhack (TDD 3):** en cada tick, `GameLoop.sendState` incluye en el paquete de A solo a quien A puede ver
  (alcance, cono y raycast DDA contra los vóxeles). El resto no sale del servidor. Los pasos de los ocultos viajan como
  *sector de 22,5° + volumen*, nunca como posición.
- **Cola de comandos (TDD 5.A):** los hilos de red solo encolan en una `ConcurrentLinkedQueue`; el hilo del bucle es el único
  que toca el estado.
- **Anti-speedhack:** cada input mueve exactamente `velocidad * dt` y como mucho 2 por tick; seq repetidos se ignoran.
- **Predicción y reconciliación (TDD 2):** `Predictor` mueve al instante con el mismo código que el servidor y se corrige con
  `ackSeq`. Probado contra el servidor real con latencia y con discrepancias.
- **Mundo:** `byte[32*32*16]` por chunk; la capa z=1 es la que bloquea paso y vista.

## Fase 3, corte 1: recolección e inventario

| Bloque | Golpes a mano | Cada golpe da |
|---|---|---|
| Árbol | 4 | 6 de madera |
| Roca | 5 | 5 de piedra |
| Madera construida | 6 | nada |
| Piedra y lecho de roca | no se rompen a mano (Tier 1) | |

- El inventario (24 ranuras, pilas de hasta 500) es **del servidor**: solo se envía a su dueño y el cliente solo lo dibuja.
  Si el botín no cabe, el golpe se rechaza sin gastar el recurso.
- Construir con madera cuesta 5 y solo se cobra si el bloque llega a colocarse.
- La carga frena hasta un 40 % con 400 unidades. El servidor y la predicción del cliente usan la misma fórmula.
- Los golpes se oyen hasta 16 casillas, fuera de la vista y a través de las paredes: arcos naranjas (los pasos son ámbar).
  Llegan como sector y volumen, nunca como posición.
- El HUD (inventario y barra de carga) se dibuja con formas y dígitos de 3 x 5, sin fuentes ni assets, para no arriesgar
  el arranque en el navegador. La barra se pone roja al acercarse a la carga máxima.
- Un cuadro blanco resalta la casilla bajo el cursor cuando está al alcance (3,5 casillas).

## Decisiones y desviaciones del TDD

1. **Vista cenital 2D** (el TDD no fija cámara). El cono y la niebla se dibujan sin stencil ni framebuffers.
2. **El cliente regenera el mundo desde la semilla**; por la red solo viajan los bloques cambiados. Sin streaming de chunks
   todavía, así que un cliente modificado vería el terreno (no a los jugadores). Pendiente para el mapa grande.
3. **Netty para E/S y un hilo de plataforma para el bucle.** Netty ya multiplexa conexiones, así que los Virtual Threads
   se reservan para la persistencia (Fase 3).
4. **Puertos:** servidor de juego en 8080 (como en el TDD); el servidor de desarrollo de TeaVM en 8000.
5. El servidor escucha por defecto en `127.0.0.1`; usa `--host=0.0.0.0` solo si no hay proxy delante.
6. Sin delta compression todavía: el paquete ya va filtrado por visibilidad y pesa 12 bytes por entidad visible.

## Despliegue (Oracle Cloud + Caddy + itch.io)

1. `./gradlew :server:installDist` y copia `server/build/install/server` a `/opt/gos/server` en la instancia.
2. Instala `deploy/grid-of-shadows.service` en systemd y `deploy/Caddyfile` en Caddy (con tu dominio).
3. Abre 80 y 443 en la lista de seguridad de la VCN **y** en el iptables de la imagen Ubuntu (suele bloquearlos).
4. Cambia `PRODUCTION_SERVER` en `teavm/.../WebLauncher.java` a tu `wss://dominio/ws`.
5. `./gradlew :teavm:gdx_teavm_web_js_build` y sube a itch.io, en un zip con `index.html` en la raíz, el contenido de
   `teavm/build/dist/js/webapp`.

## Qué está verificado y qué no

Fases 1 y 2: ejecutadas en local (Gradle, Netty, cliente de escritorio y comunicación cliente-servidor).

Fase 3, corte 1, verificada con JDK 21: `common` (34 comprobaciones) y la simulación del servidor (42: anti-wallhack,
speedhack, recolección, construcción, inventario lleno, carga, sonido de golpes, predicción con latencia y con carga, y
divergencia). El cliente y los lanzadores compilan contra stubs con las firmas de libGDX y TeaVM y no generan
`invokedynamic`; **el cliente de este corte aún no se ha ejecutado**.

**No verificado:** el render del HUD y de los nuevos arcos con libGDX, y la compilación a JS con TeaVM de este corte
(el entorno donde se escribió no alcanza Maven Central).

## Siguientes pasos

- **Fase 3 (lo que falta):** crafteo Tier 1 con barra de acceso rápido (hacha, pico, lanza, antorcha), combate cuerpo a cuerpo y full loot,
  reaparición de árboles y rocas, persistencia (H2/SQLite con Virtual Threads) y HUD con texto real.
- **Fase 4:** construcción con integridad estructural (BFS hacia `FOUNDATION`), cerraduras, zonas seguras.
- **Transversal:** interés por chunks (qué cambios de bloque recibe cada cliente), delta compression, audio real, comprobación de `Origin`.
