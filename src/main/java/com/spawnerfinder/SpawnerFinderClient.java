package com.spawnerfinder;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SpawnerFinderClient implements ClientModInitializer {
    public static final String MOD_ID = "spawnerfinder";
    public static final Logger LOG = LoggerFactory.getLogger("SpawnerFinder");

    private static KeyBinding scanKey, menuKey, toggleKey;
    private static boolean tickErrorLogged;

    @Override
    public void onInitializeClient() {
        Config.load();

        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of(MOD_ID, "main"));
        scanKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spawnerfinder.scan", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_J, category));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spawnerfinder.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.spawnerfinder.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_U, category));

        ClientTickEvents.END_CLIENT_TICK.register(SpawnerFinderClient::onTick);
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SpawnerTracker.clear());
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SpawnerTracker.clear());

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of(MOD_ID, "overlay"),
                (ctx, tickCounter) -> Overlay.render(ctx));

        LOG.info("Spawner Finder loaded");
    }

    private static void onTick(MinecraftClient mc) {
        try {
            Config cfg = Config.get();
            while (menuKey.wasPressed()) {
                if (mc.currentScreen == null) mc.setScreen(new SettingsScreen());
            }
            while (toggleKey.wasPressed()) {
                cfg.scannerEnabled = !cfg.scannerEnabled;
                cfg.save();
                if (mc.player != null) {
                    mc.player.sendMessage(Text.literal("[Spawner Finder] Scanner "
                            + (cfg.scannerEnabled ? "ON" : "OFF")), true);
                }
            }
            boolean manual = false;
            while (scanKey.wasPressed()) manual = true;
            SpawnerTracker.tick(mc, manual);
        } catch (Throwable t) {
            if (!tickErrorLogged) {
                tickErrorLogged = true;
                LOG.error("Spawner Finder tick error (further errors suppressed)", t);
            }
        }
    }
}
