package edu.northwestern.cbits.purple_robot_manager;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.scripting.BaseScriptEngine;

public class ScheduleManager
{
    private static final String DATE_FORMAT = "yyyyMMdd'T'HHmmss";

    @SuppressLint("SimpleDateFormat")
    public static String formatString(Date date)
    {
        SimpleDateFormat sdf = new SimpleDateFormat(ScheduleManager.DATE_FORMAT);

        return sdf.format(date);
    }

    @SuppressLint("SimpleDateFormat")
    public static void runOverdueScripts(Context context)
    {
        List<Map<String, String>> scripts = ScheduleManager.fetchScripts(context);

        long now = System.currentTimeMillis();
        SimpleDateFormat sdf = new SimpleDateFormat(ScheduleManager.DATE_FORMAT);

        List<Map<String, String>> executed = new ArrayList<>();

        for (Map<String, String> script : scripts)
        {
            String dateString = script.get("date");

            try
            {
                Date d = sdf.parse(dateString);

                if (d.getTime() < now)
                {
                    executed.add(script);

                    String action = script.get("action");

                    BaseScriptEngine.runScript(context, action);

                    HashMap<String, Object> payload = new HashMap<>();
                    payload.put("run_timestamp", now);
                    payload.put("scheduled_timestamp", d.getTime());
                    payload.put("action", action);

                    LogManager.getInstance(context).log("pr_scheduled_script_run", payload);
                }
            }
            catch (Exception e)
            {
                LogManager.getInstance(context).logException(e);

                executed.add(script);
            }
        }

        scripts.removeAll(executed);

        ScheduleManager.persistScripts(context, scripts);
    }

    private static void persistScripts(Context context, List<Map<String, String>> scripts)
    {
        JSONArray array = new JSONArray();

        for (Map<String, String> script : scripts)
        {
            JSONObject json = new JSONObject();

            if (script.containsKey("identifier") && script.containsKey("date") && script.containsKey("action"))
            {
                try
                {
                    json.put("identifier", script.get("identifier"));
                    json.put("date", script.get("date"));
                    json.put("action", script.get("action"));

                    array.put(json);
                }
                catch (JSONException e)
                {
                    LogManager.getInstance(context).logException(e);
                }
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Editor e = prefs.edit();

        e.putString("scheduled_scripts", array.toString());

        e.apply();
    }

    private static List<Map<String, String>> fetchScripts(Context context)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        ArrayList<Map<String, String>> scripts = new ArrayList<>();

        try
        {
            JSONArray array = new JSONArray(prefs.getString("scheduled_scripts", "[]"));

            for (int i = 0; i < array.length(); i++)
            {
                JSONObject json = (JSONObject) array.get(i);

                HashMap<String, String> script = new HashMap<>();

                if (json.has("identifier") && json.has("date") && json.has("action"))
                {
                    script.put("identifier", json.get("identifier").toString());
                    script.put("date", json.get("date").toString());
                    script.put("action", json.get("action").toString());

                    scripts.add(script);
                }
            }
        }
        catch (JSONException e)
        {
            LogManager.getInstance(context).logException(e);
        }

        return scripts;
    }

    public static void updateScript(Context context, String identifier, String dateString, String action)
    {
        List<Map<String, String>> scripts = ScheduleManager.fetchScripts(context);

        if (dateString == null || action == null)
        {
            Map<String, String> found = null;

            for (Map<String, String> script : scripts)
            {
                if (script.get("identifier").equals(identifier))
                    found = script;
            }

            scripts.remove(found);

            LogManager.getInstance(context).log("pr_cancel_scheduled_script", null);
        }
        else
        {
            boolean found = false;

            for (Map<String, String> script : scripts)
            {
                if (identifier.equals(script.get("identifier")))
                {
                    script.put("date", dateString);
                    script.put("action", action);

                    found = true;
                }
            }

            if (found == false)
            {
                HashMap<String, String> script = new HashMap<>();

                script.put("date", dateString);
                script.put("action", action);
                script.put("identifier", identifier);

                scripts.add(script);
            }

            HashMap<String, Object> payload = new HashMap<>();
            payload.put("scheduled_date", dateString);
            payload.put("action", action);
            payload.put("identifier", identifier);

            LogManager.getInstance(context).log("pr_scheduled_script", payload);
        }

        ScheduleManager.persistScripts(context, scripts);
    }

    public static void updateScript(Context context, String identifier, long delay, String action)
    {
        long now = System.currentTimeMillis();

        Date when = new Date(now + delay);

        SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

        ScheduleManager.updateScript(context, identifier, format.format(when), action);
    }

    public static Date clearMillis(Date d)
    {
        long time = d.getTime();

        time = time - (time % 1000);

        return new Date(time);
    }

    @SuppressLint("SimpleDateFormat")
    public static Date parseString(String dateString)
    {
        SimpleDateFormat sdf = new SimpleDateFormat(ScheduleManager.DATE_FORMAT);

        try
        {
            return ScheduleManager.clearMillis(sdf.parse(dateString));

        }
        catch (ParseException e)
        {
            try
            {
                LogManager.getInstance(null).logException(e);
            }
            catch (NullPointerException ee)
            {
                // No LogManager available yet.
            }

            return null;
        }
    }

    public static List<Map<String, String>> allScripts(Context context)
    {
        return ScheduleManager.fetchScripts(context);
    }
}
