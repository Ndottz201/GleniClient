package glen.euphoria.module.modules.client;

import glen.euphoria.EuphoriaClient;
import glen.euphoria.module.Category;
import glen.euphoria.module.Module;
import glen.euphoria.utils.AutoSaveManager;
import glen.euphoria.utils.mc.ChatUtil;
import glen.euphoria.utils.notification.NotificationManager;
import glen.euphoria.utils.render.nanovg.NanoVGContext;
import glen.euphoria.utils.render.nanovg.NanoVGRenderer;
import glen.euphoria.utils.rotation.SilentRotation;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

public final class SelfDestruct extends Module {

    private static boolean destroyed = false;

    public SelfDestruct() {
        super("Self Destruct", "Unloads client and cleans files", -1, Category.CLIENT);
    }

    public static boolean isDestroyed() {
        return destroyed;
    }

    @Override
    public void onEnable() {
        if (destroyed) {
            setEnabled(false);
            return;
        }

        destroyed = true;
        mc.execute(this::performSelfDestruct);
    }

    private void performSelfDestruct() {
        // 1 — User feedback
        if (mc.player != null) {
            ChatUtil.addChatMessage("§a[Euphoria] Unloaded client.");
        }

        // 2 — Stop auto-save timer so configs are not re-written
        try {
            AutoSaveManager.getInstance().shutdown();
        } catch (Exception ignored) {}

        // 3 — Clear notifications and combat state
        try {
            NotificationManager.getInstance().getNotifications().clear();
        } catch (Exception ignored) {}

        try {
            SilentRotation.clear();
        } catch (Exception ignored) {}

        // 4 — Close any client GUI that's open
        closeClientScreens();

        // 5 — Unsubscribe all modules and global handlers from Orbit event bus
        try {
            if (EuphoriaClient.INSTANCE != null && EuphoriaClient.INSTANCE.getModuleManager() != null) {
                for (Module module : EuphoriaClient.INSTANCE.getModuleManager().getModules()) {
                    try {
                        EuphoriaClient.INSTANCE.getEuphoriaEventBus().unsubscribe(module);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}

        try {
            if (EuphoriaClient.INSTANCE != null) {
                if (EuphoriaClient.INSTANCE.getMouseModuleHandler() != null) {
                    EuphoriaClient.INSTANCE.getEuphoriaEventBus().unsubscribe(EuphoriaClient.INSTANCE.getMouseModuleHandler());
                }
                if (EuphoriaClient.INSTANCE.getNotificationManager() != null) {
                    EuphoriaClient.INSTANCE.getEuphoriaEventBus().unsubscribe(EuphoriaClient.INSTANCE.getNotificationManager());
                }
            }
        } catch (Exception ignored) {}

        // 6 — Silently disable every module and call onDisable so state is cleanly restored to vanilla
        silentDisableAllModules();

        // 7 — Empty the module list so every mixin iterator becomes a no-op
        try {
            if (EuphoriaClient.INSTANCE != null && EuphoriaClient.INSTANCE.getModuleManager() != null) {
                EuphoriaClient.INSTANCE.getModuleManager().getModules().clear();
            }
        } catch (Exception ignored) {}

        // 8 — Clean up NanoVG graphics resources
        try {
            NanoVGRenderer.cleanup();
            NanoVGContext.cleanup();
        } catch (Exception ignored) {}

        // 9 — Revert window title to vanilla
        try {
            mc.updateWindowTitle();
        } catch (Exception ignored) {}

        // 10 — Clean up mod configuration directory (Euphoria/)
        cleanConfigFiles();

        // 11 — Clean Fabric cache entries (.fabric/)
        cleanFabricCache();

        // 12 — Clean crash reports mentioning euphoria
        cleanCrashReports();

        // 13 — Delete mod jar and any duplicates, tracking locked files
        List<File> lockedFiles = new ArrayList<>();
        cleanModFiles(lockedFiles);

        // 14 — Spawn background cleanup task for remaining locked files and log stripping
        File latestLog = null;
        if (mc.runDirectory != null) {
            latestLog = new File(mc.runDirectory, "logs" + File.separator + "latest.log");
        }
        scheduleBackgroundCleanup(lockedFiles, latestLog);

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
        if (EuphoriaClient.INSTANCE == null || EuphoriaClient.INSTANCE.getModuleManager() == null) return;
        List<Module> snapshot = new ArrayList<>(EuphoriaClient.INSTANCE.getModuleManager().getModules());

        for (Module module : snapshot) {
            try {
                EuphoriaClient.INSTANCE.getEuphoriaEventBus().unsubscribe(module);
            } catch (Exception ignored) {}

            try {
                if (module.isEnabled()) {
                    module.onDisable();
                }
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

    private void cleanConfigFiles() {
        try {
            File runDir = mc.runDirectory;
            if (runDir != null) {
                File euphoriaDir = new File(runDir, "Euphoria");
                deleteDirectoryRecursively(euphoriaDir);
            }
        } catch (Exception ignored) {}
    }

    private void cleanFabricCache() {
        try {
            File runDir = mc.runDirectory;
            if (runDir != null) {
                File fabricDir = new File(runDir, ".fabric");
                if (fabricDir.exists() && fabricDir.isDirectory()) {
                    Files.walkFileTree(fabricDir.toPath(), new SimpleFileVisitor<Path>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if (file.getFileName().toString().toLowerCase().contains("euphoria")) {
                                Files.deleteIfExists(file);
                            }
                            return FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                            if (dir.getFileName().toString().toLowerCase().contains("euphoria")) {
                                Files.deleteIfExists(dir);
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
        } catch (Exception ignored) {}
    }

    private void cleanCrashReports() {
        try {
            File runDir = mc.runDirectory;
            if (runDir != null) {
                File crashReportsDir = new File(runDir, "crash-reports");
                if (crashReportsDir.exists() && crashReportsDir.isDirectory()) {
                    File[] files = crashReportsDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile() && f.getName().endsWith(".txt")) {
                                boolean matches = f.getName().toLowerCase().contains("euphoria");
                                if (!matches) {
                                    try {
                                        String content = Files.readString(f.toPath());
                                        if (content.contains("euphoria") || content.contains("glen.euphoria")) {
                                            matches = true;
                                        }
                                    } catch (Exception ignored) {}
                                }
                                if (matches) {
                                    Files.deleteIfExists(f.toPath());
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
    }

    private void cleanModFiles(List<File> lockedFiles) {
        File jarFile = null;
        try {
            java.net.URL location = SelfDestruct.class.getProtectionDomain().getCodeSource().getLocation();
            if (location != null) {
                jarFile = new File(location.toURI());
            }
        } catch (Exception ignored) {}

        List<File> jarsToDelete = new ArrayList<>();
        if (jarFile != null && jarFile.exists()) {
            jarsToDelete.add(jarFile);
        }

        try {
            File runDir = mc.runDirectory;
            if (runDir != null) {
                File modsDir = new File(runDir, "mods");
                if (modsDir.exists() && modsDir.isDirectory()) {
                    File[] files = modsDir.listFiles();
                    if (files != null) {
                        for (File f : files) {
                            if (f.isFile() && f.getName().toLowerCase().contains("euphoria") && f.getName().endsWith(".jar")) {
                                if (!jarsToDelete.contains(f)) {
                                    jarsToDelete.add(f);
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {}

        for (File f : jarsToDelete) {
            attemptDeleteOrMove(f, lockedFiles);
        }
    }

    private void attemptDeleteOrMove(File file, List<File> lockedFiles) {
        if (file == null || !file.exists()) return;

        // 1. Try immediate delete
        try {
            if (file.delete()) return;
        } catch (Exception ignored) {}

        // 2. Try rename to temp directory
        File tempTarget = new File(System.getProperty("java.io.tmpdir"), "tmp_" + System.currentTimeMillis() + "_" + file.getName() + ".tmp");
        boolean moved = false;
        try {
            moved = file.renameTo(tempTarget);
        } catch (Exception ignored) {}

        if (!moved) {
            try {
                Files.move(file.toPath(), tempTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
                moved = true;
            } catch (Exception ignored) {}
        }

        if (moved) {
            try {
                if (tempTarget.delete()) return;
            } catch (Exception ignored) {}
            tempTarget.deleteOnExit();
            lockedFiles.add(tempTarget);
            return;
        }

        // 3. Try rename within same folder (e.g. to hidden .tmp)
        File localTmp = new File(file.getParentFile(), "." + System.currentTimeMillis() + ".tmp");
        try {
            if (file.renameTo(localTmp)) {
                try {
                    if (localTmp.delete()) return;
                } catch (Exception ignored) {}
                localTmp.deleteOnExit();
                lockedFiles.add(localTmp);
                return;
            }
        } catch (Exception ignored) {}

        // 4. File remains locked at original path
        file.deleteOnExit();
        lockedFiles.add(file);
    }

    private void scheduleBackgroundCleanup(List<File> lockedFiles, File latestLog) {
        if (lockedFiles.isEmpty() && (latestLog == null || !latestLog.exists())) return;
        try {
            File tempDir = new File(System.getProperty("java.io.tmpdir"));
            File batFile = new File(tempDir, "euphoria_clean_" + System.currentTimeMillis() + ".bat");
            StringBuilder sb = new StringBuilder();
            sb.append("@echo off\r\n");
            sb.append("setlocal\r\n");
            sb.append("set count=0\r\n");
            sb.append(":loop\r\n");
            sb.append("set /a count+=1\r\n");
            sb.append("if %count% geq 600 goto cleanlog\r\n");
            sb.append("set remaining=0\r\n");

            for (File f : lockedFiles) {
                String p = f.getAbsolutePath();
                sb.append("if exist \"").append(p).append("\" (\r\n");
                sb.append("    del /f /q \"").append(p).append("\" >nul 2>&1\r\n");
                sb.append("    if exist \"").append(p).append("\" set remaining=1\r\n");
                sb.append(")\r\n");
            }

            sb.append("if %remaining% equ 0 goto cleanlog\r\n");
            sb.append("timeout /t 1 /nobreak >nul 2>&1\r\n");
            sb.append("goto loop\r\n");

            sb.append(":cleanlog\r\n");
            if (latestLog != null && latestLog.exists()) {
                String logPath = latestLog.getAbsolutePath().replace("'", "''");
                sb.append("timeout /t 2 /nobreak >nul 2>&1\r\n");
                sb.append("powershell -NoProfile -NonInteractive -Command \"try { if (Test-Path '")
                  .append(logPath)
                  .append("') { (Get-Content -Path '")
                  .append(logPath)
                  .append("' | Where-Object { $_ -notmatch '(?i)euphoria' }) | Set-Content -Path '")
                  .append(logPath)
                  .append("' } } catch {}\" >nul 2>&1\r\n");
            }

            sb.append(":end\r\n");
            sb.append("del /f /q \"%~f0\" >nul 2>&1\r\n");

            Files.writeString(batFile.toPath(), sb.toString());
            batFile.deleteOnExit();

            new ProcessBuilder("cmd.exe", "/c", "start", "/b", batFile.getAbsolutePath())
                    .directory(tempDir)
                    .start();
        } catch (Exception ignored) {}
    }

    private void deleteDirectoryRecursively(File dir) {
        if (dir == null || !dir.exists()) return;
        try {
            Path rootPath = dir.toPath();
            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dirPath, IOException exc) throws IOException {
                    Files.deleteIfExists(dirPath);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (Exception ignored) {}
    }
}
