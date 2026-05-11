package com.android.tv.settings;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

public abstract class SettingsPreferenceFragment {
    protected int getPageId() { return 0; }
    public Context getContext() { return null; }
    public Activity getActivity() { return null; }
    public PreferenceManager getPreferenceManager() { return null; }
    public void setPreferenceScreen(PreferenceScreen screen) {}
    public PreferenceScreen getPreferenceScreen() { return null; }
    public Preference findPreference(CharSequence key) { return null; }
    public void onCreatePreferences(Bundle bundle, String rootKey) {}
    public boolean onPreferenceTreeClick(Preference preference) { return false; }
}
