package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.WebkitActivity;
import edu.northwestern.cbits.purple_robot_manager.activities.WebkitLandscapeActivity;
import edu.northwestern.cbits.purple_robot_manager.activities.settings.FlexibleListPreference;
import edu.northwestern.cbits.purple_robot_manager.charts.SplineChart;
import edu.northwestern.cbits.purple_robot_manager.db.ProbeValuesProvider;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

@SuppressLint("SimpleDateFormat")
public class ProximityProbe extends ContinuousProbe implements SensorEventListener
{
    private static final int BUFFER_SIZE = 8;

    private static final String DB_TABLE = "proximity_probe";

    private static final String DISTANCE_KEY = "DISTANCE";

    private static final String DEFAULT_THRESHOLD = "5.0";

    public static final String NAME = "edu.northwestern.cbits.purple_robot_manager.probes.builtin.ProximityProbe";

    private static final String FREQUENCY = "config_probe_proximity_built_in_frequency";
    private static final String THRESHOLD = "config_probe_proximity_built_in_threshold";
    private static final String ENABLED = "config_probe_proximity_built_in_enabled";
    private static final String USE_HANDLER = "config_probe_proximity_built_in_handler";

    private static String[] fieldNames = { DISTANCE_KEY };

    private double _lastValue = Double.MAX_VALUE;

    private long lastThresholdLookup = 0;
    private double lastThreshold = 5.0;

    private float[][] _currentValueBuffer = null;
    private int[] _currentAccuracyBuffer = null;
    private double[] _currentTimeBuffer = null;
    private double[] _currentSensorTimeBuffer = null;

    private int _bufferCount = 0;

    private int bufferIndex = 0;

    private int _lastFrequency = -1;

    private static Handler _handler = null;

    @Override
    public boolean getUsesThread()
    {
        SharedPreferences prefs = ContinuousProbe.getPreferences(this._context);

        return prefs.getBoolean(ProximityProbe.USE_HANDLER, MagneticFieldProbe.DEFAULT_USE_HANDLER);
    }

    @Override
    public String probeCategory(Context context)
    {
        return context.getString(R.string.probe_sensor_category);
    }

    @Override
    public Intent viewIntent(Context context)
    {
        Intent i = new Intent(context, WebkitLandscapeActivity.class);

        return i;
    }

    @Override
    public String contentSubtitle(Context context)
    {
        Cursor c = ProbeValuesProvider.getProvider(context).retrieveValues(context, ProximityProbe.DB_TABLE, this.databaseSchema());

        int count = -1;

        if (c != null)
        {
            count = c.getCount();
            c.close();
        }

        return String.format(context.getString(R.string.display_item_count), count);
    }

    public Map<String, String> databaseSchema()
    {
        HashMap<String, String> schema = new HashMap<>();

        schema.put(ProximityProbe.DISTANCE_KEY, ProbeValuesProvider.REAL_TYPE);

        return schema;
    }

    @Override
    public String getDisplayContent(Activity activity)
    {
        try
        {
            String template = WebkitActivity.stringForAsset(activity, "webkit/chart_spline_full.html");

            ArrayList<Double> distance = new ArrayList<>();
            ArrayList<Double> time = new ArrayList<>();

            Cursor cursor = ProbeValuesProvider.getProvider(activity).retrieveValues(activity, ProximityProbe.DB_TABLE, this.databaseSchema());

            int count = -1;

            if (cursor != null)
            {
                count = cursor.getCount();

                while (cursor.moveToNext())
                {
                    double d = cursor.getDouble(cursor.getColumnIndex(ProximityProbe.DISTANCE_KEY));
                    double t = cursor.getDouble(cursor.getColumnIndex(ProbeValuesProvider.TIMESTAMP));

                    distance.add(d);
                    time.add(t);
                }

                cursor.close();
            }

            SplineChart c = new SplineChart();
            c.addSeries(activity.getString(R.string.proximity_label), distance);

            c.addTime("tIME", time);

            JSONObject json = c.dataJson(activity);

            template = template.replace("{{{ highchart_json }}}", json.toString());
            template = template.replace("{{{ highchart_count }}}", "" + count);

            return template;
        }
        catch (IOException | JSONException e)
        {
            LogManager.getInstance(activity).logException(e);
        }

        return null;
    }

