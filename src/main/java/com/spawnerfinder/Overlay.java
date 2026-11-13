package com.spawnerfinder;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.List;
import java.util.Locale;

/**
 * Draws the HUD and the spawner ESP. The ESP is a screen-space bracket outline projected from the
 * camera, and it is only ever fed spawners that passed the line-of-sight check - nothing is shown
 * through walls.
 */
public final class Overlay {
    private static float shownDist = -1f;
    private static int lastCount = 0;
    private static long popStart = 0, lastNs = System.nanoTime();
    private static boolean errorLogged;

    private Overlay() {}

    public static double nearestDistance(MinecraftClient mc) {
        List<BlockPos> list = SpawnerTracker.visible();
        if (list.isEmpty() || mc.gameRenderer == null) return -1;
        Vec3d cp = mc.gameRenderer.getCamera().getPos();
        double best = Double.MAX_VALUE;
        for (BlockPos p : list) best = Math.min(best, Vec3d.ofCenter(p).distanceTo(cp));
        return best;
    }

    public static void render(DrawContext ctx) {
        try {
            renderInner(ctx);
        } catch (Throwable t) {
            if (!errorLogged) {
                errorLogged = true;
                SpawnerFinderClient.LOG.error("Overlay render error (further errors suppressed)", t);
            }
        }
    }

    private static void renderInner(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.world == null || mc.options.hudHidden) return;
        if (mc.currentScreen instanceof SettingsScreen) return;
        Config cfg = Config.get();
        Ui.alpha = 1f;

        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        float pulse = 0.5f + 0.5f * (float) Math.sin(now / 1e9 * 3.0);

        List<BlockPos> list = SpawnerTracker.visible();
        int count = list.size();
        double nearest = nearestDistance(mc);

        if (count != lastCount) {
            lastCount = count;
            popStart = now;
        }
        float pt = (now - popStart) / 1e9f / 0.3f;
        float pop = pt < 1f ? (float) Math.sin(pt * Math.PI) : 0f;
        if (nearest >= 0) {
            shownDist = shownDist < 0 ? (float) nearest
                    : shownDist + ((float) nearest - shownDist) * (1f - (float) Math.exp(-dt * 12f));
        } else {
            shownDist = -1f;
        }

        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();

        if (cfg.espEnabled && cfg.scannerEnabled && count > 0
                && mc.options.getPerspective().isFirstPerson()) {
            drawEsp(ctx, mc, list, sw, sh, cfg.outlineColor, pulse);
        }

