package com.spawnerfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Small immediate-mode drawing helpers: rounded rects, scaled text, colour maths. */
final class Ui {
    static float alpha = 1f; // global opacity multiplier for everything drawn through Ui

    static final int GREEN = 0xFF3DDC84, GREEN_DIM = 0xFF1E7A48;
    static final int BG = 0xF2101412, CARD = 0xFF181E1B, CARD_HOVER = 0xFF212A25;
    static final int BORDER = 0xFF26312B, TRACK = 0xFF2A332E;
    static final int TEXT = 0xFFE8F0EC, DIM = 0xFF7F8F87;
    static final int RED = 0xFFE5534B, WARN = 0xFFFFD166, WHITE = 0xFFFFFFFF;

    private Ui() {}

    static int mul(int argb, float a) {
        float f = Math.max(0f, Math.min(1f, a * alpha));
        int al = Math.round(((argb >>> 24) & 0xFF) * f);
        return (al << 24) | (argb & 0xFFFFFF);
    }

    static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int al = (int) (((a >>> 24) & 255) + (((b >>> 24) & 255) - ((a >>> 24) & 255)) * t);
        int r = (int) (((a >> 16) & 255) + (((b >> 16) & 255) - ((a >> 16) & 255)) * t);
        int g = (int) (((a >> 8) & 255) + (((b >> 8) & 255) - ((a >> 8) & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return (al << 24) | (r << 16) | (g << 8) | bl;
    }

    static TextRenderer tr() {
        return MinecraftClient.getInstance().textRenderer;
    }

    static int width(String s) {
        return tr().getWidth(s);
    }

    static void rect(DrawContext c, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) return;
        c.fill(x, y, x + w, y + h, mul(color, 1f));
    }

    static void rounded(DrawContext c, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        int col = mul(color, 1f);
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) {
            c.fill(x, y, x + w, y + h, col);
            return;
        }
        c.fill(x, y + r, x + w, y + h - r, col);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = (int) Math.round(r - Math.sqrt(Math.max(0, r * r - dy * dy)));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
    }

    static void text(DrawContext c, String s, int x, int y, int color) {
        int col = mul(color, 1f);
        if ((col >>> 24) < 8) return;
        c.drawText(tr(), s, x, y, col, false);
    }

    static void textScaled(DrawContext c, String s, float x, float y, float scale, int color) {
        var m = c.getMatrices();
        m.pushMatrix();
        m.translate(x, y);
        m.scale(scale, scale);
        text(c, s, 0, 0, color);
        m.popMatrix();
    }

    static void textRightScaled(DrawContext c, String s, float rightX, float y, float scale, int color) {
        textScaled(c, s, rightX - width(s) * scale, y, scale, color);
    }

    static void textCentered(DrawContext c, String s, int cx, int y, int color) {
        text(c, s, cx - width(s) / 2, y, color);
    }

    static int hsv(float h, float s, float v) {
        h = h - (float) Math.floor(h);
        int i = (int) (h * 6);
        float f = h * 6 - i;
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i % 6) {
            case 0 -> { r = v; g = t; b = p; }
            case 1 -> { r = q; g = v; b = p; }
            case 2 -> { r = p; g = v; b = t; }
            case 3 -> { r = p; g = q; b = v; }
            case 4 -> { r = t; g = p; b = v; }
            default -> { r = v; g = p; b = q; }
        }
        return ((int) (r * 255 + .5f) << 16) | ((int) (g * 255 + .5f) << 8) | (int) (b * 255 + .5f);
    }

    static float hueOf(int rgb) {
        float r = ((rgb >> 16) & 255) / 255f, g = ((rgb >> 8) & 255) / 255f, b = (rgb & 255) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
        if (d < 1e-4f) return 0f;
        float h;
        if (max == r) h = ((g - b) / d) % 6f;
        else if (max == g) h = (b - r) / d + 2f;
        else h = (r - g) / d + 4f;
        h /= 6f;
        if (h < 0) h += 1f;
        return h;
    }
}