    @Override
    public Bundle formattedBundle(Context context, Bundle bundle)
    {
        Bundle formatted = super.formattedBundle(context, bundle);

        double[] eventTimes = bundle.getDoubleArray(ContinuousProbe.EVENT_TIMESTAMP);
        double[] distance = bundle.getDoubleArray(ProximityProbe.DISTANCE_KEY);

        ArrayList<String> keys = new ArrayList<>();

        if (distance != null && eventTimes != null)
        {
            SimpleDateFormat sdf = new SimpleDateFormat(context.getString(R.string.display_date_format));

            if (eventTimes.length > 1)
            {
                Bundle readings = new Bundle();

                for (double eventTime : eventTimes) {
                    String formatString = context.getString(R.string.proximity_formatted);

                    double time = eventTime;

                    Date d = new Date((long) time);

                    String key = sdf.format(d);

                    readings.putString(key, formatString);

                    keys.add(key);
                }

                if (keys.size() > 0)
                    readings.putStringArrayList("KEY_ORDER", keys);

                formatted.putBundle(context.getString(R.string.display_light_readings), readings);
            }
            else if (eventTimes.length > 0)
            {
                String formatString = "fORMATTED " + distance[0];

                double time = eventTimes[0];

                Date d = new Date((long) time);

                formatted.putString(sdf.format(d), formatString);
            }
        }

        return formatted;
    }

    @Override
    public long getFrequency()
    {
        SharedPreferences prefs = ContinuousProbe.getPreferences(this._context);

        return Long.parseLong(prefs.getString(ProximityProbe.FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));
    }

    @Override
    public String name(Context context)
    {
        return ProximityProbe.NAME;
    }

    @Override
    public int getTitleResource()
    {
        return R.string.title_proximity_probe;
    }

    @Override
    public boolean isEnabled(Context context)
    {
        SharedPreferences prefs = ContinuousProbe.getPreferences(context);

        this._context = context.getApplicationContext();

        final SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        final Sensor sensor = sensors.getDefaultSensor(Sensor.TYPE_PROXIMITY);

        if (super.isEnabled(context))
        {
            if (prefs.getBoolean(ProximityProbe.ENABLED, ContinuousProbe.DEFAULT_ENABLED))
            {
                int frequency = Integer.parseInt(prefs.getString(ProximityProbe.FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));

                if (this._lastFrequency != frequency)
                {
                    sensors.unregisterListener(this, sensor);

                    if (ProximityProbe._handler != null)
                    {
                        Looper loop = ProximityProbe._handler.getLooper();
                        loop.quit();

                        ProximityProbe._handler = null;
                    }

                    if (frequency != SensorManager.SENSOR_DELAY_FASTEST && frequency != SensorManager.SENSOR_DELAY_UI &&
                            frequency != SensorManager.SENSOR_DELAY_NORMAL)
                    {
                        frequency = SensorManager.SENSOR_DELAY_GAME;
                    }

                    if (prefs.getBoolean(ProximityProbe.USE_HANDLER, ContinuousProbe.DEFAULT_USE_HANDLER))
                    {
                        final ProximityProbe me = this;
                        final int finalFrequency = frequency;

                        Runnable r = new Runnable()
                        {
                            public void run()
                            {
                                Looper.prepare();

                                ProximityProbe._handler = new Handler();

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                                    sensors.registerListener(me, sensor, finalFrequency, 0, ProximityProbe._handler);
                                else
                                    sensors.registerListener(me, sensor, finalFrequency, ProximityProbe._handler);

                                Looper.loop();
                            }
                        };

                        Thread t = new Thread(r, "proximity");
                        t.start();
                    }
                    else
                    {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                            sensors.registerListener(this, sensor, frequency, 0);
                        else
                            sensors.registerListener(this, sensor, frequency, null);
                    }

                    this._lastFrequency = frequency;
                }

                return true;
            }
            else
            {
                sensors.unregisterListener(this, sensor);
                this._lastFrequency = -1;

                if (ProximityProbe._handler != null)
                {
                    Looper loop = ProximityProbe._handler.getLooper();
                    loop.quit();

                    ProximityProbe._handler = null;
                }
            }
        }
        else
        {
            sensors.unregisterListener(this, sensor);
            this._lastFrequency = -1;

            if (ProximityProbe._handler != null)
            {
                Looper loop = ProximityProbe._handler.getLooper();
                loop.quit();

                ProximityProbe._handler = null;
            }
        }

        return false;
    }

