package com.android.tv.settings.device;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.tv.settings.SettingsPreferenceFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Method;

public final class CpuPerformancePreference {
    private static final String TAG = "X88CpuPerformance";
    private static final String KEY = "x88_cpu_max_lock";
    private static final String PROP = "persist.x88.cpu_max_lock";
    private static final String POLICY = "/sys/devices/system/cpu/cpufreq/policy0";
    private static final String DEFAULT_GOVERNOR = "interactive";

    private CpuPerformancePreference() {
    }

    public static void add(final SettingsPreferenceFragment fragment) {
        PreferenceScreen screen = fragment.getPreferenceScreen();
        Context context = fragment.getContext();
        if (screen == null || context == null || fragment.findPreference(KEY) != null) {
            return;
        }

        PreferenceCategory category = new PreferenceCategory(context);
        category.setTitle("Performance");
        category.setOrder(9000);
        screen.addPreference(category);

        SwitchPreference pref = new SwitchPreference(context);
        pref.setKey(KEY);
        pref.setTitle("Lock CPU at max speed");
        pref.setOrder(9001);
        boolean enabled = isEnabled();
        if (enabled) {
            apply(true);
        }
        pref.setChecked(enabled);
        setSummary(pref, enabled);
        pref.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                boolean enabled = Boolean.TRUE.equals(newValue);
                boolean applied = apply(enabled);
                if (applied) {
                    setProperty(PROP, enabled ? "1" : "0");
                }
                setSummary(preference, applied ? enabled : isEnabled());
                Toast.makeText(fragment.getContext(),
                        applied ? (enabled ? "CPU locked at max speed" : "CPU scaling restored")
                                : "CPU setting could not be applied",
                        Toast.LENGTH_SHORT).show();
                return applied;
            }
        });
        screen.addPreference(pref);
    }

    private static boolean isEnabled() {
        return "1".equals(getProperty(PROP, "0"));
    }

    private static void setSummary(Preference pref, boolean enabled) {
        String max = read(POLICY + "/cpuinfo_max_freq", "unknown");
        String cur = read(POLICY + "/scaling_cur_freq", "unknown");
        String temp = read("/sys/class/thermal/thermal_zone0/temp", "");
        pref.setSummary((enabled ? "Enabled" : "Disabled")
                + "  Current: " + mhz(cur)
                + "  Max: " + mhz(max)
                + (temp.length() > 0 ? "  Temp: " + celsius(temp) : ""));
    }

    private static boolean apply(boolean enabled) {
        String min = read(POLICY + "/cpuinfo_min_freq", "408000");
        String max = read(POLICY + "/cpuinfo_max_freq", "2016000");
        boolean ok = true;
        if (enabled) {
            ok &= write(POLICY + "/scaling_max_freq", max);
            ok &= write(POLICY + "/scaling_min_freq", max);
            ok &= write(POLICY + "/scaling_governor", "performance");
        } else {
            ok &= write(POLICY + "/scaling_min_freq", min);
            ok &= write(POLICY + "/scaling_max_freq", max);
            ok &= write(POLICY + "/scaling_governor", DEFAULT_GOVERNOR);
        }
        return ok;
    }

    private static String mhz(String khz) {
        try {
            return String.valueOf(Integer.parseInt(khz.trim()) / 1000) + " MHz";
        } catch (Throwable t) {
            return khz;
        }
    }

    private static String celsius(String milliC) {
        try {
            return String.valueOf(Integer.parseInt(milliC.trim()) / 1000) + " C";
        } catch (Throwable t) {
            return milliC;
        }
    }

    private static String read(String path, String fallback) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new FileReader(new File(path)));
            String line = reader.readLine();
            return line == null ? fallback : line.trim();
        } catch (Throwable t) {
            return fallback;
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean write(String path, String value) {
        FileWriter writer = null;
        try {
            writer = new FileWriter(new File(path));
            writer.write(value);
            writer.write("\n");
            return true;
        } catch (Throwable t) {
            Log.e(TAG, "Write failed: " + path + "=" + value, t);
            return false;
        } finally {
            if (writer != null) {
                try {
                    writer.close();
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static String getProperty(String key, String fallback) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method method = cls.getMethod("get", String.class, String.class);
            return String.valueOf(method.invoke(null, key, fallback));
        } catch (Throwable t) {
            return fallback;
        }
    }

    private static void setProperty(String key, String value) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method method = cls.getMethod("set", String.class, String.class);
            method.invoke(null, key, value);
        } catch (Throwable t) {
            Log.e(TAG, "Property write failed: " + key + "=" + value, t);
        }
    }
}
