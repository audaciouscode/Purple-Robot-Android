package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.settings.FlexibleListPreference;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

public class SoftwareInformationProbe extends Probe
{
    private static final String CODENAME = "CODENAME";
    private static final String INCREMENTAL = "INCREMENTAL";
    private static final String RELEASE = "RELEASE";
    private static final String SDK_INT = "SDK_INT";
    private static final String APP_NAME = "APP_NAME";
    private static final String PACKAGE_NAME = "PACKAGE_NAME";
    private static final String PACKAGE_VERSION_NAME = "PACKAGE_VERSION_NAME";
    private static final String PACKAGE_VERSION_CODE = "PACKAGE_VERSION_CODE";

    private static final String INSTALLED_APPS = "INSTALLED_APPS";
    private static final String INSTALLED_APP_COUNT = "INSTALLED_APP_COUNT";

    private static final boolean DEFAULT_ENABLED = true;
    private static final String ENABLED = "config_probe_software_enabled";
    private static final String FREQUENCY = "config_probe_software_frequency";

    private long _lastCheck = 0;

    @Override
    public String getPreferenceKey() {
        return "built_in_software";
    }

    @Override
    public String name(Context context)
    {
        return "edu.northwestern.cbits.purple_robot_manager.probes.builtin.SoftwareInformationProbe";
    }

    @Override
    public String title(Context context)
    {
        return context.getString(R.string.title_software_info_probe);
    }

    @Override
    public String probeCategory(Context context)
    {
        return context.getResources().getString(R.string.probe_device_info_category);
    }

    @Override
    public void enable(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor e = prefs.edit();
        e.putBoolean(SoftwareInformationProbe.ENABLED, true);

        e.apply();
    }

    @Override
    public void disable(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor e = prefs.edit();
        e.putBoolean(SoftwareInformationProbe.ENABLED, false);

        e.apply();
    }

    @Override
    public boolean isEnabled(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        if (super.isEnabled(context))
        {
            long now = System.currentTimeMillis();

            if (prefs.getBoolean(SoftwareInformationProbe.ENABLED, SoftwareInformationProbe.DEFAULT_ENABLED))
            {
                synchronized (this)
                {
                    long freq = Long.parseLong(prefs.getString(SoftwareInformationProbe.FREQUENCY, Probe.DEFAULT_FREQUENCY));

                    if (now - this._lastCheck > freq)
                    {
                        Bundle bundle = new Bundle();
                        bundle.putString("PROBE", this.name(context));
                        bundle.putLong("TIMESTAMP", System.currentTimeMillis() / 1000);

                        bundle.putString(SoftwareInformationProbe.CODENAME, Build.VERSION.CODENAME);
                        bundle.putString(SoftwareInformationProbe.INCREMENTAL, Build.VERSION.INCREMENTAL);
                        bundle.putString(SoftwareInformationProbe.RELEASE, Build.VERSION.RELEASE);
                        bundle.putInt(SoftwareInformationProbe.SDK_INT, Build.VERSION.SDK_INT);

                        try
                        {
                            PackageManager pm = context.getApplicationContext().getPackageManager();

                            List<ApplicationInfo> infos = pm.getInstalledApplications(0);

                            ArrayList<Bundle> installed = new ArrayList<>();

                            for (ApplicationInfo info : infos)
                            {
                                try
                                {
                                    Bundle appBundle = new Bundle();

                                    appBundle.putString(SoftwareInformationProbe.APP_NAME, info.loadLabel(pm).toString());
                                    appBundle.putString(SoftwareInformationProbe.PACKAGE_NAME, info.packageName);

                                    try
                                    {
                                        PackageInfo pkgInfo = pm.getPackageInfo(info.packageName, 0);

                                        appBundle.putString(SoftwareInformationProbe.PACKAGE_VERSION_NAME, pkgInfo.versionName);
                                        appBundle.putInt(SoftwareInformationProbe.PACKAGE_VERSION_CODE, pkgInfo.versionCode);
                                    }
                                    catch (Resources.NotFoundException e)
                                    {
                                        appBundle.putString(SoftwareInformationProbe.PACKAGE_VERSION_NAME, "");
                                        appBundle.putInt(SoftwareInformationProbe.PACKAGE_VERSION_CODE, -1);
                                    }

                                    installed.add(appBundle);
                                }
                                catch (PackageManager.NameNotFoundException e)
                                {
                                    e.printStackTrace();
                                }
                            }

                            bundle.putParcelableArrayList(SoftwareInformationProbe.INSTALLED_APPS, installed);
                            bundle.putInt(SoftwareInformationProbe.INSTALLED_APP_COUNT, installed.size());
                        }
                        catch (RuntimeException e)
                        {
                            LogManager.getInstance(context).logException(e);
                        }

                        this.transmitData(context, bundle);

                        this._lastCheck = now;
                    }
                }

                return true;
            }
        }

        return false;
    }