    @Override
    public PreferenceScreen preferenceScreen(Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = super.preferenceScreen(context, manager);

        FlexibleListPreference threshold = new FlexibleListPreference(context);
        threshold.setKey("config_probe_proximity_threshold");
        threshold.setDefaultValue(ProximityProbe.DEFAULT_THRESHOLD);
        threshold.setEntryValues(R.array.probe_proximity_threshold);
        threshold.setEntries(R.array.probe_proximity_threshold_labels);
        threshold.setTitle(R.string.probe_noise_threshold_label);
        threshold.setSummary(R.string.probe_noise_threshold_summary);

        screen.addPreference(threshold);

        CheckBoxPreference handler = new CheckBoxPreference(context);
        handler.setTitle(R.string.title_own_sensor_handler);
        handler.setKey(ProximityProbe.USE_HANDLER);
        handler.setDefaultValue(ContinuousProbe.DEFAULT_USE_HANDLER);

        screen.addPreference(handler);

        return screen;
    }

    @Override
    protected boolean passesThreshold(SensorEvent event)
    {
        long now = System.currentTimeMillis();

        if (now - this.lastThresholdLookup > 5000)
        {
            this.lastThreshold = this.getThreshold();

            this.lastThresholdLookup = now;
        }

        double value = event.values[0];

        boolean passes = false;

        if (Math.abs(value - this._lastValue) >= this.lastThreshold)
            passes = true;

        if (passes)
            this._lastValue = value;

        return passes;
    }

    @Override
    @SuppressLint("NewApi")
    public void onSensorChanged(final SensorEvent event)
    {
        if (this.shouldProcessEvent(event) == false)
            return;

        final double now = System.currentTimeMillis();

        synchronized (this) {
            if (this._currentValueBuffer == null) {
                this._bufferCount += 1;

                this._currentValueBuffer = new float[1][BUFFER_SIZE];
                this._currentAccuracyBuffer = new int[BUFFER_SIZE];
                this._currentTimeBuffer = new double[BUFFER_SIZE];
                this._currentSensorTimeBuffer = new double[BUFFER_SIZE];
            }
        }

        if (this.passesThreshold(event))
        {
            synchronized (this)
            {
                this._currentSensorTimeBuffer[bufferIndex] = event.timestamp;
                this._currentTimeBuffer[bufferIndex] = now / 1000;
                this._currentAccuracyBuffer[bufferIndex] = event.accuracy;
                this._currentValueBuffer[0][bufferIndex] = event.values[0];

                bufferIndex += 1;

                if (bufferIndex >= this._currentTimeBuffer.length)
                {
                    final float[][] myValueBuffer = this._currentValueBuffer;
                    final int[] myAccuracyBuffer = this._currentAccuracyBuffer;
                    final double[] myTimeBuffer = this._currentTimeBuffer;
                    final double[] mySensorTimeBuffer = this._currentSensorTimeBuffer;

                    bufferIndex = 0;

                    this._currentValueBuffer = null;
                    this._currentAccuracyBuffer = null;
                    this._currentTimeBuffer = null;
                    this._currentSensorTimeBuffer = null;

                    final ProximityProbe me = this;

                    Runnable r = new Runnable() {
                        @Override
                        public void run() {
                            Sensor sensor = event.sensor;

                            Bundle data = new Bundle();

                            Bundle sensorBundle = new Bundle();
                            sensorBundle.putFloat(ContinuousProbe.SENSOR_MAXIMUM_RANGE, sensor.getMaximumRange());
                            sensorBundle.putString(ContinuousProbe.SENSOR_NAME, sensor.getName());
                            sensorBundle.putFloat(ContinuousProbe.SENSOR_POWER, sensor.getPower());
                            sensorBundle.putFloat(ContinuousProbe.SENSOR_RESOLUTION, sensor.getResolution());
                            sensorBundle.putInt(ContinuousProbe.SENSOR_TYPE, sensor.getType());
                            sensorBundle.putString(ContinuousProbe.SENSOR_VENDOR, sensor.getVendor());
                            sensorBundle.putInt(ContinuousProbe.SENSOR_VERSION, sensor.getVersion());

                            data.putDouble(Probe.BUNDLE_TIMESTAMP, now / 1000);
                            data.putString(Probe.BUNDLE_PROBE, me.name(me._context));
                            data.putInt(ContinuousProbe.ACTIVE_BUFFER_COUNT, me._bufferCount);

                            data.putBundle(ContinuousProbe.BUNDLE_SENSOR, sensorBundle);

                            data.putDoubleArray(ContinuousProbe.EVENT_TIMESTAMP, myTimeBuffer);
                            data.putDoubleArray(ContinuousProbe.SENSOR_TIMESTAMP, mySensorTimeBuffer);
                            data.putIntArray(ContinuousProbe.SENSOR_ACCURACY, myAccuracyBuffer);

                            for (int i = 0; i < fieldNames.length; i++)
                            {
                                data.putFloatArray(fieldNames[i], myValueBuffer[i]);
                            }

                            me.transmitData(me._context, data);

                            for (int j = 0; j < myTimeBuffer.length; j++)
                            {
                                Double distance = null;

                                for (int i = 0; i < fieldNames.length; i++)
                                {
                                    if (fieldNames[i].equals(ProximityProbe.DISTANCE_KEY))
                                        distance = (double) myValueBuffer[i][j];
                                }

                                if (distance != null)
                                {
                                    Map<String, Object> values = new HashMap<>();

                                    values.put(ProximityProbe.DISTANCE_KEY, distance);

                                    values.put(ProbeValuesProvider.TIMESTAMP, myTimeBuffer[j] / 1000);

                                    ProbeValuesProvider.getProvider(me._context).insertValue(me._context, ProximityProbe.DB_TABLE, me.databaseSchema(), values);
                                }
                            }

                            me._bufferCount -= 1;
                        }
                    };

                    Thread t = new Thread(r);
                    t.start();
                }
            }
        }
    }

