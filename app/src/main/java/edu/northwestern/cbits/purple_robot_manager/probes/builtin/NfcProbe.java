package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

public class NfcProbe extends Probe
{

    private static final String ENABLED = "config_probe_nfc_enabled";
    private static final boolean DEFAULT_ENABLED = true;

    @Override
    public String getPreferenceKey() {
        return "built_in_nfc";
    }

    @Override
    public String name(Context context)
    {
        return "edu.northwestern.cbits.purple_robot_manager.probes.builtin.NfcProbe";
    }

    @Override
    public String title(Context context)
    {
        return context.getString(R.string.title_nfc_probe);
    }

    @Override
    public String probeCategory(Context context)
    {
        return context.getResources().getString(R.string.probe_sensor_category);
    }

    @SuppressWarnings("SimplifiableIfStatement")
    @Override
    public boolean isEnabled(final Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        if (super.isEnabled(context))
        {
            return prefs.getBoolean(NfcProbe.ENABLED, NfcProbe.DEFAULT_ENABLED);
        }

        return false;
    }

    @SuppressWarnings("deprecation")
    @Override
    public PreferenceScreen preferenceScreen(final Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = super.preferenceScreen(context, manager);

        screen.setTitle(this.title(context));
        screen.setSummary(R.string.summary_nfc_probe_desc);

        CheckBoxPreference enabled = new CheckBoxPreference(context);
        enabled.setTitle(R.string.title_enable_probe);
        enabled.setKey(NfcProbe.ENABLED);
        enabled.setDefaultValue(NfcProbe.DEFAULT_ENABLED);

        screen.addPreference(enabled);

        return screen;
    }

    @Override
    public String summary(Context context)
    {
        return context.getString(R.string.summary_nfc_probe_desc);
    }

    @Override
    public void enable(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor e = prefs.edit();
        e.putBoolean(NfcProbe.ENABLED, true);

        e.apply();
    }

    @Override
    public void disable(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor e = prefs.edit();
        e.putBoolean(NfcProbe.ENABLED, false);

        e.apply();
    }

    @Override
    public String summarizeValue(Context context, Bundle bundle)
    {
        String tagId = bundle.getString("TAG_ID");

        return String.format(context.getResources().getString(R.string.summary_nfc_probe), tagId);
    }
}