    @Override
    public String summarizeValue(Context context, Bundle bundle)
    {
        String release = bundle.getString(SoftwareInformationProbe.RELEASE);
        int count = (int) bundle.getDouble(SoftwareInformationProbe.INSTALLED_APP_COUNT);

        return String.format(context.getResources().getString(R.string.summary_software_info_probe), release, count);
    }

    private Bundle bundleForAppArray(Context context, ArrayList<Bundle> objects)
    {
        Bundle bundle = new Bundle();

        ArrayList<String> keys = new ArrayList<>();

        for (int i = 0; i < objects.size(); i++)
        {
            Bundle value = objects.get(i);
            String name = value.getString(SoftwareInformationProbe.APP_NAME);
            String key = value.getString(SoftwareInformationProbe.PACKAGE_NAME);

            keys.add(key);
            bundle.putString(key, name);
        }

        bundle.putStringArrayList("KEY_ORDER", keys);

        return bundle;
    }

    @Override
    public Bundle formattedBundle(Context context, Bundle bundle)
    {
        Bundle formatted = super.formattedBundle(context, bundle);

        @SuppressWarnings("unchecked")
        ArrayList<Bundle> array = (ArrayList<Bundle>) bundle.get(SoftwareInformationProbe.INSTALLED_APPS);
        int count = (int) bundle.getDouble(SoftwareInformationProbe.INSTALLED_APP_COUNT);

        Bundle appsBundle = this.bundleForAppArray(context, array);

        formatted.putBundle(String.format(context.getString(R.string.display_installed_apps_title), count), appsBundle);
        formatted.putString(context.getString(R.string.display_android_version_title), bundle.getString(SoftwareInformationProbe.RELEASE));

        return formatted;
    }

    @Override
    public Map<String, Object> configuration(Context context)
    {
        Map<String, Object> map = super.configuration(context);

        SharedPreferences prefs = Probe.getPreferences(context);

        long freq = Long.parseLong(prefs.getString(SoftwareInformationProbe.FREQUENCY, Probe.DEFAULT_FREQUENCY));

        map.put(Probe.PROBE_FREQUENCY, freq);

        return map;
    }

    @Override
    public void updateFromMap(Context context, Map<String, Object> params)
    {
        super.updateFromMap(context, params);

        if (params.containsKey(Probe.PROBE_FREQUENCY))
        {
            Object frequency = params.get(Probe.PROBE_FREQUENCY);

            if (frequency instanceof Double)
            {
                frequency = ((Double) frequency).longValue();
            }

            if (frequency instanceof Long)
            {
                SharedPreferences prefs = Probe.getPreferences(context);
                Editor e = prefs.edit();
                e.putString(SoftwareInformationProbe.FREQUENCY, frequency.toString());
                e.apply();
            }
        }
    }

    @Override
    public String summary(Context context)
    {
        return context.getString(R.string.summary_software_info_probe_desc);
    }

    @Override
    @SuppressWarnings("deprecation")
    public PreferenceScreen preferenceScreen(Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = super.preferenceScreen(context, manager);

        screen.setTitle(this.title(context));
        screen.setSummary(R.string.summary_software_info_probe_desc);

        CheckBoxPreference enabled = new CheckBoxPreference(context);
        enabled.setTitle(R.string.title_enable_probe);
        enabled.setKey(SoftwareInformationProbe.ENABLED);
        enabled.setDefaultValue(SoftwareInformationProbe.DEFAULT_ENABLED);

        screen.addPreference(enabled);

        FlexibleListPreference duration = new FlexibleListPreference(context);
        duration.setKey(SoftwareInformationProbe.FREQUENCY);
        duration.setEntryValues(R.array.probe_low_frequency_values);
        duration.setEntries(R.array.probe_low_frequency_labels);
        duration.setTitle(R.string.probe_frequency_label);
        duration.setDefaultValue(Probe.DEFAULT_FREQUENCY);

        screen.addPreference(duration);

        return screen;
    }

    @Override
    public JSONObject fetchSettings(Context context)
    {
        JSONObject settings = super.fetchSettings(context);

        try
        {
            JSONObject frequency = new JSONObject();
            frequency.put(Probe.PROBE_TYPE, Probe.PROBE_TYPE_LONG);
            JSONArray values = new JSONArray();

            String[] options = context.getResources().getStringArray(R.array.probe_low_frequency_values);

            for (String option : options)
            {
                values.put(Long.parseLong(option));
            }

            frequency.put(Probe.PROBE_VALUES, values);
            settings.put(Probe.PROBE_FREQUENCY, frequency);
        }
        catch (JSONException e)
        {
            LogManager.getInstance(context).logException(e);
        }

        return settings;
    }

    public String assetPath(Context context)
    {
        return "software-information-probe.html";
    }
}
