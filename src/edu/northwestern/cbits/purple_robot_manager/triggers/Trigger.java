package edu.northwestern.cbits.purple_robot_manager.triggers;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.CodeViewerActivity;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.scripting.BaseScriptEngine;

public abstract class Trigger
{
    public static final String NAME = "name";
    public static final String ACTION = "action";
    public static final String IDENTIFIER = "identifier";
    private static final String ENABLED = "enabled";
    private static final String LAST_FIRED = "last_fired";
    public static final String TYPE = "trigger_type";

    private String _name = null;
    private String _action = null;
    private String _identifier = "unidentified-trigger";

    public Trigger(Context context, Map<String, Object> map)
    {
        this.updateFromMap(context, map);
    }

    public abstract boolean matches(Context context, Object obj);

    public abstract void refresh(Context context);

    public boolean enabled(Context context)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.getBoolean(this.enabledKey(), true);
    }

    public void setEnabled(Context context, boolean enabled)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor e = prefs.edit();
        e.putBoolean(this.enabledKey(), enabled);
        e.apply();
    }

    public void execute(final Context context, boolean force)
    {
        if (this.enabled(context) && this._action != null)
        {
            final Trigger me = this;

            Runnable r = new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        BaseScriptEngine.runScript(context, me._action);
                    }
                    catch (Exception e)
                    {
                        LogManager.getInstance(context).logException(e);

                        HashMap<String, Object> payload = new HashMap<>();
                        payload.put("script", me._action);

                        LogManager.getInstance(context).log("failed_trigger_script", payload);
                    }
                }
            };

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

            String key = "last_fired_" + this.identifier();
            Editor edit = prefs.edit();
            edit.putLong(key, System.currentTimeMillis());
            edit.apply();

            Thread t = new Thread(new ThreadGroup("Triggers"), r, this.name(), 32768);
            t.start();

            HashMap<String, Object> payload = new HashMap<>();
            payload.put("name", this.name());
            payload.put("identifier", this.identifier());
            payload.put("action", this._action);

            LogManager.getInstance(context).log("pr_trigger_fired", payload);
        }
    }

    @Override
    public String toString()
    {
        return this.name();
    }

    public String name()
    {
        return this._name;
    }

    @Override
    public boolean equals(Object obj)
    {
        if (obj != null && obj instanceof Trigger)
        {
            Trigger t = (Trigger) obj;

            if ((t._identifier == null || this._identifier == null) && (t._name != null && t._name.equals(this._name)))
                return true;
            else if (t._identifier != null && t._identifier.equals(this._identifier))
                return true;
        }

        return false;
    }

    public void merge(Trigger trigger)
    {
        this._name = trigger._name;
        this._action = trigger._action;
    }

    public String identifier()
    {
        return this._identifier;
    }

    public void reset(Context context)
    {
        // Default implementation does nothing...
    }

    @SuppressWarnings("deprecation")
    public PreferenceScreen preferenceScreen(final Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = manager.createPreferenceScreen(context);
        screen.setTitle(this._name);

        String type = context.getString(R.string.type_trigger_unknown);

        if (this instanceof ProbeTrigger)
            type = context.getString(R.string.type_trigger_probe);
        if (this instanceof DateTrigger)
            type = context.getString(R.string.type_trigger_datetime);
        if (this instanceof BatteryLevelTrigger)
            type = context.getString(R.string.type_trigger_battery);

        screen.setSummary(type);

        final Trigger me = this;

        Preference viewAction = new Preference(context);
        viewAction.setTitle(R.string.label_trigger_show_action);
        viewAction.setSummary(R.string.label_trigger_show_action_desc);
        viewAction.setOrder(Integer.MAX_VALUE);

        viewAction.setOnPreferenceClickListener(new OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(context, CodeViewerActivity.class);
                intent.putExtra(CodeViewerActivity.SOURCE_CODE, me._action);
                intent.putExtra(CodeViewerActivity.TITLE, me.name());

                context.startActivity(intent);

                return true;
            }
        });

        screen.addPreference(viewAction);

        CheckBoxPreference enabled = new CheckBoxPreference(context);
        enabled.setTitle(R.string.label_trigger_enable_action);
        enabled.setSummary(R.string.label_trigger_enable_action_desc);
        enabled.setKey(this.enabledKey());
        enabled.setDefaultValue(true);

        enabled.setOrder(Integer.MAX_VALUE);

        enabled.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object newValue) {
                return true;
            }
        });

        screen.addPreference(enabled);

        this.addCustomPreferences(context, screen);

        Preference fireNow = new Preference(context);
        fireNow.setTitle(R.string.label_trigger_fire_now);
        fireNow.setSummary(R.string.label_trigger_fire_now_desc);
        fireNow.setOrder(Integer.MAX_VALUE / 2);

        fireNow.setOnPreferenceClickListener(new OnPreferenceClickListener()
        {
            @Override
            public boolean onPreferenceClick(Preference preference)
            {
                me.execute(context, true);

                return true;
            }
        });

        screen.addPreference(fireNow);

        return screen;
    }

    public void addCustomPreferences(Context context, PreferenceScreen screen) {
        // Do nothing by default...
    }

    private String enabledKey()
    {
        return "trigger_enabled_" + this._identifier;
    }

    public static Trigger parse(Context context, Map<String, Object> params)
    {
        String type = params.get("type").toString();

        if (DateTrigger.TYPE_NAME.equals(type))
            return new DateTrigger(context, params);
        else if (ProbeTrigger.TYPE_NAME.equals(type))
            return new ProbeTrigger(context, params);
        else if (BatteryLevelTrigger.TYPE_NAME.equals(type))
            return new BatteryLevelTrigger(context, params);

        return null;
    }

    public Map<String, Object> configuration(Context context)
    {
        Map<String, Object> config = new HashMap<>();

        config.put("name", this._name);

        if (this._identifier != null)
            config.put("identifier", this._identifier);
        else
            config.put("identifier", "unspecified-identifier");

        if (this._action != null)
            config.put("action", this._action);
        else
            config.put("action", "");

        if (this._name != null)
            config.put("name", this._name);
        else
            config.put("name", context.getString(R.string.name_anonymous_trigger));

        return config;
    }

    public boolean updateFromMap(Context context, Map<String, Object> params)
    {
        if (params.containsKey("name"))
            this._name = params.get("name").toString();

        if (params.containsKey("action"))
            this._action = params.get("action").toString();

        if (params.containsKey("identifier"))
        {
            try
            {
                this._identifier = params.get("identifier").toString();
            }
            catch (NullPointerException e)
            {
                this._identifier = "unspecified-identifier";
            }
        }

        TriggerManager.getInstance(context).persistTriggers(context);

        return true;
    }

    public abstract String getDiagnosticString(Context context);

    public Bundle bundle(Context context)
    {
        Bundle bundle = new Bundle();

        bundle.putString(Trigger.NAME, this._name);

        if (this._action != null)
            bundle.putString(Trigger.ACTION, this._action);

        bundle.putString(Trigger.IDENTIFIER, this._identifier);

        bundle.putBoolean(Trigger.ENABLED, this.enabled(context));

        bundle.putLong(Trigger.LAST_FIRED, this.lastFireTime(context));

        return bundle;
    }

    public long lastFireTime(Context context)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        String key = "last_fired_" + this.identifier();

        return prefs.getLong(key, 0);
    }
}
