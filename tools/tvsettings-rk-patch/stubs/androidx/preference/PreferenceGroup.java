package androidx.preference;

import android.content.Context;

public class PreferenceGroup extends Preference {
    public PreferenceGroup(Context context) { super(context); }
    public boolean addPreference(Preference preference) { return true; }
    public void removeAll() {}
}
