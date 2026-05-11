package com.android.tv.settings.device.displaysound;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;

import com.android.tv.settings.SettingsPreferenceFragment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class RockchipDisplayFragment extends SettingsPreferenceFragment {
    private static final String TAG = "X88RockchipDisplay";

    private static final String[] COLOR_MODES = {
            "Auto",
            "RGB-8bit",
            "YCBCR444-8bit",
            "YCBCR422-8bit",
            "YCBCR420-8bit",
            "YCBCR444-10bit",
            "YCBCR420-10bit"
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final RkOutputManagerClient rk = new RkOutputManagerClient();
    private final Preference.OnPreferenceClickListener clickListener =
            new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    return handlePreferenceClick(preference);
                }
            };
    private PreferenceScreen screen;
    private long display;

    @Override
    protected int getPageId() {
        return 0x15360000;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        Context context = getPreferenceManager().getContext();
        screen = getPreferenceManager().createPreferenceScreen(context);
        screen.setTitle("Rockchip display output");
        setPreferenceScreen(screen);
        refresh();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        if (handlePreferenceClick(preference)) {
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private boolean handlePreferenceClick(Preference preference) {
        String key = preference.getKey();
        if ("display_selector".equals(key)) {
            showDisplayDialog();
            return true;
        }
        if ("rk_resolution".equals(key)) {
            showResolutionDialog();
            return true;
        }
        if ("rk_color".equals(key)) {
            showColorDialog();
            return true;
        }
        if ("rk_brightness".equals(key)) {
            showPictureDialog(0, summaryInt(preference, 50));
            return true;
        }
        if ("rk_contrast".equals(key)) {
            showPictureDialog(1, summaryInt(preference, 50));
            return true;
        }
        if ("rk_saturation".equals(key)) {
            showPictureDialog(2, summaryInt(preference, 50));
            return true;
        }
        if ("rk_hue".equals(key)) {
            showPictureDialog(3, summaryInt(preference, 50));
            return true;
        }
        if ("rk_picture_reset".equals(key)) {
            setPictureValue(0, 50);
            setPictureValue(1, 50);
            setPictureValue(2, 50);
            setPictureValue(3, 50);
            saveAndRefresh("Picture reset");
            return true;
        }
        if ("rk_refresh".equals(key)) {
            refresh();
            return true;
        }
        return false;
    }

    private void refresh() {
        if (screen == null) {
            return;
        }
        screen.removeAll();
        try {
            rk.connect();
            int connectors = Math.max(1, rk.getConnectorCount());
            if (display >= connectors) {
                display = 0;
            }
            addStatus(connectors);
            addDisplaySelector(connectors);
            addResolution();
            addColor();
            addPicture();
            addRefresh();
        } catch (Throwable t) {
            warn("Refresh failed", t);
            addCategory("Status");
            addInfo("Rockchip output manager unavailable", errorText(t));
            addRefresh();
        }
    }

    private void addStatus(int connectors) throws Exception {
        String mode = rk.getCurrentMode(display);
        String color = rk.getCurrentColor(display);
        int[] bcsh = rk.getBcsh(display);
        int[] overscan = rk.getOverscan(display);
        int connectState = rk.getConnectState(display);
        int builtIn = rk.getBuiltIn(display);
        String summary = "HDMI output: " + connectionLabel(connectState)
                + "\nHAL display index: " + display
                + "\nConnector slots reported: " + connectors
                + "\nConnector type: " + builtIn
                + "\nMode: " + mode
                + "\nColor: " + color
                + "\nAndroid resolution: " + getProp("vendor.hwc.resolution_mode", "unknown")
                + "\nHDR state: " + getProp("vendor.hwc.hdr_state", "unknown")
                + "  SurfaceFlinger HDR: " + getProp("ro.surface_flinger.has_HDR_display", "unknown")
                + "\nCurrent mode HDR support: " + rk.getHdrResolutionSupported(display, mode)
                + "\nBCSH: " + Arrays.toString(bcsh)
                + "\nOverscan: " + Arrays.toString(overscan)
                + "\nColor configs: " + Arrays.toString(rk.getColorConfigs(display));
        addCategory("Status");
        addInfo("Current output", summary);
    }

    private void addDisplaySelector(int connectors) {
        if (connectors <= 1) {
            return;
        }
        addCategory("Display");
        Preference pref = pref("display_selector", "Output connector",
                "HAL display index " + display + " (" + safeConnectionLabel(display) + ")");
        screen.addPreference(pref);
    }

    private void addResolution() throws Exception {
        addCategory("Resolution");
        Preference pref = pref("rk_resolution", "Output resolution", rk.getCurrentMode(display));
        screen.addPreference(pref);
    }

    private void addColor() throws Exception {
        addCategory("Color format");
        Preference pref = pref("rk_color", "Output color format", rk.getCurrentColor(display));
        screen.addPreference(pref);

        addInfo("HDR support", "Current state: " + getProp("vendor.hwc.hdr_state", "unknown")
                + "\nHDR capability is reported by the Rockchip HAL per output mode.");
    }

    private void addPicture() throws Exception {
        addCategory("Picture");
        int[] bcsh = rk.getBcsh(display);
        addPicturePreference("rk_brightness", "Brightness", bcsh, 0);
        addPicturePreference("rk_contrast", "Contrast", bcsh, 1);
        addPicturePreference("rk_saturation", "Saturation", bcsh, 2);
        addPicturePreference("rk_hue", "Hue", bcsh, 3);

        Preference reset = pref("rk_picture_reset", "Reset picture values", "Set brightness, contrast, saturation, and hue to 50");
        screen.addPreference(reset);
    }

    private void addPicturePreference(String key, String title, int[] values, final int index) {
        final int value = values.length > index ? values[index] : 50;
        Preference pref = pref(key, title, String.valueOf(value));
        screen.addPreference(pref);
    }

    private void addRefresh() {
        Preference pref = pref("rk_refresh", "Refresh output state", "Reload modes and current Rockchip HAL state");
        screen.addPreference(pref);
    }

    private void showDisplayDialog() {
        try {
            int connectors = Math.max(1, rk.getConnectorCount());
            String[] names = new String[connectors];
            for (int i = 0; i < connectors; i++) {
                names[i] = "Output connector " + i + " (" + safeConnectionLabel(i) + ")";
            }
            new AlertDialog.Builder(context())
                    .setTitle("Active display")
                    .setSingleChoiceItems(names, (int) display, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            display = which;
                            dialog.dismiss();
                            refresh();
                        }
                    })
                    .show();
        } catch (Throwable t) {
            warn("Display list failed", t);
            toast("Display list failed: " + errorText(t));
        }
    }

    private void showResolutionDialog() {
        try {
            final ArrayList<String> modes = new ArrayList<>();
            final ArrayList<String> labels = new ArrayList<>();
            modes.add("Auto");
            labels.add("Auto");
            List<RkOutputManagerClient.Mode> displayModes = rk.getDisplayModes(display);
            for (RkOutputManagerClient.Mode mode : displayModes) {
                modes.add(mode.setName);
                labels.add(mode.label());
            }
            new AlertDialog.Builder(context())
                    .setTitle("Output resolution")
                    .setItems(labels.toArray(new String[0]), new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            applyMode(modes.get(which), which == 0);
                        }
                    })
                    .show();
        } catch (Throwable t) {
            warn("Resolution list failed", t);
            toast("Resolution list failed: " + errorText(t));
        }
    }

    private void showColorDialog() {
        new AlertDialog.Builder(context())
                .setTitle("Output color format")
                .setItems(COLOR_MODES, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        applyColor(COLOR_MODES[which]);
                    }
                })
                .show();
    }

    private void showPictureDialog(final int index, int currentValue) {
        final String[] values = new String[21];
        int checked = Math.max(0, Math.min(20, currentValue / 5));
        for (int i = 0; i < values.length; i++) {
            values[i] = String.valueOf(i * 5);
        }
        new AlertDialog.Builder(context())
                .setTitle(index == 0 ? "Brightness" : index == 1 ? "Contrast" : index == 2 ? "Saturation" : "Hue")
                .setSingleChoiceItems(values, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setPictureValue(index, which * 5);
                        saveAndRefresh("Picture saved");
                        dialog.dismiss();
                    }
                })
                .show();
    }

    private void applyMode(final String mode, boolean persistImmediately) {
        try {
            final String previous = rk.getCurrentMode(display);
            int result = rk.setMode(display, mode);
            if (result != 0) {
                toast("Mode failed: " + result);
                return;
            }
            if (persistImmediately) {
                rk.saveConfig();
                toast("Mode saved");
                refresh();
            } else {
                showModeConfirm(mode, previous);
            }
        } catch (Throwable t) {
            warn("Mode failed", t);
            toast("Mode failed: " + errorText(t));
            refresh();
        }
    }

    private void showModeConfirm(final String mode, final String previous) {
        final int[] remaining = {15};
        final boolean[] handled = {false};
        final AlertDialog dialog = new AlertDialog.Builder(context())
                .setTitle("Keep this resolution?")
                .setMessage("Keeping " + mode + " in " + remaining[0] + " seconds")
                .setPositiveButton("Keep", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        handled[0] = true;
                        try {
                            rk.saveConfig();
                            toast("Mode saved");
                        } catch (Throwable t) {
                            warn("Save mode failed", t);
                            toast("Save failed: " + errorText(t));
                        }
                        refresh();
                    }
                })
                .setNegativeButton("Revert", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        handled[0] = true;
                        revertMode(previous);
                    }
                })
                .create();

        final Runnable tick = new Runnable() {
            @Override
            public void run() {
                if (handled[0]) {
                    return;
                }
                remaining[0]--;
                if (remaining[0] <= 0) {
                    handled[0] = true;
                    dialog.dismiss();
                    revertMode(previous);
                    return;
                }
                dialog.setMessage("Keeping " + mode + " in " + remaining[0] + " seconds");
                handler.postDelayed(this, 1000);
            }
        };
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                handler.removeCallbacks(tick);
                if (!handled[0]) {
                    handled[0] = true;
                    revertMode(previous);
                }
            }
        });
        dialog.show();
        handler.postDelayed(tick, 1000);
    }

    private void revertMode(String previous) {
        try {
            rk.setMode(display, previous);
            rk.saveConfig();
            toast("Mode reverted");
        } catch (Throwable t) {
            warn("Revert mode failed", t);
            toast("Revert failed: " + errorText(t));
        }
        refresh();
    }

    private void applyColor(String mode) {
        try {
            int result = rk.setColorMode(display, mode);
            if (result == 0) {
                rk.saveConfig();
                toast("Color saved");
            } else {
                toast("Color failed: " + result);
            }
        } catch (Throwable t) {
            warn("Color failed", t);
            toast("Color failed: " + errorText(t));
        }
        refresh();
    }

    private void setPictureValue(int index, int value) {
        try {
            if (index == 0) {
                rk.setBrightness(display, value);
            } else if (index == 1) {
                rk.setContrast(display, value);
            } else if (index == 2) {
                rk.setSaturation(display, value);
            } else {
                rk.setHue(display, value);
            }
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private void saveAndRefresh(String message) {
        try {
            rk.saveConfig();
            toast(message);
        } catch (Throwable t) {
            warn("Save failed", t);
            toast("Save failed: " + errorText(t));
        }
        refresh();
    }

    private void addCategory(String title) {
        PreferenceCategory category = new PreferenceCategory(getPreferenceManager().getContext());
        category.setTitle(title);
        screen.addPreference(category);
    }

    private void addInfo(String title, String summary) {
        screen.addPreference(pref("info_" + title, title, summary));
    }

    private Preference pref(String key, String title, String summary) {
        Preference pref = new Preference(getPreferenceManager().getContext());
        pref.setKey(key);
        pref.setTitle(title);
        pref.setSummary(summary);
        pref.setOnPreferenceClickListener(clickListener);
        return pref;
    }

    private Context context() {
        return getPreferenceManager().getContext();
    }

    private int summaryInt(Preference preference, int fallback) {
        try {
            CharSequence summary = preference.getSummary();
            return summary == null ? fallback : Integer.parseInt(summary.toString());
        } catch (Throwable t) {
            return fallback;
        }
    }

    private String getProp(String key, String fallback) {
        try {
            Class<?> cls = Class.forName("android.os.SystemProperties");
            Method method = cls.getMethod("get", String.class, String.class);
            return String.valueOf(method.invoke(null, key, fallback));
        } catch (Throwable t) {
            return fallback;
        }
    }

    private String safeConnectionLabel(long displayIndex) {
        try {
            return connectionLabel(rk.getConnectState(displayIndex));
        } catch (Throwable t) {
            warn("Connection state failed", t);
            return "state unknown";
        }
    }

    private String connectionLabel(int state) {
        if (state == 1) {
            return "connected";
        }
        if (state == 0) {
            return "disconnected";
        }
        return "state " + state;
    }

    private String errorText(Throwable throwable) {
        Throwable root = rootCause(throwable);
        String message = root.getMessage();
        if (message == null || message.length() == 0) {
            return root.getClass().getSimpleName();
        }
        return root.getClass().getSimpleName() + ": " + message;
    }

    private Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof InvocationTargetException
                && ((InvocationTargetException) current).getTargetException() != null) {
            current = ((InvocationTargetException) current).getTargetException();
        }
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
            while (current instanceof InvocationTargetException
                    && ((InvocationTargetException) current).getTargetException() != null) {
                current = ((InvocationTargetException) current).getTargetException();
            }
        }
        return current;
    }

    private void warn(String message, Throwable throwable) {
        Log.e(TAG, message, throwable);
    }

    private void toast(String message) {
        Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
    }
}
