package com.spawnerfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.HashMap;
import java.util.Map;

/**
 * Fully custom dark settings screen. Input is polled from GLFW instead of overriding the
 * mouse callbacks, which keeps it independent of Minecraft's input API changes.
 */
public class SettingsScreen extends Screen {
    private static final int PW = 300, PH = 318, PAD = 14, IW = PW - PAD * 2;
    private static final int[] SWATCHES = {0x3DDC84, 0x2EE6D6, 0xFFD166, 0xFF9F43, 0xFF5C5C, 0xFF6FB5, 0xB28DFF, 0xFFFFFF};

    private long lastNs = System.nanoTime();
    private float dt, open;
    private boolean down, wasDown = true, pressed;
    private String activeSlider;
    private boolean draggingHud, closeRequested;
    private int grabX, grabY;
    private double lx, ly;
    private boolean inPanel;
    private final Map<String, Float> anim = new HashMap<>();
    private Config cfg;

    public SettingsScreen() {
        super(Text.literal("Spawner Finder"));
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public void removed() {
        Config.get().save();
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        cfg = Config.get();
        long now = System.nanoTime();
        dt = Math.min(0.05f, (now - lastNs) / 1e9f);
        lastNs = now;
        open = Math.min(1f, open + dt * 6f);
        float eo = 1f - (1f - open) * (1f - open);

        down = GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        pressed = down && !wasDown;

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        float s = Math.min(1f, Math.min((sh - 16f) / PH, (sw - 16f) / PW));
        s = Math.max(0.5f, s);
        float ox = (sw - PW * s) / 2f, oy = (sh - PH * s) / 2f + (1f - eo) * 12f;
        lx = (mx - ox) / s;
        ly = (my - oy) / s;
        inPanel = lx >= 0 && lx < PW && ly >= 0 && ly < PH;

        Ui.alpha = 1f;
        ctx.fill(0, 0, sw, sh, Ui.mul(0xB0050806, eo));
        hudPreview(ctx, sw, sh, mx, my, eo);

        Ui.alpha = eo;
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(ox, oy);
        m.scale(s, s);
        panel(ctx);
        m.popMatrix();
        Ui.alpha = 1f;

        if (!down) {
            if (activeSlider != null || draggingHud) cfg.save();
            activeSlider = null;
            draggingHud = false;
        }
        wasDown = down;
        if (closeRequested) {
            closeRequested = false;
            this.close();
        }
    }

    // ------------------------------------------------------------- HUD preview / dragging

    private void hudPreview(DrawContext ctx, int sw, int sh, int mx, int my, float eo) {
        int[] p = Hud.position(cfg, sw, sh);
        int hx = p[0], hy = p[1], hw = p[2], hh = p[3];
        boolean over = mx >= hx && mx < hx + hw && my >= hy && my < hy + hh;
        if (pressed && over && !inPanel && activeSlider == null) {
            draggingHud = true;
            grabX = mx - hx;
            grabY = my - hy;
        }
        if (draggingHud && down) {
            cfg.hudX = Math.max(0f, Math.min(1f, (mx - grabX) / (float) Math.max(1, sw - hw)));
            cfg.hudY = Math.max(0f, Math.min(1f, (my - grabY) / (float) Math.max(1, sh - hh)));
            p = Hud.position(cfg, sw, sh);
            hx = p[0]; hy = p[1];
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int count = SpawnerTracker.visible().size();
        double nearest = Overlay.nearestDistance(mc);
        if (count == 0) { count = 3; nearest = 14.2; } // sample values so the layout is visible
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.nanoTime() / 1e9 * 3.0);

        Ui.alpha = eo;
        Hud.draw(ctx, count, nearest, cfg.scanRange, cfg.autoScan, cfg.scannerEnabled,
                hx, hy, cfg.hudScale, cfg.hudEnabled ? 1f : 0.4f, 0f, pulse);

        float hover = anim("hudhover", (over && !inPanel) || draggingHud ? 1f : 0f);
        if (hover > 0.01f) {
            int col = Ui.mul(Ui.GREEN, hover * (0.45f + 0.35f * pulse));
            int x1 = hx - 3, y1 = hy - 3, x2 = hx + hw + 3, y2 = hy + hh + 3;
            for (int i = x1; i < x2; i += 6) {
                ctx.fill(i, y1, Math.min(i + 3, x2), y1 + 1, col);
                ctx.fill(i, y2 - 1, Math.min(i + 3, x2), y2, col);
            }
            for (int i = y1; i < y2; i += 6) {
                ctx.fill(x1, i, x1 + 1, Math.min(i + 3, y2), col);
                ctx.fill(x2 - 1, i, x2, Math.min(i + 3, y2), col);
            }
            String hint = draggingHud ? "Release to place" : "Drag to move";
            int ty = (hy + hh + 20 < sh) ? hy + hh + 7 : hy - 14;
            Ui.alpha = eo * hover;
            Ui.textCentered(ctx, hint, hx + hw / 2, ty, Ui.GREEN);
        }
        Ui.alpha = 1f;
    }

    // ------------------------------------------------------------- panel

    private void panel(DrawContext c) {
        Ui.rounded(c, -2, -1, PW + 4, PH + 6, 12, 0x40000000);
        Ui.rounded(c, 0, 0, PW, PH, 10, Ui.BORDER);
        Ui.rounded(c, 1, 1, PW - 2, PH - 2, 9, Ui.BG);

        // header
        Ui.rounded(c, PAD, 12, 20, 20, 6, Ui.GREEN);
        Ui.rounded(c, PAD + 5, 17, 10, 10, 3, Ui.BG);
        Ui.rounded(c, PAD + 8, 20, 4, 4, 1, Ui.GREEN);
        Ui.textScaled(c, "Spawner Finder", PAD + 28, 11, 1.25f, Ui.TEXT);
        Ui.textScaled(c, "Donut SMP edition  -  line-of-sight scanner", PAD + 28, 25, 0.72f, Ui.DIM);

        int cx = PW - PAD - 18, cy = 12;
        boolean hov = hover(cx, cy, 18, 18);
        float h = anim("close", hov ? 1f : 0f);
        Ui.rounded(c, cx, cy, 18, 18, 5, Ui.lerp(Ui.CARD, 0xFF4A2522, h));
        Ui.textCentered(c, "x", cx + 9, cy + 5, Ui.lerp(Ui.DIM, Ui.RED, h));
        if (hov && pressed) closeRequested = true;

        Ui.rect(c, PAD, 40, IW, 1, 0x22FFFFFF);

        section(c, "OVERLAY", 47);
        int half = (IW - 8) / 2;
        cfg.espEnabled = toggle(c, "esp", PAD, 59, half, "Spawner ESP", cfg.espEnabled);
        cfg.hudEnabled = toggle(c, "hud", PAD + half + 8, 59, half, "HUD panel", cfg.hudEnabled);
        cfg.scannerEnabled = toggle(c, "scanner", PAD, 83, half, "Scanner", cfg.scannerEnabled);
        cfg.autoScan = toggle(c, "auto", PAD + half + 8, 83, half, "Auto scan", cfg.autoScan);

        section(c, "SCANNING AND HUD", 111);
        float n = slider(c, "range", PAD, 123, IW, "Scan range", cfg.scanRange + " blocks", (cfg.scanRange - 8) / 120f);
        cfg.scanRange = Math.round(8 + n * 120);
        n = slider(c, "scale", PAD, 149, IW, "HUD scale", Math.round(cfg.hudScale * 100) + "%", (cfg.hudScale - 0.5f) / 1.5f);
        cfg.hudScale = Math.round((0.5f + n * 1.5f) * 20f) / 20f;
        cfg.hudX = slider(c, "hx", PAD, 175, IW, "HUD position X", Math.round(cfg.hudX * 100) + "%", cfg.hudX);
        cfg.hudY = slider(c, "hy", PAD, 201, IW, "HUD position Y", Math.round(cfg.hudY * 100) + "%", cfg.hudY);

        section(c, "OUTLINE COLOR", 231);
        float pitch = (IW - 26) / 7f;
        for (int i = 0; i < SWATCHES.length; i++) {
            int px = PAD + Math.round(i * pitch), py = 243;
            boolean sel = (cfg.outlineColor & 0xFFFFFF) == SWATCHES[i];
            boolean sh = hover(px, py, 26, 16);
            if (sh && pressed && activeSlider == null) {
                cfg.outlineColor = SWATCHES[i];
                cfg.save();
            }
            float a = anim("sw" + i, sel ? 1f : 0f);
            float hv = anim("swh" + i, sh ? 1f : 0f);
            if (a > 0.01f) {
                Ui.rounded(c, px - 3, py - 3, 32, 22, 7, Ui.mul(Ui.WHITE, a));
                Ui.rounded(c, px - 2, py - 2, 30, 20, 6, Ui.BG);
            }
            int off = hv > 0.5f && a < 0.5f ? 1 : 0;
            Ui.rounded(c, px - off, py - off, 26 + off * 2, 16 + off * 2, 4, 0xFF000000 | SWATCHES[i]);
        }
        hueSlider(c, PAD, 267, IW);

        // footer
        int fy = 289;
        if (button(c, "reset", PAD, fy, half, 20, "Reset HUD position", false)) {
            cfg.hudX = Config.DEF_X;
            cfg.hudY = Config.DEF_Y;
            cfg.hudScale = 1f;
            cfg.save();
        }
        if (button(c, "done", PAD + half + 8, fy, half, 20, "Done", true)) closeRequested = true;
    }

    private void section(DrawContext c, String s, int y) {
        Ui.textScaled(c, s, PAD, y, 0.72f, Ui.DIM);
        int w = (int) (Ui.width(s) * 0.72f);
        Ui.rect(c, PAD + w + 6, y + 3, IW - w - 6, 1, 0x18FFFFFF);
    }

    // ------------------------------------------------------------- widgets (immediate mode)

    private boolean hover(int x, int y, int w, int h) {
        return !draggingHud && open > 0.6f && lx >= x && lx < x + w && ly >= y && ly < y + h;
    }

    private float anim(String id, float target) {
        float cur = anim.getOrDefault(id, target);
        cur += (target - cur) * (1f - (float) Math.exp(-dt * 16f));
        if (Math.abs(target - cur) < 0.002f) cur = target;
        anim.put(id, cur);
        return cur;
    }

    private boolean toggle(DrawContext c, String id, int x, int y, int w, String label, boolean v) {
        boolean hov = hover(x, y, w, 20);
        if (hov && pressed && activeSlider == null) {
            v = !v;
            cfg.save();
        }
        float a = anim(id, v ? 1f : 0f), hv = anim(id + "h", hov ? 1f : 0f);
        Ui.rounded(c, x, y, w, 20, 6, Ui.lerp(Ui.CARD, Ui.CARD_HOVER, hv));
        Ui.text(c, label, x + 8, y + 6, Ui.lerp(Ui.DIM, Ui.TEXT, Math.max(a, hv * 0.7f)));
        int sx = x + w - 32, sy = y + 4;
        Ui.rounded(c, sx, sy, 24, 12, 6, Ui.lerp(Ui.TRACK, Ui.GREEN, a));
        Ui.rounded(c, sx + 2 + Math.round(a * 12), sy + 2, 8, 8, 4, Ui.lerp(0xFF8A968F, 0xFF0C100E, a));
        return v;
    }

    private float slider(DrawContext c, String id, int x, int y, int w, String label, String val, float norm) {
        boolean hov = hover(x, y, w, 22);
        if (hov && pressed && activeSlider == null && !draggingHud) activeSlider = id;
        boolean act = id.equals(activeSlider);
        if (act && down) norm = Math.max(0f, Math.min(1f, (float) ((lx - x) / w)));
        float hv = anim(id + "h", hov || act ? 1f : 0f);
        Ui.text(c, label, x, y, Ui.lerp(Ui.DIM, Ui.TEXT, hv));
        Ui.text(c, val, x + w - Ui.width(val), y, Ui.GREEN);
        int ty = y + 13;
        Ui.rounded(c, x, ty, w, 4, 2, Ui.TRACK);
        int fill = Math.round(w * norm);
        Ui.rounded(c, x, ty, Math.max(4, fill), 4, 2, Ui.GREEN);
        int kr = 4 + Math.round(hv);
        Ui.rounded(c, x + fill - kr, ty + 2 - kr, kr * 2, kr * 2, kr, Ui.WHITE);
        return norm;
    }

    private void hueSlider(DrawContext c, int x, int y, int w) {
        boolean hov = hover(x, y, w, 14);
        if (hov && pressed && activeSlider == null && !draggingHud) activeSlider = "hue";
        float hue = Ui.hueOf(cfg.outlineColor);
        if ("hue".equals(activeSlider) && down) {
            hue = Math.max(0f, Math.min(1f, (float) ((lx - x) / w)));
            cfg.outlineColor = Ui.hsv(hue, 0.75f, 1f);
        }
        Ui.rounded(c, x, y + 3, w, 8, 4, Ui.BORDER);
        int segs = (w - 6) / 3;
        for (int i = 0; i < segs; i++) {
            Ui.rect(c, x + 3 + i * 3, y + 4, 3, 6, 0xFF000000 | Ui.hsv(i / (float) segs, 0.75f, 1f));
        }
        int kx = x + Math.round(hue * w);
        Ui.rounded(c, kx - 4, y, 8, 14, 4, Ui.WHITE);
        Ui.rounded(c, kx - 2, y + 2, 4, 10, 2, 0xFF000000 | (cfg.outlineColor & 0xFFFFFF));
    }

    private boolean button(DrawContext c, String id, int x, int y, int w, int h, String label, boolean primary) {
        boolean hov = hover(x, y, w, h);
        float hv = anim(id, hov ? 1f : 0f);
        int bg = primary ? Ui.lerp(Ui.GREEN, 0xFF63EBA0, hv) : Ui.lerp(Ui.CARD, Ui.CARD_HOVER, hv);
        Ui.rounded(c, x, y, w, h, 6, bg);
        Ui.textCentered(c, label, x + w / 2, y + (h - 8) / 2, primary ? 0xFF08120C : Ui.TEXT);
        return hov && pressed && activeSlider == null;
    }
}
