package glen.euphoria.module.modules.client;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;

import java.util.ArrayList;
import java.util.List;

/**
 * Cleanly disables the client for the current session: unsubscribes every module
 * and global handler from the event bus, disables all modules, closes any open
 * client screen and clears the module list so all mixin iterators become no-ops.
 *
 * This is an in-memory kill switch only. It deliberately does NOT touch the
 * filesystem (no log/crash-report scrubbing, no config deletion, no self-deleting
 * the jar) — that behavior was anti-forensic and has been removed during the port.
 */
public final class SelfDestruct extends Module {

    private static boolean destroyed = false;

    public SelfDestruct() {
        super("SelfDestruct", "Cleanly disables the client and all modules for this session", -1, Category.CLIENT);
    }

    public static boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public void onEnable() {
        if (isNull()) {
            setEnabled(false);
            return;
        }

        destroyed = true;
        mc.execute(this::performSelfDestruct);
    }

    private void performSelfDestruct() {
        // ─────────────────────────────────────────────
        //  GLOBAL KILL SWITCH. NOTHING RUNS AFTER THIS.
        // ─────────────────────────────────────────────

        // Unsubscribe all modules individually (Orbit 0.2.4 compatible)
        try {
            for (Module module : EuphoriaClient.INSTANCE.getModuleManager().getModules()) {
                try { EuphoriaClient.INSTANCE.getEuphoriaEventBus().unsubscribe(module); } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        // Unsubscribe global handlers
        try { EuphoriaClient.INSTANCE.getEuphoriaEventBus().unsubscribe(EuphoriaClient.INSTANCE.getMouseModuleHandler()); } catch (Exception ignored) {}
        try { EuphoriaClient.INSTANCE.getEuphoriaEventBus().unsubscribe(EuphoriaClient.INSTANCE.getNotificationManager()); } catch (Exception ignored) {}

        // 1 — Close any client GUI that's open
        closeClientScreens();

        // 2 — Silently disable every module via reflection
        silentDisableAllModules();

        // 3 — Empty the module list so every mixin iterator becomes a no-op
        EuphoriaClient.INSTANCE.getModuleManager().getModules().clear();

        setEnabled(false);
    }

    private void closeClientScreens() {
        if (mc.currentScreen != null) {
            String name = mc.currentScreen.getClass().getName();
            if (name.startsWith("glen.euphoria")) {
                mc.setScreen(null);
            }
        }
    }

    private void silentDisableAllModules() {
        List<Module> snapshot = new ArrayList<>(EuphoriaClient.INSTANCE.getModuleManager().getModules());

        for (Module module : snapshot) {
            try {
                EuphoriaClient.INSTANCE.getEuphoriaEventBus().unsubscribe(module);
            } catch (Exception ignored) {}

            try {
                java.lang.reflect.Field enabledField = Module.class.getDeclaredField("enabled");
                enabledField.setAccessible(true);
                enabledField.setBoolean(module, false);

                java.lang.reflect.Field registeredField = Module.class.getDeclaredField("registered");
                registeredField.setAccessible(true);
                registeredField.setBoolean(module, false);
            } catch (Exception ignored) {}
        }
    }
}