    @Override
    public String getPreferenceKey()
    {
        return "proximity_built_in";
    }

    @Override
    public String summarizeValue(Context context, Bundle bundle)
    {
        double distance = bundle.getDoubleArray(ProximityProbe.DISTANCE_KEY)[0];

        return String.format(context.getResources().getString(R.string.summary_proximity_value_probe), distance);
    }

    @Override
    public int getSummaryResource()
    {
        return R.string.summary_proximity_probe_desc;
    }

    @Override
    protected double getThreshold()
    {
        SharedPreferences prefs = Probe.getPreferences(this._context);

        return Double.parseDouble(prefs.getString(ProximityProbe.THRESHOLD, ProximityProbe.DEFAULT_THRESHOLD));
    }

    @Override
    protected int getResourceThresholdValues()
    {
        return R.array.probe_proximity_threshold;
    }

    @Override
    public JSONObject fetchSettings(Context context)
    {
        JSONObject settings = super.fetchSettings(context);

        try
        {
            JSONObject handler = new JSONObject();
            handler.put(Probe.PROBE_TYPE, Probe.PROBE_TYPE_BOOLEAN);

            JSONArray values = new JSONArray();
            values.put(true);
            values.put(false);
            handler.put(Probe.PROBE_VALUES, values);
            settings.put(ProximityProbe.USE_HANDLER, handler);
        }
        catch (JSONException e)
        {
            LogManager.getInstance(context).logException(e);
        }

        return settings;
    }

    @Override
    public void updateFromMap(Context context, Map<String, Object> params)
    {
        super.updateFromMap(context, params);

        if (params.containsKey(ProximityProbe.USE_HANDLER))
        {
            Object handler = params.get(ProximityProbe.USE_HANDLER);

            if (handler instanceof Boolean)
            {
                SharedPreferences prefs = Probe.getPreferences(context);
                SharedPreferences.Editor e = prefs.edit();

                e.putBoolean(ProximityProbe.USE_HANDLER, (Boolean) handler);
                e.apply();
            }
        }
    }
}
