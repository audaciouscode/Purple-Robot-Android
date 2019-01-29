package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
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

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.RealTimeProbeViewActivity;
import edu.northwestern.cbits.purple_robot_manager.activities.settings.FlexibleListPreference;
import edu.northwestern.cbits.purple_robot_manager.db.ProbeValuesProvider;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

@SuppressLint("SimpleDateFormat")
public class PressureProbe extends Continuous1DProbe implements SensorEventListener
{
    public static final String DB_TABLE = "pressure_probe";

    private static final String DEFAULT_THRESHOLD = "0.5";

    public static final String NAME = "edu.northwestern.cbits.purple_robot_manager.probes.builtin.PressureProbe";

    private static final String FREQUENCY = "config_probe_pressure_built_in_frequency";
    private static final String ENABLED = "config_probe_pressure_built_in_enabled";
    private static final String THRESHOLD = "config_probe_pressure_built_in_threshold";
    private static final String USE_HANDLER = "config_probe_prssure_built_in_handler";

    private static final int BUFFER_SIZE = 512;

    private static String PRESSURE_KEY = "PRESSURE";
    private static String ALTITUDE_KEY = "ALTITUDE";

    private static String[] fieldNames = { PRESSURE_KEY, ALTITUDE_KEY };

    private double _lastValue = Double.MAX_VALUE;

    private long lastThresholdLookup = 0;
    private double lastThreshold = 0.5;

    private float[][] _currentValueBuffer = null;
    private int[] _currentAccuracyBuffer = null;
    private double[] _currentTimeBuffer = null;
    private double[] _currentSensorTimeBuffer = null;

    private int _bufferCount = 0;

    private int bufferIndex = 0;

    private Map<String, String> _schema = null;

    private int _lastFrequency = -1;

    private static Handler _handler = null;

    @Override
    public boolean getUsesThread()
    {
        SharedPreferences prefs = ContinuousProbe.getPreferences(this._context);

        return prefs.getBoolean(PressureProbe.USE_HANDLER, PressureProbe.DEFAULT_USE_HANDLER);
    }

    @Override
    public String probeCategory(Context context)
    {
        return context.getString(R.string.probe_sensor_category);
    }

    @Override
    public String contentSubtitle(Context context)
    {
        Cursor c = ProbeValuesProvider.getProvider(context).retrieveValues(context, PressureProbe.DB_TABLE, this.databaseSchema());

        int count = -1;

        if (c != null)
        {
            count = c.getCount();
            c.close();
        }

        return String.format(context.getString(R.string.display_item_count), count);
    }

