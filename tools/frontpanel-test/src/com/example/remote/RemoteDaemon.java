package com.example.remote;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
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
    private static final long MOUSE_REPEAT_MS = 65L;
    private static final int MOUSE_STEP = 28;

    private final String eventSpec;
    private final String logPath;
    private final Map<Integer, String> actions = new HashMap<>();
    private final Map<Integer, Long> lastRun = new HashMap<>();
    private final VirtualMouse virtualMouse = new VirtualMouse();
    private boolean mouseMode;

    private RemoteDaemon(String eventSpec, String logPath) {
        this.eventSpec = eventSpec;
        this.logPath = logPath;
        installDefaults();
    }

    public static void main(String[] args) throws Exception {
        String eventPath = args.length > 0 ? args[0] : "auto:ffa90030.pwm";
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
        addAction("mouse_toggle", "388");
        addAction("mouse_up", "3");
        addAction("mouse_left", "5");
        addAction("mouse_click", "6,232");
        addAction("mouse_right", "7");
        addAction("mouse_down", "9");
    }

    private void run() throws Exception {
        byte[] event = new byte[INPUT_EVENT_SIZE];
        while (true) {
            String eventPath = resolveEventPath(eventSpec);
            log("start eventSpec=" + eventSpec + " event=" + eventPath + " actions=" + actions);
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
                    if (handleMouseAction(code, action)) {
                        continue;
                    }
                    runAction(code, action);
                }
            } catch (IOException e) {
                log("event-error event=" + eventPath + " error=" + e + " retrying");
                sleep(1000L);
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
                    if (!launchPackage("com.google.android.youtube.tv")) {
                        launchPackage("org.smarttube.stable");
                    }
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

    private boolean launchPackage(String packageName) throws IOException, InterruptedException {
        if (packageName == null || packageName.trim().isEmpty()) {
            log("empty-launch-package");
            return false;
        }
        packageName = packageName.trim();
        int result = runCommand("/system/bin/am", "start", "--user", "0",
                "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.LEANBACK_LAUNCHER",
                "-p", packageName);
        if (result != 0) {
            result = runCommand("/system/bin/am", "start", "--user", "0",
                    "-a", "android.intent.action.MAIN",
                    "-c", "android.intent.category.LAUNCHER",
                    "-p", packageName);
        }
        return result == 0;
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

    private boolean handleMouseAction(int code, String action) throws Exception {
        if ("mouse_toggle".equals(action)) {
            if (debounced(code, DEBOUNCE_MS)) {
                return true;
            }
            mouseMode = !mouseMode;
            if (mouseMode) {
                virtualMouse.ensureReady();
                virtualMouse.move(0, 0);
            }
            log("mouse-mode=" + mouseMode + " device=uhid");
            return true;
        }
        if (!action.startsWith("mouse_")) {
            return false;
        }
        if (!mouseMode) {
            runMouseModeKeyAsNormalKey(code);
            return true;
        }
        if (debounced(code, MOUSE_REPEAT_MS)) {
            return true;
        }
        if ("mouse_up".equals(action)) {
            virtualMouse.move(0, -MOUSE_STEP);
            log("mouse-move dx=0 dy=" + -MOUSE_STEP);
        } else if ("mouse_down".equals(action)) {
            virtualMouse.move(0, MOUSE_STEP);
            log("mouse-move dx=0 dy=" + MOUSE_STEP);
        } else if ("mouse_left".equals(action)) {
            virtualMouse.move(-MOUSE_STEP, 0);
            log("mouse-move dx=" + -MOUSE_STEP + " dy=0");
        } else if ("mouse_right".equals(action)) {
            virtualMouse.move(MOUSE_STEP, 0);
            log("mouse-move dx=" + MOUSE_STEP + " dy=0");
        } else if ("mouse_click".equals(action)) {
            virtualMouse.click();
            log("mouse-click");
        }
        return true;
    }

    private void runMouseModeKeyAsNormalKey(int code) throws IOException, InterruptedException {
        String keyevent = null;
        if (code == 3) {
            keyevent = "9";  // KEYCODE_2
        } else if (code == 5) {
            keyevent = "11"; // KEYCODE_4
        } else if (code == 6) {
            keyevent = "12"; // KEYCODE_5
        } else if (code == 7) {
            keyevent = "13"; // KEYCODE_6
        } else if (code == 9) {
            keyevent = "15"; // KEYCODE_8
        } else if (code == 232) {
            keyevent = "23"; // KEYCODE_DPAD_CENTER
        }
        if (keyevent != null) {
            runCommand("/system/bin/input", "keyevent", keyevent);
        }
    }

    private boolean debounced(int code, long windowMs) {
        long now = System.currentTimeMillis();
        Long last = lastRun.get(code);
        if (last != null && now - last.longValue() < windowMs) {
            return true;
        }
        lastRun.put(code, Long.valueOf(now));
        return false;
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

    private static final class VirtualMouse {
        private static final int UHID_CREATE2 = 11;
        private static final int UHID_INPUT2 = 12;
        private static final int BUS_USB = 0x03;
        private static final byte[] MOUSE_DESCRIPTOR = new byte[] {
                0x05, 0x01,       // Usage Page (Generic Desktop)
                0x09, 0x02,       // Usage (Mouse)
                (byte) 0xA1, 0x01, // Collection (Application)
                0x09, 0x01,       // Usage (Pointer)
                (byte) 0xA1, 0x00, // Collection (Physical)
                0x05, 0x09,       // Usage Page (Buttons)
                0x19, 0x01,       // Usage Minimum (1)
                0x29, 0x03,       // Usage Maximum (3)
                0x15, 0x00,       // Logical Minimum (0)
                0x25, 0x01,       // Logical Maximum (1)
                (byte) 0x95, 0x03, // Report Count (3)
                0x75, 0x01,       // Report Size (1)
                (byte) 0x81, 0x02, // Input (Data,Var,Abs)
                (byte) 0x95, 0x01, // Report Count (1)
                0x75, 0x05,       // Report Size (5)
                (byte) 0x81, 0x01, // Input (Const,Array,Abs)
                0x05, 0x01,       // Usage Page (Generic Desktop)
                0x09, 0x30,       // Usage (X)
                0x09, 0x31,       // Usage (Y)
                0x15, (byte) 0x81, // Logical Minimum (-127)
                0x25, 0x7F,       // Logical Maximum (127)
                0x75, 0x08,       // Report Size (8)
                (byte) 0x95, 0x02, // Report Count (2)
                (byte) 0x81, 0x06, // Input (Data,Var,Rel)
                (byte) 0xC0,
                (byte) 0xC0
        };

        private FileOutputStream output;

        void ensureReady() throws IOException {
            if (output != null) {
                return;
            }
            output = new FileOutputStream("/dev/uhid");
            ByteBuffer event = ByteBuffer.allocate(4 + 4372).order(ByteOrder.LITTLE_ENDIAN);
            event.putInt(UHID_CREATE2);
            putFixedAscii(event, "X88 IR Mouse", 128);
            putFixedAscii(event, "x88-ir-remote/input0", 64);
            putFixedAscii(event, "x88-ir-mouse", 64);
            event.putShort((short) MOUSE_DESCRIPTOR.length);
            event.putShort((short) BUS_USB);
            event.putInt(0x2207);
            event.putInt(0x8888);
            event.putInt(1);
            event.putInt(0);
            event.put(MOUSE_DESCRIPTOR);
            writeEvent(event);
        }

        void move(int dx, int dy) throws IOException {
            ensureReady();
            sendReport(0, clampByte(dx), clampByte(dy));
        }

        void click() throws IOException {
            ensureReady();
            sendReport(1, 0, 0);
            try {
                Thread.sleep(40L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            sendReport(0, 0, 0);
        }

        private void sendReport(int buttons, int dx, int dy) throws IOException {
            ByteBuffer event = ByteBuffer.allocate(4 + 2 + 3).order(ByteOrder.LITTLE_ENDIAN);
            event.putInt(UHID_INPUT2);
            event.putShort((short) 3);
            event.put((byte) buttons);
            event.put((byte) dx);
            event.put((byte) dy);
            writeEvent(event);
        }

        private void writeEvent(ByteBuffer event) throws IOException {
            output.write(event.array(), 0, event.position());
            output.flush();
        }

        private static int clampByte(int value) {
            return Math.max(-127, Math.min(127, value));
        }

        private static void putFixedAscii(ByteBuffer buffer, String value, int size) {
            byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
            int length = Math.min(bytes.length, size);
            buffer.put(bytes, 0, length);
            for (int i = length; i < size; i++) {
                buffer.put((byte) 0);
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

    private String resolveEventPath(String requested) throws IOException {
        if (requested == null || requested.trim().isEmpty() || "auto".equals(requested.trim())) {
            return findInputEvent(new HashSet<>(Arrays.asList("ffa90030.pwm")));
        }
        requested = requested.trim();
        if (requested.startsWith("auto:")) {
            Set<String> names = new HashSet<>();
            for (String token : requested.substring("auto:".length()).split(",")) {
                token = token.trim();
                if (!token.isEmpty()) {
                    names.add(token);
                }
            }
            if (names.isEmpty()) {
                names.add("ffa90030.pwm");
            }
            return findInputEvent(names);
        }
        return requested;
    }

    private String findInputEvent(Set<String> names) throws IOException {
        String currentName = null;
        String currentHandlers = null;
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/bus/input/devices"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("N: Name=")) {
                    currentName = parseQuotedName(line);
                } else if (line.startsWith("H: Handlers=")) {
                    currentHandlers = line.substring("H: Handlers=".length());
                } else if (line.trim().isEmpty()) {
                    String path = eventPathIfMatch(names, currentName, currentHandlers);
                    if (path != null) {
                        return path;
                    }
                    currentName = null;
                    currentHandlers = null;
                }
            }
        }
        String path = eventPathIfMatch(names, currentName, currentHandlers);
        if (path != null) {
            return path;
        }
        log("auto-event-not-found names=" + names + " fallback=/dev/input/event1");
        return "/dev/input/event1";
    }

    private static String parseQuotedName(String line) {
        int start = line.indexOf('"');
        int end = line.lastIndexOf('"');
        return start >= 0 && end > start ? line.substring(start + 1, end) : line;
    }

    private static String eventPathIfMatch(Set<String> names, String name, String handlers) {
        if (name == null || handlers == null || !names.contains(name)) {
            return null;
        }
        for (String token : handlers.split("\\s+")) {
            if (token.startsWith("event")) {
                return "/dev/input/" + token;
            }
        }
        return null;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
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
