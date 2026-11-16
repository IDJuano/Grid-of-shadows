package com.gridofshadows.client;

import com.badlogic.gdx.graphics.glutils.ShapeRenderer;

/** Dígitos de 3 x 5 puntos dibujados con rectángulos: el HUD no depende de fuentes ni de assets. */
final class PixelFont {
    private PixelFont() {}

    private static final String[] DIGITS = {
        "111101101101111", "010110010010111", "111001111100111", "111001111001111", "101101111001001",
        "111100111001111", "111100111101111", "111001001010010", "111101111101111", "111101111001111"
    };

    /** Dibuja un entero con su esquina inferior izquierda en (x, y); px = tamaño de cada punto. */
    static void draw(ShapeRenderer sr, int value, float x, float y, float px) {
        String s = Integer.toString(Math.max(0, value));
        for (int i = 0; i < s.length(); i++) {
            String g = DIGITS[s.charAt(i) - '0'];
            for (int r = 0; r < 5; r++) {
                for (int c = 0; c < 3; c++) {
                    if (g.charAt(r * 3 + c) == '1') sr.rect(x + (i * 4 + c) * px, y + (4 - r) * px, px, px);
                }
            }
        }
    }
}
