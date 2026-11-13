package com.spawnerfinder;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Persistent settings, saved to config/spawnerfinder.json. */
public final class Config {
    public static final float DEF_X = 0.015f, DEF_Y = 0.03f;

    public boolean espEnabled = true;
    public boolean hudEnabled = true;
    public boolean scannerEnabled = true;
    public boolean autoScan = true;
    public int scanRange = 32;
    public int outlineColor = 0x3DDC84;
    public float hudX = DEF_X;   // 0..1 across the free screen width
    public float hudY = DEF_Y;   // 0..1 across the free screen height
    public float hudScale = 1.0f;

    private static Config instance = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static Config get() {
        return instance;
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("spawnerfinder.json");
    }

    public static void load() {
        try {
            Path p = file();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) instance = c;
            }
        } catch (Exception e) {
            SpawnerFinderClient.LOG.warn("Could not read config, using defaults", e);
            instance = new Config();
        }
        instance.save();
    }

    public void save() {
        try {
            clamp();
            Files.writeString(file(), GSON.toJson(this));
        } catch (IOException e) {
            SpawnerFinderClient.LOG.warn("Could not save config", e);
        }
    }

    private void clamp() {
        scanRange = Math.max(8, Math.min(128, scanRange));
        hudScale = Math.max(0.5f, Math.min(2.0f, hudScale));
        hudX = Math.max(0f, Math.min(1f, hudX));
        hudY = Math.max(0f, Math.min(1f, hudY));
        outlineColor &= 0xFFFFFF;
    }
}
