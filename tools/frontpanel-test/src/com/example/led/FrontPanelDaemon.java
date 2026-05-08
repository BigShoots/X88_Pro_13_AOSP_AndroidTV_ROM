package com.example.led;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

public final class FrontPanelDaemon {
    private static final DateTimeFormatter TIME_24_FORMAT = DateTimeFormatter.ofPattern("HHmm");
    private static final DateTimeFormatter TIME_12_FORMAT = DateTimeFormatter.ofPattern("hhmm");

    private FrontPanelDaemon() {
    }

    public static void main(String[] args) throws Exception {
        String lib = args.length > 0 ? args[0] : "/system/lib64/libled.so";
        long intervalMs = args.length > 1 ? Long.parseLong(args[1]) : 15000L;
        System.load(lib);

        LedUtils led = new LedUtils();
        log("device_open=" + led.device_open());
        led.LED_Enable();

        String lastTime = "";
        while (true) {
            try {
                led.LED_Enable();
                led.LED_Pwr_Display();
                led.LED_Colon_Display();

                String currentTime = formatAndroidTime();
                if (!currentTime.equals(lastTime)) {
                    led.LedShowString(currentTime);
                    lastTime = currentTime;
                    log("time=" + currentTime);
                }

                updateLan(led);
                updateWifi(led);
            } catch (Throwable t) {
                log("error=" + t);
            }
            Thread.sleep(intervalMs);
        }
    }

    private static String formatAndroidTime() {
        DateTimeFormatter formatter = use24HourClock() ? TIME_24_FORMAT : TIME_12_FORMAT;
        return ZonedDateTime.now(readAndroidZone()).format(formatter);
    }

    private static ZoneId readAndroidZone() {
        String zone = runCommand("getprop", "persist.sys.timezone");
        if (zone.isEmpty()) {
            return ZoneId.systemDefault();
        }
        try {
            return ZoneId.of(zone);
        } catch (DateTimeException ignored) {
            return ZoneId.systemDefault();
        }
    }

    private static boolean use24HourClock() {
        String setting = runCommand("settings", "get", "system", "time_12_24");
        if ("24".equals(setting)) {
            return true;
        }
        if ("12".equals(setting)) {
            return false;
        }
        return localeDefaultsTo24Hour();
    }

    private static boolean localeDefaultsTo24Hour() {
        String localeTag = runCommand("getprop", "persist.sys.locale");
        if (localeTag.isEmpty()) {
            localeTag = runCommand("getprop", "ro.product.locale");
        }
        if (localeTag.isEmpty()) {
            return false;
        }
        Locale locale = Locale.forLanguageTag(localeTag.replace('_', '-'));
        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"), locale);
        calendar.set(2026, Calendar.JANUARY, 1, 13, 0, 0);
        DateFormat dateFormat = DateFormat.getTimeInstance(DateFormat.SHORT, locale);
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String formatted = dateFormat.format(calendar.getTime());
        return formatted.contains("13");
    }

    private static void updateLan(LedUtils led) {
        if (isInterfaceUp("eth0") || "1".equals(readTrimmed("/sys/class/net/eth0/carrier"))) {
            led.LED_Lan_Display();
        } else {
            led.LED_Lan_Off();
        }
    }

    private static void updateWifi(LedUtils led) {
        int quality = readWifiQuality();
        if (quality >= 45) {
            led.LED_Wifi_Fine_Display();
        } else if (quality > 0 || isAnyWlanUp()) {
            led.LED_Wifi_Low_Display();
        } else {
            led.LED_Wifi_Off();
        }
    }

    private static boolean isAnyWlanUp() {
        File net = new File("/sys/class/net");
        File[] children = net.listFiles();
        if (children == null) {
            return false;
        }
        for (File child : children) {
            if (child.getName().startsWith("wlan") && isInterfaceUp(child.getName())) {
                return true;
            }
        }
        return false;
    }

    private static boolean isInterfaceUp(String iface) {
        String state = readTrimmed("/sys/class/net/" + iface + "/operstate");
        return "up".equals(state) || "unknown".equals(state);
    }

    private static int readWifiQuality() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/net/wireless"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("wlan")) {
                    continue;
                }
                String[] parts = line.split("\\s+");
                if (parts.length > 2) {
                    return (int) Float.parseFloat(parts[2].replace(".", ""));
                }
            }
        } catch (Exception ignored) {
        }
        return -1;
    }

    private static String readTrimmed(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            return reader.readLine().trim();
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String runCommand(String... command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line = reader.readLine();
                if (line != null) {
                    output.append(line.trim());
                }
            }
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return "";
            }
            String value = output.toString();
            return "null".equals(value) ? "" : value;
        } catch (Exception ignored) {
            return "";
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    private static void log(String message) {
        System.out.println("x88-frontpanel: " + message);
    }
}
