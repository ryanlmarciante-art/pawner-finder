package com.spawnerfinder;

import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.text.Text;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.chunk.WorldChunk;

import java.util.*;

/**
 * Finds spawners using only data the server already sent (loaded chunk block entities)
 * and keeps ONLY the ones with a clear line of sight from the camera.
 * Spawners are de-duplicated by block position.
 */
public final class SpawnerTracker {
    private static final int AUTO_INTERVAL = 5;   // ticks between automatic scans
    private static final int AUTO_HOLD = 8;       // ticks a spawner stays after last seen (anti-flicker)
    private static final int MANUAL_HOLD = 100;   // ticks a manual scan result stays (5 s)
    private static final int MAX_SPAWNERS = 128;  // hard cap per scan

    private static final Map<Long, Integer> expiry = new HashMap<>();
    private static volatile List<BlockPos> snapshot = List.of();
    private static int ticks, lastScan = -100;
    private static ClientWorld lastWorld;
    private static boolean errorLogged;

    private static final double[][] SAMPLES = buildSamples();

    private static double[][] buildSamples() {
        List<double[]> l = new ArrayList<>();
        l.add(new double[]{.5, .5, .5});
        double a = 0.04, b = 0.96;
        l.add(new double[]{a, .5, .5}); l.add(new double[]{b, .5, .5});
        l.add(new double[]{.5, a, .5}); l.add(new double[]{.5, b, .5});
        l.add(new double[]{.5, .5, a}); l.add(new double[]{.5, .5, b});
        double c = 0.1, d = 0.9;
        for (double x : new double[]{c, d})
            for (double y : new double[]{c, d})
                for (double z : new double[]{c, d})
                    l.add(new double[]{x, y, z});
        return l.toArray(new double[0][]);
    }

    private SpawnerTracker() {}

    public static List<BlockPos> visible() {
        return snapshot;
    }

    public static void clear() {
        expiry.clear();
        snapshot = List.of();
        lastWorld = null;
    }

    public static void tick(MinecraftClient mc, boolean manual) {
        Config cfg = Config.get();
        ticks++;
        if (mc.world == null || mc.player == null) {
            if (!expiry.isEmpty()) clear();
            return;
        }
        if (mc.world != lastWorld) {
            expiry.clear();
            snapshot = List.of();
            lastWorld = mc.world;
        }
        if (!cfg.scannerEnabled) {
            if (!expiry.isEmpty()) {
                expiry.clear();
                snapshot = List.of();
            }
            if (manual) mc.player.sendMessage(Text.literal("[Spawner Finder] Scanner is OFF"), true);
            return;
        }

        boolean due = manual || (cfg.autoScan && ticks - lastScan >= AUTO_INTERVAL);
        if (due) {
            lastScan = ticks;
            try {
                scan(mc, cfg, manual ? MANUAL_HOLD : AUTO_HOLD);
            } catch (Throwable t) {
                if (!errorLogged) {
                    errorLogged = true;
                    SpawnerFinderClient.LOG.error("Scan error (further errors suppressed)", t);
                }
            }
        }
        rebuild(mc.world);
        if (manual) {
            int n = snapshot.size();
            mc.player.sendMessage(Text.literal("[Spawner Finder] " + n + " spawner" + (n == 1 ? "" : "s")
                    + " in view (" + cfg.scanRange + " blocks)"), true);
        }
    }

    private static void rebuild(ClientWorld world) {
        List<BlockPos> out = new ArrayList<>();
        Iterator<Map.Entry<Long, Integer>> it = expiry.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, Integer> e = it.next();
            BlockPos pos = BlockPos.fromLong(e.getKey());
            if (e.getValue() < ticks || !world.getBlockState(pos).isOf(Blocks.SPAWNER)) {
                it.remove();
                continue;
            }
            out.add(pos);
        }
        snapshot = List.copyOf(out);
    }

    private static void scan(MinecraftClient mc, Config cfg, int hold) {
        ClientWorld w = mc.world;
        ClientPlayerEntity p = mc.player;
        Vec3d eye = p.getEyePos();
        double r = cfg.scanRange, r2 = r * r;
        int pcx = p.getBlockX() >> 4, pcz = p.getBlockZ() >> 4;
        int cr = (int) Math.ceil(r / 16.0) + 1;
        int found = 0;

        for (int dx = -cr; dx <= cr; dx++) {
            for (int dz = -cr; dz <= cr; dz++) {
                WorldChunk ch = w.getChunkManager().getWorldChunk(pcx + dx, pcz + dz);
                if (ch == null) continue;
                // Only block entities (a few per chunk) are inspected - no block-by-block scanning.
                for (BlockPos pos : new ArrayList<>(ch.getBlockEntities().keySet())) {
                    if (Vec3d.ofCenter(pos).squaredDistanceTo(eye) > r2) continue;
                    if (!w.getBlockState(pos).isOf(Blocks.SPAWNER)) continue;
                    if (++found > MAX_SPAWNERS) return;
                    if (isVisible(w, p, eye, pos)) expiry.put(pos.asLong(), ticks + hold);
                }
            }
        }
    }

    /** True only if a ray from the camera reaches this exact block without hitting anything else first. */
    private static boolean isVisible(ClientWorld w, ClientPlayerEntity p, Vec3d eye, BlockPos pos) {
        for (double[] s : SAMPLES) {
            Vec3d target = new Vec3d(pos.getX() + s[0], pos.getY() + s[1], pos.getZ() + s[2]);
            BlockHitResult hit = w.raycast(new RaycastContext(eye, target,
                    RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, p));
            if (hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos)) return true;
        }
        return false;
    }
}
