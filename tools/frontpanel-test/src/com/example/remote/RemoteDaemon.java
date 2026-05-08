package com.example.remote;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class RemoteDaemon {
    private static final int EV_KEY = 0x01;
    private static final int KEY_DOWN = 0x01;
    private static final int INPUT_EVENT_SIZE = 24;
    private static final long DEBOUNCE_MS = 900L;

    private final String eventPath;
    private final String logPath;
    private final Map<Integer, String> actions = new HashMap<>();
    private final Map<Integer, Long> lastRun = new HashMap<>();

    private RemoteDaemon(String eventPath, String logPath) {
        this.eventPath = eventPath;
        this.logPath = logPath;
        installDefaults();
    }

    public static void main(String[] args) throws Exception {
        String eventPath = args.length > 0 ? args[0] : "/dev/input/event1";
        String configPath = args.length > 1 ? args[1] : "/system/etc/x88-remote.conf";
        String logPath = args.length > 2 ? args[2] : "/data/local/tmp/x88-remote.log";

        RemoteDaemon daemon = new RemoteDaemon(eventPath, logPath);
        daemon.loadConfig(configPath, false);
        daemon.loadConfig("/data/local/tmp/x88-remote.conf", true);
        daemon.run();
    }

    private void installDefaults() {
        addAction("home", "102,172");
        addAction("settings", "139,141,176");

        // The stock IR keylayout leaves app shortcut keys as generic F/EXPLORER
        // codes. These are logged too, so the mapping can be tightened live.
        addAction("youtube", "150,63,64,67,69,73");
    }

    private void run() throws IOException {
        log("start event=" + eventPath + " actions=" + actions);
        byte[] event = new byte[INPUT_EVENT_SIZE];
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(eventPath))) {
            while (true) {
                readFully(input, event);
                int type = u16(event, 16);
                int code = u16(event, 18);
                int value = s32(event, 20);
                if (type != EV_KEY || value != KEY_DOWN) {
                    continue;
                }
                String action = actions.get(code);
                if (action == null) {
                    log("key code=" + code + " action=none");
                    continue;
                }
                log("key code=" + code + " action=" + action);
                runAction(code, action);
            }
        }
    }

    private void runAction(int code, String action) {
        long now = System.currentTimeMillis();
        Long last = lastRun.get(code);
        if (last != null && now - last.longValue() < DEBOUNCE_MS) {
            return;
        }
        lastRun.put(code, Long.valueOf(now));

        try {
            if ("home".equals(action)) {
                runCommand("/system/bin/am", "start", "--user", "0",
                        "-a", "android.intent.action.MAIN",
                        "-c", "android.intent.category.HOME");
            } else if ("settings".equals(action)) {
                runCommand("/system/bin/am", "start", "--user", "0", "-a", "android.settings.SETTINGS");
            } else if ("youtube".equals(action)) {
                if (runCommand("/system/bin/am", "start", "--user", "0", "-n",
                        "com.google.android.youtube.tv/com.google.android.apps.youtube.tv.activity.ShellActivity") != 0) {
                    launchPackage("com.google.android.youtube.tv");
                }
            } else if (action.startsWith("launch:")) {
                launchPackage(action.substring("launch:".length()));
            } else if (action.startsWith("component:")) {
                launchComponent(action.substring("component:".length()));
            } else if (action.startsWith("keyevent:")) {
                runCommand("/system/bin/input", "keyevent", action.substring("keyevent:".length()));
            } else {
                log("unknown-action=" + action);
            }
        } catch (Exception e) {
            log("action-error code=" + code + " action=" + action + " error=" + e);
        }
    }

    private void launchPackage(String packageName) throws IOException, InterruptedException {
        if (packageName == null || packageName.trim().isEmpty()) {
            log("empty-launch-package");
            return;
        }
        packageName = packageName.trim();
        int result = runCommand("/system/bin/am", "start", "--user", "0",
                "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.LEANBACK_LAUNCHER",
                "-p", packageName);
        if (result != 0) {
            runCommand("/system/bin/am", "start", "--user", "0",
                    "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.LAUNCHER",
                    "-p", packageName);
        }
    }

    private void launchComponent(String componentName) throws IOException, InterruptedException {
        if (componentName == null || componentName.trim().isEmpty()) {
            log("empty-launch-component");
            return;
        }
        runCommand("/system/bin/am", "start", "--user", "0", "-n", componentName.trim());
    }

    private int runCommand(String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean exited = process.waitFor(5, TimeUnit.SECONDS);
        if (!exited) {
            process.destroy();
            log("command-timeout=" + String.join(" ", command));
            return -1;
        }
        log("command-exit=" + process.exitValue() + " cmd=" + String.join(" ", command));
        return process.exitValue();
    }

    private void loadConfig(String path, boolean optional) {
        File file = new File(path);
        if (!file.isFile()) {
            if (!optional) {
                log("missing-config=" + path + " using-defaults");
            }
            return;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            Set<String> cleared = new HashSet<>();
            while ((line = reader.readLine()) != null) {
                line = stripComment(line).trim();
                if (line.isEmpty()) {
                    continue;
                }
                int split = line.indexOf('=');
                if (split < 1) {
                    log("bad-config-line=" + line);
                    continue;
                }
                String action = line.substring(0, split).trim();
                String codes = line.substring(split + 1).trim();
                if (!cleared.contains(action)) {
                    removeAction(action);
                    cleared.add(action);
                }
                addAction(action, codes);
            }
            log("loaded-config=" + path + " actions=" + actions);
        } catch (Exception e) {
            log("config-error=" + path + " error=" + e);
        }
    }

    private void addAction(String action, String codes) {
        for (String token : codes.split(",")) {
            token = token.trim();
            if (token.isEmpty()) {
                continue;
            }
            try {
                actions.put(Integer.decode(token), action);
            } catch (NumberFormatException e) {
                log("bad-code action=" + action + " code=" + token);
            }
        }
    }

    private void removeAction(String action) {
        Iterator<Map.Entry<Integer, String>> iterator = actions.entrySet().iterator();
        while (iterator.hasNext()) {
            if (action.equals(iterator.next().getValue())) {
                iterator.remove();
            }
        }
    }

    private static void readFully(BufferedInputStream input, byte[] buffer) throws IOException {
        int offset = 0;
        while (offset < buffer.length) {
            int read = input.read(buffer, offset, buffer.length - offset);
            if (read < 0) {
                throw new IOException("EOF");
            }
            offset += read;
        }
    }

    private static int u16(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff) | ((bytes[offset + 1] & 0xff) << 8);
    }

    private static int s32(byte[] bytes, int offset) {
        return (bytes[offset] & 0xff)
                | ((bytes[offset + 1] & 0xff) << 8)
                | ((bytes[offset + 2] & 0xff) << 16)
                | (bytes[offset + 3] << 24);
    }

    private static String stripComment(String line) {
        int comment = line.indexOf('#');
        return comment >= 0 ? line.substring(0, comment) : line;
    }

    private void log(String message) {
        String line = Instant.now() + " x88-remote: " + message + "\n";
        System.out.print(line);
        try (FileWriter writer = new FileWriter(logPath, true)) {
            writer.write(line);
        } catch (IOException ignored) {
        }
    }
}