        if (cfg.hudEnabled) {
            int[] pos = Hud.position(cfg, sw, sh);
            Hud.draw(ctx, count, shownDist, cfg.scanRange, cfg.autoScan, cfg.scannerEnabled,
                    pos[0], pos[1], cfg.hudScale, 1f, pop, pulse);
        }
    }

    // ---------------------------------------------------------------- ESP

    private static void drawEsp(DrawContext ctx, MinecraftClient mc, List<BlockPos> list,
                                int sw, int sh, int rgb, float pulse) {
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d cp = cam.getPos();
        double yr = Math.toRadians(cam.getYaw()), pr = Math.toRadians(cam.getPitch());
        double cpch = Math.cos(pr), spch = Math.sin(pr), cy = Math.cos(yr), sy = Math.sin(yr);
        Vec3d f = new Vec3d(-sy * cpch, -spch, cy * cpch);
        Vec3d r = new Vec3d(-cy, 0, -sy);
        Vec3d u = r.crossProduct(f);
        double tanHalf = Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
        double aspect = (double) sw / (double) sh;

        // nearest-first so labels for the closest ones win when crowded
        List<BlockPos> sorted = new java.util.ArrayList<>(list);
        sorted.sort(java.util.Comparator.comparingDouble(p -> Vec3d.ofCenter(p).squaredDistanceTo(cp)));

        int labels = 0;
        for (BlockPos p : sorted) {
            double minX = 1e9, minY = 1e9, maxX = -1e9, maxY = -1e9;
            int valid = 0;
            for (int i = 0; i < 8; i++) {
                Vec3d v = new Vec3d(p.getX() + (i & 1), p.getY() + ((i >> 1) & 1), p.getZ() + ((i >> 2) & 1));
                Vec3d d = v.subtract(cp);
                double z = d.dotProduct(f);
                if (z < 0.05) continue;
                double nx = d.dotProduct(r) / (z * tanHalf * aspect);
                double ny = d.dotProduct(u) / (z * tanHalf);
                double sx = (nx + 1) * 0.5 * sw, sy2 = (1 - ny) * 0.5 * sh;
                valid++;
                minX = Math.min(minX, sx); maxX = Math.max(maxX, sx);
                minY = Math.min(minY, sy2); maxY = Math.max(maxY, sy2);
            }
            if (valid < 8) continue;
            if (maxX < 0 || minX > sw || maxY < 0 || minY > sh) continue;

            double midX = (minX + maxX) / 2, midY = (minY + maxY) / 2;
            if (maxX - minX < 8) { minX = midX - 4; maxX = midX + 4; }
            if (maxY - minY < 8) { minY = midY - 4; maxY = midY + 4; }

            int x1 = (int) Math.floor(minX) - 2, y1 = (int) Math.floor(minY) - 2;
            int x2 = (int) Math.ceil(maxX) + 2, y2 = (int) Math.ceil(maxY) + 2;
            double dist = Vec3d.ofCenter(p).distanceTo(cp);
            boolean label = labels < 10;
            if (label) labels++;
            drawBox(ctx, x1, y1, x2, y2, rgb, pulse, label ? dist : -1);
        }
    }

    private static void drawBox(DrawContext c, int x1, int y1, int x2, int y2, int rgb, float pulse, double dist) {
        int base = 0xFF000000 | rgb;
        int w = x2 - x1, h = y2 - y1;
        int len = Math.max(4, Math.min(14, Math.min(w, h) / 3));
        int strong = Ui.mul(base, 0.78f + 0.22f * pulse);

        // very faint tint and thin full outline (keeps the view clear)
        c.fill(x1, y1, x2, y2, Ui.mul(base, 0.06f));
        int thin = Ui.mul(base, 0.30f);
        c.fill(x1, y1, x2, y1 + 1, thin);
        c.fill(x1, y2 - 1, x2, y2, thin);
        c.fill(x1, y1, x1 + 1, y2, thin);
        c.fill(x2 - 1, y1, x2, y2, thin);

        // 2px corner brackets with a soft outer glow
        int glow = Ui.mul(base, 0.16f);
        bracket(c, x1 - 1, y1 - 1, x2 + 1, y2 + 1, len + 1, 1, glow);
        bracket(c, x1, y1, x2, y2, len, 2, strong);

        if (dist >= 0) {
            String s = String.format(Locale.ROOT, "%.1f m", dist);
            float sc = 0.8f;
            int tw = (int) (Ui.width(s) * sc) + 10;
            int lx = (x1 + x2) / 2 - tw / 2;
            int ly = y1 - 13 >= 2 ? y1 - 13 : y2 + 3;
            Ui.alpha = 1f;
            Ui.rounded(c, lx, ly, tw, 11, 5, 0xD0101412);
            Ui.rounded(c, lx + 3, ly + 4, 3, 3, 1, strong);
            Ui.textScaled(c, s, lx + 8, ly + 2, sc, Ui.TEXT);
        }
    }

    private static void bracket(DrawContext c, int x1, int y1, int x2, int y2, int len, int t, int col) {
        // top-left
        c.fill(x1 + 1, y1, x1 + len, y1 + t, col);
        c.fill(x1, y1 + 1, x1 + t, y1 + len, col);
        // top-right
        c.fill(x2 - len, y1, x2 - 1, y1 + t, col);
        c.fill(x2 - t, y1 + 1, x2, y1 + len, col);
        // bottom-left
        c.fill(x1 + 1, y2 - t, x1 + len, y2, col);
        c.fill(x1, y2 - len, x1 + t, y2 - 1, col);
        // bottom-right
        c.fill(x2 - len, y2 - t, x2 - 1, y2, col);
        c.fill(x2 - t, y2 - len, x2, y2 - 1, col);
    }
}
