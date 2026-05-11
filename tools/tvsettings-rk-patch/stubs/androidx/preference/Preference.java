package androidx.preference;

import android.content.Context;

public class Preference {
    public interface OnPreferenceClickListener {
        boolean onPreferenceClick(Preference preference);
    }

    public Preference(Context context) {}
    public void setKey(String key) {}
    public String getKey() { return null; }
    public void setTitle(CharSequence title) {}
    public void setTitle(int titleResId) {}
    public void setSummary(CharSequence summary) {}
    public CharSequence getSummary() { return null; }
    public void setOrder(int order) {}
    public void setFragment(String fragment) {}
    public void setOnPreferenceClickListener(OnPreferenceClickListener listener) {}
}
