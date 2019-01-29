package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import android.app.ActivityManager;
import android.app.ActivityManager.RunningTaskInfo;
import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.widget.Toast;

import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.WiFiHelper;
import edu.northwestern.cbits.purple_robot_manager.activities.settings.FlexibleListPreference;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.logging.SanityCheck;
import edu.northwestern.cbits.purple_robot_manager.logging.SanityManager;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

public class RunningSoftwareProbe extends Probe
{
    private static final String PACKAGE_NAME = "PACKAGE_NAME";
    private static final String RUNNING_TASKS = "RUNNING_TASKS";
    private static final String RUNNING_TASK_COUNT = "RUNNING_TASK_COUNT";
    private static final String PACKAGE_CATEGORY = "PACKAGE_CATEGORY";
    private static final String TASK_STACK_INDEX = "TASK_STACK_INDEX";

    private static final boolean DEFAULT_ENABLED = true;
    private static final String ENABLED = "config_probe_running_software_enabled";
    private static final String FREQUENCY = "config_probe_running_software_frequency";
    private static final String MUTE_ANDROID_FIVE_WARNING = "config_probe_running_software_mute_android_five_warning";
    private static final boolean DEFAULT_ANDROID_FIVE_WARNING = false;

    private long _lastCheck = 0;

    @Override
    public String getPreferenceKey() {
        return "built_in_running_software";
    }

    @Override
    public String name(Context context)
    {
        return "edu.northwestern.cbits.purple_robot_manager.probes.builtin.RunningSoftwareProbe";
    }

    @Override
    public String title(Context context)
    {
        return context.getString(R.string.title_running_software_probe);
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
        e.putBoolean(RunningSoftwareProbe.ENABLED, true);

        e.apply();
    }

    @Override
    public void disable(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor e = prefs.edit();
        e.putBoolean(RunningSoftwareProbe.ENABLED, false);

        e.apply();
    }

