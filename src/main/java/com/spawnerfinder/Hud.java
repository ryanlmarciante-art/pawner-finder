package com.spawnerfinder;

import net.minecraft.client.gui.DrawContext;

import java.util.Locale;

/** The on-screen info panel. Used by the live overlay and by the settings-screen preview. */
final class Hud {
    static final int W = 150, H = 64;

    private Hud() {}

    static int[] position(Config cfg, int sw, int sh) {
        int hw = Math.round(W * cfg.hudScale), hh = Math.round(H * cfg.hudScale);
        int x = Math.round(cfg.hudX * Math.max(0, sw - hw));
        int y = Math.round(cfg.hudY * Math.max(0, sh - hh));
        return new int[]{x, y, hw, hh};
    }

    /** nearest < 0 means "none". pop is 0..1 (count-change bump), pulse is 0..1 (idle animation). */
    static void draw(DrawContext c, int count, double nearest, int range, boolean auto, boolean on,
                     int x, int y, float scale, float alpha, float pop, float pulse) {
        float prev = Ui.alpha;
        Ui.alpha = prev * alpha;
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);

        // card
        Ui.rounded(c, -1, 0, W + 2, H + 2, 7, 0x30000000);
        Ui.rounded(c, 0, 0, W, H, 6, 0xFF1F2B24);
        Ui.rounded(c, 1, 1, W - 2, H - 2, 5, Ui.BG);
        Ui.rounded(c, 1, 9, 3, H - 18, 1, on ? Ui.GREEN : Ui.TRACK);

        // header
        Ui.textScaled(c, "SPAWNER FINDER", 11, 8, 0.75f, Ui.DIM);
        int pw = 32, px = W - 9 - pw, py = 6;
        Ui.rounded(c, px, py, pw, 11, 5, on ? 0x331E7A48 : 0x22FFFFFF);
        float dot = on ? 0.55f + 0.45f * pulse : 1f;
        Ui.rounded(c, px + 6, py + 3, 5, 5, 2, on ? Ui.mul(Ui.GREEN, dot) : 0xFF59635D);
        Ui.textScaled(c, on ? "ON" : "OFF", px + 14, py + 2, 0.85f, on ? Ui.GREEN : Ui.DIM);

        // big count (left)
        float cs = 2.0f * (1f + 0.22f * pop);
        Ui.textScaled(c, String.valueOf(count), 11, 20 - (cs - 2f) * 4f, cs, count > 0 ? Ui.TEXT : Ui.DIM);
        Ui.textScaled(c, count == 1 ? "spawner visible" : "spawners visible", 11, 38, 0.7f, Ui.DIM);

        // nearest distance (right)
        boolean has = nearest >= 0;
        String d = has ? String.format(Locale.ROOT, "%.1f", nearest) : "--";
        int dc = !has ? Ui.DIM : (nearest < 10 ? Ui.WARN : Ui.GREEN);
        Ui.textRightScaled(c, d, W - 10, 20, 1.7f, dc);
        Ui.textRightScaled(c, "blocks to nearest", W - 10, 38, 0.7f, Ui.DIM);

        // footer
        Ui.rect(c, 10, 48, W - 20, 1, 0x1EFFFFFF);
        Ui.textScaled(c, "RANGE", 10, 53, 0.7f, Ui.DIM);
        Ui.textScaled(c, range + " blocks", 38, 52, 0.85f, Ui.TEXT);
        Ui.textRightScaled(c, auto ? "AUTO SCAN" : "MANUAL", W - 10, 53, 0.7f, auto ? Ui.GREEN : Ui.DIM);

        m.popMatrix();
        Ui.alpha = prev;
    }
}