    @Override
    public Bundle formattedBundle(Context context, Bundle bundle)
    {
        Bundle formatted = super.formattedBundle(context, bundle);

        double[] eventTimes = bundle.getDoubleArray(ContinuousProbe.EVENT_TIMESTAMP);
        double[] altitudes = bundle.getDoubleArray(ALTITUDE_KEY);
        double[] pressures = bundle.getDoubleArray(PRESSURE_KEY);

        ArrayList<String> keys = new ArrayList<>();

        if (altitudes != null && pressures != null && eventTimes != null)
        {
            SimpleDateFormat sdf = new SimpleDateFormat(context.getString(R.string.display_date_format));

            if (eventTimes.length > 1)
            {
                Bundle readings = new Bundle();

                for (int i = 0; i < eventTimes.length; i++)
                {
                    String formatString = String.format(context.getString(R.string.display_pressure_reading), pressures[i], altitudes[i]);

                    double time = eventTimes[i];

                    Date d = new Date((long) time);

                    String key = sdf.format(d);

                    readings.putString(key, formatString);

                    keys.add(key);
                }

                if (keys.size() > 0)
                    readings.putStringArrayList("KEY_ORDER", keys);

                formatted.putBundle(context.getString(R.string.display_pressure_readings), readings);
            }
            else if (eventTimes.length > 0)
            {
                String formatString = String.format(context.getString(R.string.display_pressure_reading), pressures[0], altitudes[0]);

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
        return Long.parseLong(prefs.getString(PressureProbe.FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));
    }

    @Override
    public String name(Context context)
    {
        return PressureProbe.NAME;
    }

    @Override
    public int getTitleResource()
    {
        return R.string.title_pressure_probe;
    }

    @Override
    public boolean isEnabled(Context context)
    {
        SharedPreferences prefs = ContinuousProbe.getPreferences(context);

        this._context = context.getApplicationContext();

        final SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        final Sensor sensor = sensors.getDefaultSensor(Sensor.TYPE_PRESSURE);

        if (super.isEnabled(context))
        {
            if (prefs.getBoolean(PressureProbe.ENABLED, ContinuousProbe.DEFAULT_ENABLED))
            {
                int frequency = Integer.parseInt(prefs.getString(PressureProbe.FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));

                if (this._lastFrequency != frequency)
                {
                    sensors.unregisterListener(this, sensor);

                    if (PressureProbe._handler != null)
                    {
                        Looper loop = PressureProbe._handler.getLooper();
                        loop.quit();

                        PressureProbe._handler = null;
                    }

                    if (frequency != SensorManager.SENSOR_DELAY_FASTEST && frequency != SensorManager.SENSOR_DELAY_UI &&
                            frequency != SensorManager.SENSOR_DELAY_NORMAL)
                    {
                        frequency = SensorManager.SENSOR_DELAY_GAME;
                    }

                    if (prefs.getBoolean(PressureProbe.USE_HANDLER, ContinuousProbe.DEFAULT_USE_HANDLER))
                    {
                        final PressureProbe me = this;
                        final int finalFrequency = frequency;

                        Runnable r = new Runnable()
                        {
                            public void run()
                            {
                                Looper.prepare();

                                PressureProbe._handler = new Handler();

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                                    sensors.registerListener(me, sensor, finalFrequency, 0, PressureProbe._handler);
                                else
                                    sensors.registerListener(me, sensor, finalFrequency, PressureProbe._handler);

                                Looper.loop();
                            }
                        };

                        Thread t = new Thread(r, "pressure");
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

                if (PressureProbe._handler != null)
                {
                    Looper loop = PressureProbe._handler.getLooper();
                    loop.quit();

                    PressureProbe._handler = null;
                }
            }
        }
        else
        {
            sensors.unregisterListener(this, sensor);
            this._lastFrequency = -1;


            if (PressureProbe._handler != null)
            {
                Looper loop = PressureProbe._handler.getLooper();
                loop.quit();

                PressureProbe._handler = null;
            }
        }

        return false;
    }

    @Override
    public PreferenceScreen preferenceScreen(Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = super.preferenceScreen(context, manager);

        FlexibleListPreference threshold = new FlexibleListPreference(context);
        threshold.setKey(PressureProbe.THRESHOLD);
        threshold.setDefaultValue(PressureProbe.DEFAULT_THRESHOLD);
        threshold.setEntryValues(R.array.probe_pressure_threshold);
        threshold.setEntries(R.array.probe_pressure_threshold_labels);
        threshold.setTitle(R.string.probe_noise_threshold_label);
        threshold.setSummary(R.string.probe_noise_threshold_summary);

        screen.addPreference(threshold);

        CheckBoxPreference handler = new CheckBoxPreference(context);
        handler.setTitle(R.string.title_own_sensor_handler);
        handler.setKey(PressureProbe.USE_HANDLER);
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

                this._currentValueBuffer = new float[2][BUFFER_SIZE];
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

                try
                {
                    this._currentValueBuffer[1][bufferIndex] = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, event.values[0]);
                }
                catch (Exception e)
                {
                    this._currentValueBuffer[1][bufferIndex] = 0;
                }

                final double[] plotValues = { this._currentTimeBuffer[0] / 1000, this._currentValueBuffer[0][bufferIndex] };

                final PressureProbe me = this;

                Thread t = new Thread(new Runnable() {
                    @Override
                    public void run() {
                        RealTimeProbeViewActivity.plotIfVisible(me.getTitleResource(), plotValues);
                    }
                });

                t.start();

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
                                Double pressure = null;
                                Double altitude = null;

                                for (int i = 0; i < fieldNames.length; i++)
                                {
                                    if (fieldNames[i].equals(PressureProbe.PRESSURE_KEY))
                                        pressure = (double) myValueBuffer[i][j];
                                    else if (fieldNames[i].equals(PressureProbe.ALTITUDE_KEY))
                                        altitude = (double) myValueBuffer[i][j];
                                }

                                if (pressure != null && altitude != null)
                                {
                                    Map<String, Object> values = new HashMap<>();

                                    values.put(PressureProbe.PRESSURE_KEY, pressure);
                                    values.put(PressureProbe.ALTITUDE_KEY, altitude);

                                    values.put(ProbeValuesProvider.TIMESTAMP, myTimeBuffer[j] / 1000);

                                    ProbeValuesProvider.getProvider(me._context).insertValue(me._context, PressureProbe.DB_TABLE, me.databaseSchema(), values);
                                }
                            }

                            me._bufferCount -= 1;
                        }
                    };

                    t = new Thread(r);
                    t.start();
                }
            }
        }
    }

    @Override
    public Map<String, String> databaseSchema()
    {
        if (this._schema == null)
        {
            this._schema = new HashMap<>();

            this._schema.put(PressureProbe.PRESSURE_KEY, ProbeValuesProvider.REAL_TYPE);
            this._schema.put(PressureProbe.ALTITUDE_KEY, ProbeValuesProvider.REAL_TYPE);
        }

        return this._schema;
    }

    @Override
    public String getPreferenceKey()
    {
        return "pressure_built_in";
    }

    @Override
    public String summarizeValue(Context context, Bundle bundle)
    {
        double pressure = bundle.getDoubleArray(PressureProbe.PRESSURE_KEY)[0];
        double altitude = bundle.getDoubleArray(PressureProbe.ALTITUDE_KEY)[0];

        return String.format(context.getResources().getString(R.string.summary_pressure_probe), pressure, altitude);
    }

    @Override
    public int getSummaryResource()
    {
        return R.string.summary_pressure_probe_desc;
    }

    @Override
    protected String tableName()
    {
        return PressureProbe.DB_TABLE;
    }

    @Override
    protected double getThreshold()
    {
        SharedPreferences prefs = Probe.getPreferences(this._context);

        return Double.parseDouble(prefs.getString(PressureProbe.THRESHOLD, PressureProbe.DEFAULT_THRESHOLD));
    }

    @Override
    protected int getResourceThresholdValues()
    {
        return R.array.probe_pressure_threshold;
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
            settings.put(ContinuousProbe.USE_THREAD, handler);
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

        if (params.containsKey(ContinuousProbe.USE_THREAD))
        {
            Object handler = params.get(ContinuousProbe.USE_THREAD);

            if (handler instanceof Boolean)
            {
                SharedPreferences prefs = Probe.getPreferences(context);
                SharedPreferences.Editor e = prefs.edit();

                e.putBoolean(PressureProbe.USE_HANDLER, (Boolean) handler);
                e.apply();
            }
        }
    }
}
