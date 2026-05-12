package com.android.tv.settings.device.displaysound;

import android.content.Context;
import android.provider.Settings;

import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.tv.settings.SettingsPreferenceFragment;

public final class HdmiCecMenuPreference {
    private static final String KEY = "x88_hdmi_cec";

    private HdmiCecMenuPreference() {
    }

    public static void add(SettingsPreferenceFragment fragment) {
        PreferenceScreen screen = fragment.getPreferenceScreen();
        Context context = fragment.getContext();
        if (screen == null || context == null || fragment.findPreference(KEY) != null) {
            return;
        }

        Preference pref = new Preference(context);
        pref.setKey(KEY);
        pref.setTitle("HDMI-CEC");
        pref.setSummary(Settings.Global.getInt(context.getContentResolver(),
                "hdmi_control_enabled", 1) != 0
                ? "Device control is enabled"
                : "Device control is disabled");
        pref.setFragment(HdmiCecFragment.class.getName());
        pref.setOrder(3);
        screen.addPreference(pref);
    }
}
