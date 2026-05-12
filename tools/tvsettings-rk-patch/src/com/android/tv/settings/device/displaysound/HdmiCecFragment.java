package com.android.tv.settings.device.displaysound;

import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreference;

import com.android.tv.settings.SettingsPreferenceFragment;

import java.lang.reflect.Method;

public final class HdmiCecFragment extends SettingsPreferenceFragment {
    private static final String TAG = "X88HdmiCec";

    private static final String KEY_HDMI_CONTROL = "x88_cec_hdmi_control";
    private static final String KEY_WAKE_TV = "x88_cec_wake_tv";
    private static final String KEY_STANDBY_TV = "x88_cec_standby_tv";
    private static final String KEY_ROUTING = "x88_cec_routing";
    private static final String KEY_SYSTEM_AUDIO = "x88_cec_system_audio";
    private static final String KEY_VOLUME = "x88_cec_volume";
    private static final String KEY_MENU_LANGUAGE = "x88_cec_menu_language";
    private static final String KEY_RECOMMENDED = "x88_cec_recommended";
    private static final String KEY_REFRESH = "x88_cec_refresh";

    private PreferenceScreen screen;
    private Object hdmiManager;

    private final Preference.OnPreferenceChangeListener changeListener =
            new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    boolean enabled = Boolean.TRUE.equals(newValue);
                    boolean ok = apply(preference.getKey(), enabled);
                    preference.setSummary(switchSummary(preference.getKey(), enabled));
                    toast(ok ? "CEC setting saved" : "CEC setting saved to Android settings only");
                    return true;
                }
            };

    @Override
    protected int getPageId() {
        return 0x15360001;
    }

    @Override
    public void onCreatePreferences(Bundle bundle, String rootKey) {
        Context context = getPreferenceManager().getContext();
        screen = getPreferenceManager().createPreferenceScreen(context);
        screen.setTitle("HDMI-CEC");
        setPreferenceScreen(screen);
        hdmiManager = context.getSystemService("hdmi_control");
        refresh();
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        String key = preference.getKey();
        if (KEY_RECOMMENDED.equals(key)) {
            applyRecommended();
            toast("Recommended CEC defaults applied");
            refresh();
            return true;
        }
        if (KEY_REFRESH.equals(key)) {
            refresh();
            return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private void refresh() {
        if (screen == null) {
            return;
        }
        screen.removeAll();

        addCategory("Status");
        addInfo("Current state", statusSummary());

        addCategory("Device control");
        addSwitch(KEY_HDMI_CONTROL, "HDMI control", readHdmiControl(),
                "Allow this box and the TV to send HDMI-CEC commands.");
        addSwitch(KEY_WAKE_TV, "TV auto power on", readWakeTv(),
                "Wake the TV when this box becomes active.");
        addSwitch(KEY_STANDBY_TV, "TV standby on sleep", readStandbyTv(),
                "Send standby to the TV when this box sleeps.");
        addSwitch(KEY_ROUTING, "Routing control", readGlobal("routing_control", true),
                "Let CEC active-source and routing messages follow the selected HDMI source.");

        addCategory("Audio control");
        addSwitch(KEY_SYSTEM_AUDIO, "System audio control", readGlobal("system_audio_control", true),
                "Allow HDMI-CEC system audio mode commands.");
        addSwitch(KEY_VOLUME, "CEC volume control", readGlobal("volume_control_enabled", true),
                "Forward volume keys over HDMI-CEC when supported.");

        addCategory("Other");
        addSwitch(KEY_MENU_LANGUAGE, "Set menu language", readGlobal("set_menu_language", true),
                "Send Android's menu language over HDMI-CEC.");
        addAction(KEY_RECOMMENDED, "Apply recommended defaults",
                "Enable the CEC settings expected for an Android TV playback device.");
        addAction(KEY_REFRESH, "Refresh CEC state", "Reload current Android and CEC service values.");
    }

    private void addCategory(String title) {
        PreferenceCategory category = new PreferenceCategory(context());
        category.setTitle(title);
        screen.addPreference(category);
    }

    private void addInfo(String title, String summary) {
        Preference pref = new Preference(context());
        pref.setTitle(title);
        pref.setSummary(summary);
        screen.addPreference(pref);
    }

    private void addAction(String key, String title, String summary) {
        Preference pref = new Preference(context());
        pref.setKey(key);
        pref.setTitle(title);
        pref.setSummary(summary);
        screen.addPreference(pref);
    }

    private void addSwitch(String key, String title, boolean enabled, String summary) {
        SwitchPreference pref = new SwitchPreference(context());
        pref.setKey(key);
        pref.setTitle(title);
        pref.setChecked(enabled);
        pref.setSummary(summary + "\n" + stateSummary(enabled));
        pref.setOnPreferenceChangeListener(changeListener);
        screen.addPreference(pref);
    }

    private boolean apply(String key, boolean enabled) {
        if (KEY_HDMI_CONTROL.equals(key)) {
            boolean ok = callManagerSetter("setHdmiCecEnabled", enabled);
            putGlobal("hdmi_control_enabled", enabled);
            putGlobal("hdmi_cec_enabled", enabled);
            return ok;
        }
        if (KEY_WAKE_TV.equals(key)) {
            boolean ok = callManagerSetter("setTvWakeOnOneTouchPlay", enabled);
            putGlobal("tv_wake_on_one_touch_play", enabled);
            putGlobal("hdmi_control_auto_wakeup_enabled", enabled);
            return ok;
        }
        if (KEY_STANDBY_TV.equals(key)) {
            boolean ok = callManagerSetter("setTvSendStandbyOnSleep", enabled);
            putGlobal("tv_send_standby_on_sleep", enabled);
            putGlobal("hdmi_control_auto_device_off_enabled", enabled);
            return ok;
        }
        if (KEY_ROUTING.equals(key)) {
            putGlobal("routing_control", enabled);
            return true;
        }
        if (KEY_SYSTEM_AUDIO.equals(key)) {
            putGlobal("system_audio_control", enabled);
            putGlobal("hdmi_system_audio_control_enabled", enabled);
            return true;
        }
        if (KEY_VOLUME.equals(key)) {
            putGlobal("volume_control_enabled", enabled);
            putGlobal("hdmi_cec_volume_control_enabled", enabled);
            putGlobal("hdmi_control_volume_control_enabled", enabled);
            return true;
        }
        if (KEY_MENU_LANGUAGE.equals(key)) {
            putGlobal("set_menu_language", enabled);
            return true;
        }
        return false;
    }

    private void applyRecommended() {
        apply(KEY_HDMI_CONTROL, true);
        apply(KEY_WAKE_TV, true);
        apply(KEY_STANDBY_TV, true);
        apply(KEY_ROUTING, true);
        apply(KEY_SYSTEM_AUDIO, true);
        apply(KEY_VOLUME, true);
        apply(KEY_MENU_LANGUAGE, true);
        Settings.Global.putInt(resolver(), "hdmi_cec_version", 6);
        Settings.Global.putString(resolver(), "power_control_mode", "to_tv");
        Settings.Global.putString(resolver(), "power_state_change_on_active_source_lost", "none");
    }

    private String statusSummary() {
        return "CEC service: " + (hdmiManager != null ? "available" : "not available")
                + "\nHDMI control: " + label(readHdmiControl())
                + "\nTV auto power on: " + label(readWakeTv())
                + "\nTV standby on sleep: " + label(readStandbyTv())
                + "\nRouting control: " + label(readGlobal("routing_control", true))
                + "\nSystem audio control: " + label(readGlobal("system_audio_control", true))
                + "\nCEC volume control: " + label(readGlobal("volume_control_enabled", true));
    }

    private boolean readHdmiControl() {
        int value = callManagerGetter("getHdmiCecEnabled");
        return value >= 0 ? value == 1 : readGlobal("hdmi_control_enabled", true);
    }

    private boolean readWakeTv() {
        int value = callManagerGetter("getTvWakeOnOneTouchPlay");
        return value >= 0 ? value == 1 : readGlobal("tv_wake_on_one_touch_play", true);
    }

    private boolean readStandbyTv() {
        int value = callManagerGetter("getTvSendStandbyOnSleep");
        return value >= 0 ? value == 1 : readGlobal("tv_send_standby_on_sleep", true);
    }

    private int callManagerGetter(String methodName) {
        try {
            Object manager = manager();
            if (manager == null) {
                return -1;
            }
            Method method = manager.getClass().getMethod(methodName);
            return ((Integer) method.invoke(manager)).intValue();
        } catch (Throwable t) {
            Log.w(TAG, "CEC getter failed: " + methodName, t);
            return -1;
        }
    }

    private boolean callManagerSetter(String methodName, boolean enabled) {
        try {
            Object manager = manager();
            if (manager == null) {
                return false;
            }
            Method method = manager.getClass().getMethod(methodName, Integer.TYPE);
            method.invoke(manager, Integer.valueOf(enabled ? 1 : 0));
            return true;
        } catch (Throwable t) {
            Log.w(TAG, "CEC setter failed: " + methodName, t);
            return false;
        }
    }

    private Object manager() {
        if (hdmiManager == null) {
            hdmiManager = context().getSystemService("hdmi_control");
        }
        return hdmiManager;
    }

    private boolean readGlobal(String key, boolean fallback) {
        return Settings.Global.getInt(resolver(), key, fallback ? 1 : 0) != 0;
    }

    private void putGlobal(String key, boolean enabled) {
        Settings.Global.putInt(resolver(), key, enabled ? 1 : 0);
    }

    private android.content.ContentResolver resolver() {
        return context().getContentResolver();
    }

    private Context context() {
        Context context = getContext();
        return context != null ? context : getPreferenceManager().getContext();
    }

    private String stateSummary(boolean enabled) {
        return "Current: " + label(enabled);
    }

    private String switchSummary(String key, boolean enabled) {
        return descriptionForKey(key) + "\n" + stateSummary(enabled);
    }

    private String descriptionForKey(String key) {
        if (KEY_HDMI_CONTROL.equals(key)) {
            return "Allow this box and the TV to send HDMI-CEC commands.";
        }
        if (KEY_WAKE_TV.equals(key)) {
            return "Wake the TV when this box becomes active.";
        }
        if (KEY_STANDBY_TV.equals(key)) {
            return "Send standby to the TV when this box sleeps.";
        }
        if (KEY_ROUTING.equals(key)) {
            return "Let CEC active-source and routing messages follow the selected HDMI source.";
        }
        if (KEY_SYSTEM_AUDIO.equals(key)) {
            return "Allow HDMI-CEC system audio mode commands.";
        }
        if (KEY_VOLUME.equals(key)) {
            return "Forward volume keys over HDMI-CEC when supported.";
        }
        if (KEY_MENU_LANGUAGE.equals(key)) {
            return "Send Android's menu language over HDMI-CEC.";
        }
        return "";
    }

    private String label(boolean enabled) {
        return enabled ? "Enabled" : "Disabled";
    }

    private void toast(String text) {
        Toast.makeText(context(), text, Toast.LENGTH_SHORT).show();
    }
}