    @Override
    public boolean isEnabled(final Context context)
    {
        final SharedPreferences prefs = Probe.getPreferences(context);

        if (super.isEnabled(context))
        {
            final long now = System.currentTimeMillis();

            if (prefs.getBoolean(RunningSoftwareProbe.ENABLED, RunningSoftwareProbe.DEFAULT_ENABLED))
            {
                synchronized (this)
                {
                    long freq = Long.parseLong(prefs.getString(RunningSoftwareProbe.FREQUENCY, Probe.DEFAULT_FREQUENCY));

                    if (now - this._lastCheck > freq)
                    {
                        final RunningSoftwareProbe me = this;

                        Runnable r = new Runnable()
                        {
                            @Override
                            @SuppressWarnings("deprecation")
                            public void run()
                            {
                                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP)
                                {
                                    ActivityManager am = (ActivityManager) context.getApplicationContext()
                                            .getSystemService(Context.ACTIVITY_SERVICE);

                                    List<RunningTaskInfo> tasks = am.getRunningTasks(9999);

                                    Bundle bundle = new Bundle();
                                    bundle.putString("PROBE", me.name(context));
                                    bundle.putLong("TIMESTAMP", System.currentTimeMillis() / 1000);

                                    ArrayList<Bundle> running = new ArrayList<>();

                                    if (tasks != null)
                                    {
                                        for (int i = 0; i < tasks.size(); i++) {
                                            RunningTaskInfo info = tasks.get(i);

                                            Bundle taskBundle = new Bundle();

                                            taskBundle.putString(RunningSoftwareProbe.PACKAGE_NAME, info.baseActivity.getPackageName());
                                            taskBundle.putInt(RunningSoftwareProbe.TASK_STACK_INDEX, i);

                                            String category = RunningSoftwareProbe.fetchCategory(context,  info.baseActivity.getPackageName());
                                            taskBundle.putString(RunningSoftwareProbe.PACKAGE_CATEGORY, category);

                                            running.add(taskBundle);
                                        }

                                        bundle.putInt(RunningSoftwareProbe.RUNNING_TASK_COUNT, running.size());

                                        bundle.putParcelableArrayList(RunningSoftwareProbe.RUNNING_TASKS, running);

                                        me.transmitData(context, bundle);
                                    }
                                }
                                else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
                                {
                                    final SanityManager sanity = SanityManager.getInstance(context);
                                    final String title = context.getString(R.string.title_app_usage_data_unavailable_rsp);

                                    if (prefs.getBoolean(RunningSoftwareProbe.MUTE_ANDROID_FIVE_WARNING, RunningSoftwareProbe.DEFAULT_ANDROID_FIVE_WARNING) == false) {

                                        final String message = context.getString(R.string.message_app_usage_data_unavailable_rsp);

                                        sanity.addAlert(SanityCheck.WARNING, title, message, null);
                                    }
                                    else
                                        sanity.clearAlert(title);
                                }
                                else
                                {
                                    UsageStatsManager usage = (UsageStatsManager) context.getSystemService(Context.USAGE_STATS_SERVICE);

                                    synchronized(usage)
                                    {
                                        final SanityManager sanity = SanityManager.getInstance(context);

                                        final String title = context.getString(R.string.title_app_usage_data_required);
                                        final String message = context.getString(R.string.message_app_usage_data_required);

                                        final long now = System.currentTimeMillis();

                                        if (usage.queryEvents(now - (60 * 60 * 1000), now).hasNextEvent() == false)
                                        {
                                            Runnable action = new Runnable() {
                                                @Override
                                                public void run() {
                                                    Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                                                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                                                    try
                                                    {
                                                        context.startActivity(intent);

                                                        sanity.clearAlert(title);
                                                    }
                                                    catch(Exception e)
                                                    {
                                                        LogManager.getInstance(context).logException(e);

                                                        LogManager.getInstance(context).logException(e);

                                                        Runnable r = new Runnable()
                                                        {
                                                            @Override
                                                            public void run() {
                                                                Toast.makeText(context, R.string.toast_missing_access_settings, Toast.LENGTH_LONG).show();
                                                            }
                                                        };

                                                        new Handler(Looper.getMainLooper()).post(r);
                                                    }
                                                }
                                            };

                                            sanity.addAlert(SanityCheck.WARNING, title, message, action);
                                        }
                                        else
                                        {
                                            sanity.clearAlert(title);

                                            me._lastCheck = now;

                                            UsageEvents events = usage.queryEvents(now - (5 * 60 * 1000), now);

                                            ArrayList<String> packages = new ArrayList<>();

                                            UsageEvents.Event event = new UsageEvents.Event();

                                            while (events.getNextEvent(event)) {
                                                if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND)
                                                {
                                                    String pkgName = event.getPackageName();
                                                    if (packages.contains(pkgName))
                                                        packages.remove(pkgName);

                                                    packages.add(pkgName);
                                                }

                                                event = new UsageEvents.Event();
                                            }

                                            Bundle bundle = new Bundle();
                                            bundle.putString("PROBE", me.name(context));
                                            bundle.putLong("TIMESTAMP", System.currentTimeMillis() / 1000);

                                            ArrayList<Bundle> running = new ArrayList<>();

                                            Collections.reverse(packages);

                                            for (int i = 0; i < packages.size(); i++) {
                                                Bundle taskBundle = new Bundle();

                                                String pkgName = packages.get(i);

                                                taskBundle.putString(RunningSoftwareProbe.PACKAGE_NAME, pkgName);
                                                taskBundle.putInt(RunningSoftwareProbe.TASK_STACK_INDEX, i);

                                                String category = RunningSoftwareProbe.fetchCategory(context, pkgName);
                                                taskBundle.putString(RunningSoftwareProbe.PACKAGE_CATEGORY, category);

                                                running.add(taskBundle);
                                            }

                                            if (running.size() > 0) {
                                                bundle.putInt(RunningSoftwareProbe.RUNNING_TASK_COUNT, running.size());
                                                bundle.putParcelableArrayList(RunningSoftwareProbe.RUNNING_TASKS, running);

                                                me.transmitData(context, bundle);
                                            }
                                        }
                                    }
                               }
                            }
                        };

                        Thread t = new Thread(r);
                        t.start();

                        me._lastCheck = now;
                    }
                }

                return true;
            }
        }

        return false;
    }

    protected static String fetchCategory(Context context, String packageName)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        String key = "category_" + packageName;

        if (prefs.contains(key))
            return prefs.getString(key, context.getString(R.string.app_category_unknown));

        try
        {
            // TODO: Replace with constant...
            if (prefs.getBoolean("config_restrict_data_wifi", true))
            {
                if (WiFiHelper.wifiAvailable(context) == false)
                    return context.getString(R.string.app_category_unknown);
            }
        }
        catch (ClassCastException e)
        {
            if (prefs.getString("config_restrict_data_wifi", "true").equalsIgnoreCase("true"))
            {
                Editor ed = prefs.edit();
                ed.putBoolean("config_restrict_data_wifi", true);
                ed.apply();

                if (WiFiHelper.wifiAvailable(context) == false)
                    return context.getString(R.string.app_category_unknown);
            }
            else
            {
                Editor ed = prefs.edit();
                ed.putBoolean("config_restrict_data_wifi", false);
                ed.apply();
            }
        }

        String category = null;

        try
        {
            String url = "https://play.google.com/store/apps/details?id=" + packageName;

            Document doc = Jsoup.connect(url).get();

            Element detailsTab = doc.select("a.category").first();

            if (detailsTab != null)
            {
                Elements spans = detailsTab.select("span");

                for (Element span : spans)
                {
                    category = span.text();
                }
            }
        }
        catch (HttpStatusException ex)
        {
            if (ex.getStatusCode() == 404)
                category = context.getString(R.string.app_category_bundled);
            else
                LogManager.getInstance(context).logException(ex);
        }
        catch (IOException | NullPointerException ex)
        {
            LogManager.getInstance(context).logException(ex);
        }

        if (category == null)
            category = context.getString(R.string.app_category_unknown);

        Editor e = prefs.edit();

        e.putString(key, category);

        e.apply();

        return category;
    }

    @Override
    public String summarizeValue(Context context, Bundle bundle)
    {
        int count = (int) bundle.getDouble(RunningSoftwareProbe.RUNNING_TASK_COUNT);

        return String.format(context.getResources().getString(R.string.summary_running_software_probe), count);
    }

    private Bundle bundleForTaskArray(Context context, ArrayList<Bundle> objects)
    {
        Bundle bundle = new Bundle();

        ArrayList<String> keys = new ArrayList<>();

        for (int i = 0; i < objects.size(); i++)
        {
            Bundle value = objects.get(i);
            String name = value.getString(RunningSoftwareProbe.PACKAGE_NAME);

            String key = String.format(context.getString(R.string.display_running_task_title), (i + 1));

            keys.add(key);
            bundle.putString(key, name);
        }

        bundle.putStringArrayList("KEY_ORDER", keys);

        return bundle;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Bundle formattedBundle(Context context, Bundle bundle)
    {
        Bundle formatted = super.formattedBundle(context, bundle);

        ArrayList<Bundle> array = (ArrayList<Bundle>) bundle.get(RunningSoftwareProbe.RUNNING_TASKS);

        int count = (int) bundle.getDouble(RunningSoftwareProbe.RUNNING_TASK_COUNT);

        Bundle tasksBundle = this.bundleForTaskArray(context, array);

        formatted.putBundle(String.format(context.getString(R.string.display_running_tasks_title), count), tasksBundle);

        return formatted;
    }

    @Override
    public Map<String, Object> configuration(Context context)
    {
        Map<String, Object> map = super.configuration(context);

        SharedPreferences prefs = Probe.getPreferences(context);

        long freq = Long.parseLong(prefs.getString(RunningSoftwareProbe.FREQUENCY, Probe.DEFAULT_FREQUENCY));

        map.put(Probe.PROBE_FREQUENCY, freq);

        boolean muteWarning = prefs.getBoolean(RunningSoftwareProbe.MUTE_ANDROID_FIVE_WARNING, RunningSoftwareProbe.DEFAULT_ANDROID_FIVE_WARNING);

        map.put(Probe.PROBE_MUTE_WARNING, muteWarning);

        return map;
    }

    @Override
    public void updateFromMap(Context context, Map<String, Object> params)
    {
        super.updateFromMap(context, params);

        SharedPreferences prefs = Probe.getPreferences(context);
        Editor e = prefs.edit();

        if (params.containsKey(Probe.PROBE_FREQUENCY)) {
            Object frequency = params.get(Probe.PROBE_FREQUENCY);

            if ((frequency instanceof Double) == false)
                frequency = Double.valueOf(frequency.toString()).longValue();
            else
                frequency = ((Double) frequency).longValue();


            e.putString(RunningSoftwareProbe.FREQUENCY, frequency.toString());
        }

        if (params.containsKey(Probe.PROBE_MUTE_WARNING))
        {
            Boolean muteWarning = (Boolean) params.get(Probe.PROBE_MUTE_WARNING);

            e.putBoolean(RunningSoftwareProbe.MUTE_ANDROID_FIVE_WARNING, muteWarning);
        }

        e.apply();
    }

    @Override
    public String summary(Context context)
    {
        return context.getString(R.string.summary_running_software_probe_desc);
    }

    @Override
    @SuppressWarnings("deprecation")
    public PreferenceScreen preferenceScreen(final Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = super.preferenceScreen(context, manager);

        screen.setTitle(this.title(context));
        screen.setSummary(R.string.summary_running_software_probe_desc);

        CheckBoxPreference enabled = new CheckBoxPreference(context);
        enabled.setTitle(R.string.title_enable_probe);
        enabled.setKey(RunningSoftwareProbe.ENABLED);
        enabled.setDefaultValue(RunningSoftwareProbe.DEFAULT_ENABLED);

        screen.addPreference(enabled);

        FlexibleListPreference duration = new FlexibleListPreference(context);
        duration.setKey(RunningSoftwareProbe.FREQUENCY);
        duration.setEntryValues(R.array.probe_low_frequency_values);
        duration.setEntries(R.array.probe_low_frequency_labels);
        duration.setTitle(R.string.probe_frequency_label);
        duration.setDefaultValue(Probe.DEFAULT_FREQUENCY);

        screen.addPreference(duration);

        CheckBoxPreference muteWarning = new CheckBoxPreference(context);
        muteWarning.setTitle(R.string.title_mute_android_five_warning);
        muteWarning.setKey(RunningSoftwareProbe.MUTE_ANDROID_FIVE_WARNING);
        muteWarning.setDefaultValue(RunningSoftwareProbe.DEFAULT_ANDROID_FIVE_WARNING);

        screen.addPreference(muteWarning);

        return screen;
    }

    @Override
    public JSONObject fetchSettings(Context context)
    {
        JSONObject settings = super.fetchSettings(context);

        try
        {
            JSONArray values = new JSONArray();
            values.put(true);
            values.put(false);

            JSONObject muteWarning = new JSONObject();
            muteWarning.put(Probe.PROBE_TYPE, Probe.PROBE_TYPE_BOOLEAN);
            muteWarning.put(Probe.PROBE_VALUES, values);
            settings.put(Probe.PROBE_MUTE_WARNING, muteWarning);

            JSONObject frequency = new JSONObject();
            frequency.put(Probe.PROBE_TYPE, Probe.PROBE_TYPE_LONG);
            values = new JSONArray();

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
        return "running-software-probe.html";
    }
}
