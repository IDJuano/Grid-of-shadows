package com.gridofshadows.client;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.gridofshadows.common.Const;
import com.gridofshadows.common.Inventory;
import com.gridofshadows.common.Items;

/** HUD en píxeles de pantalla (y hacia arriba): inventario arriba a la izquierda y barra de carga. */
final class Hud {
    private Hud() {}

    private static final Color PANEL = new Color(0f, 0f, 0f, 0.5f);
    private static final Color DEFAULT_ITEM = new Color(1f, 1f, 1f, 1f);
    private static final Color[] ITEM = new Color[8];

    static {
        ITEM[Items.WOOD] = new Color(0xa8743dff);
        ITEM[Items.STONE] = new Color(0x8a93a3ff);
    }

    static void draw(ShapeRenderer sr, Inventory inv, int screenH) {
        final float left = 8f, top = screenH - 8f, panelW = 132f, rowH = 30f;
        int rows = 0;
        for (int i = 0; i < inv.item.length; i++) if (inv.item[i] != Items.NONE) rows++;
        float panelH = rows * rowH + 26f;
        sr.setColor(PANEL);
        sr.rect(left, top - panelH, panelW, panelH);

        int row = 0;
        for (int i = 0; i < inv.item.length; i++) {
            byte it = inv.item[i];
            if (it == Items.NONE) continue;
            float rowBottom = top - 6f - (row + 1) * rowH;
            Color c = it >= 0 && it < ITEM.length && ITEM[it] != null ? ITEM[it] : DEFAULT_ITEM;
            sr.setColor(c.r * 0.6f, c.g * 0.6f, c.b * 0.6f, 1f);
            sr.rect(left + 8f, rowBottom + 5f, 20f, 20f);
            sr.setColor(c);
            sr.rect(left + 10f, rowBottom + 7f, 16f, 16f);
            sr.setColor(0.91f, 0.9f, 0.86f, 1f);
            PixelFont.draw(sr, inv.count[i], left + 36f, rowBottom + 7.5f, 3f);
            row++;
        }

        // Barra de carga: se pone roja al acercarse al máximo, donde la velocidad es mínima
        float barY = top - panelH + 8f, barW = panelW - 16f;
        float f = Math.min(1f, inv.load() / (float) Const.LOAD_CAPACITY);
        sr.setColor(1f, 1f, 1f, 0.15f);
        sr.rect(left + 8f, barY, barW, 6f);
        if (f < 0.75f) sr.setColor(0.95f, 0.83f, 0.54f, 1f); else sr.setColor(0.83f, 0.34f, 0.31f, 1f);
        sr.rect(left + 8f, barY, barW * f, 6f);
    }
}
