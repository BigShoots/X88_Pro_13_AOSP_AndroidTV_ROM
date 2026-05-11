package androidx.preference;

import android.content.Context;

public class TwoStatePreference extends Preference {
    public TwoStatePreference(Context context) { super(context); }
    public void setChecked(boolean checked) {}
    public boolean isChecked() { return false; }
}
