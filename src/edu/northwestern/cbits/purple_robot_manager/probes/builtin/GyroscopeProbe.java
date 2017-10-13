package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
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
public class GyroscopeProbe extends Continuous3DProbe implements SensorEventListener
{
    private static final int BUFFER_SIZE = 1024;

    public static final String DB_TABLE = "gyroscope_probe";

    private static final String DEFAULT_THRESHOLD = "0.0025";

    public static final String NAME = "edu.northwestern.cbits.purple_robot_manager.probes.builtin.GyroscopeProbe";

    private static final String THRESHOLD = "config_probe_gyroscope_built_in_threshold";
    private static final String ENABLED = "config_probe_gyroscope_built_in_enabled";
    private static String FREQUENCY = "config_probe_gyroscope_built_in_frequency";
    private static final String USE_HANDLER = "config_probe_gyroscope_built_in_handler";

    private static String[] fieldNames = { Continuous3DProbe.X_KEY, Continuous3DProbe.Y_KEY, Continuous3DProbe.Z_KEY };

    private double _lastX = Double.MAX_VALUE;
    private double _lastY = Double.MAX_VALUE;
    private double _lastZ = Double.MAX_VALUE;

    private long lastThresholdLookup = 0;
    private double lastThreshold = 0.0025;

    private float[][] _currentValueBuffer = null;
    private int[] _currentAccuracyBuffer = null;
    private double[] _currentTimeBuffer = null;
    private double[] _currentSensorTimeBuffer = null;

    private int _bufferCount = 0;

    private Map<String, String> _schema = null;

    private int bufferIndex = 0;

    private int _lastFrequency = -1;

    private static Handler _handler = null;

    private boolean _isRefreshing = false;

    @Override
    public boolean getUsesThread()
    {
        SharedPreferences prefs = ContinuousProbe.getPreferences(this._context);

        return prefs.getBoolean(GyroscopeProbe.USE_HANDLER, GyroscopeProbe.DEFAULT_USE_HANDLER);
    }

    @Override
    public String probeCategory(Context context)
    {
        return context.getString(R.string.probe_sensor_category);
    }

    @Override
    public Map<String, String> databaseSchema()
    {
        if (this._schema == null)
        {
            this._schema = new HashMap<>();

            this._schema.put(Continuous3DProbe.X_KEY, ProbeValuesProvider.REAL_TYPE);
            this._schema.put(Continuous3DProbe.Y_KEY, ProbeValuesProvider.REAL_TYPE);
            this._schema.put(Continuous3DProbe.Z_KEY, ProbeValuesProvider.REAL_TYPE);
        }

        return this._schema;
    }

    @Override
    public Bundle formattedBundle(Context context, Bundle bundle)
    {
        Bundle formatted = super.formattedBundle(context, bundle);

        double[] eventTimes = bundle.getDoubleArray(ContinuousProbe.EVENT_TIMESTAMP);
        double[] x = bundle.getDoubleArray(Continuous3DProbe.X_KEY);
        double[] y = bundle.getDoubleArray(Continuous3DProbe.Y_KEY);
        double[] z = bundle.getDoubleArray(Continuous3DProbe.Z_KEY);

        ArrayList<String> keys = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat(context.getString(R.string.display_date_format));

        if (eventTimes.length > 1)
        {
            Bundle readings = new Bundle();

            for (int i = 0; i < eventTimes.length; i++)
            {
                String formatString = String.format(context.getString(R.string.display_gyroscope_reading), x[i], y[i], z[i]);

                double time = eventTimes[i];

                Date d = new Date((long) time);

                String key = sdf.format(d);

                readings.putString(key, formatString);

                keys.add(key);
            }

            if (keys.size() > 0)
                readings.putStringArrayList("KEY_ORDER", keys);

            formatted.putBundle(context.getString(R.string.display_gyroscope_readings), readings);
        }
        else if (eventTimes.length > 0)
        {
            String formatString = String.format(context.getString(R.string.display_gyroscope_reading), x[0], y[0], z[0]);

            double time = eventTimes[0];

            Date d = new Date((long) time);

            formatted.putString(sdf.format(d), formatString);
        }

        return formatted;
    }

    @Override
    public long getFrequency()
    {
        SharedPreferences prefs = ContinuousProbe.getPreferences(this._context);

        return Long.parseLong(prefs.getString(GyroscopeProbe.FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));
    }

    @Override
    public String name(Context context)
    {
        return GyroscopeProbe.NAME;
    }

    @Override
    public int getTitleResource()
    {
        return R.string.title_gyroscope_probe;
    }

    @Override
    public boolean isEnabled(Context context)
    {
        SharedPreferences prefs = ContinuousProbe.getPreferences(context);

        this._context = context.getApplicationContext();

        final SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        final Sensor sensor = sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);

        if (super.isEnabled(context))
        {
            if (prefs.getBoolean(GyroscopeProbe.ENABLED, ContinuousProbe.DEFAULT_ENABLED))
            {
                int frequency = Integer.parseInt(prefs.getString(GyroscopeProbe.FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));

                if (this._lastFrequency != frequency)
                {
                    sensors.unregisterListener(this, sensor);

                    if (GyroscopeProbe._handler != null)
                    {
                        Looper loop = GyroscopeProbe._handler.getLooper();
                        loop.quit();

                        GyroscopeProbe._handler = null;
                    }

                    if (frequency != SensorManager.SENSOR_DELAY_FASTEST && frequency != SensorManager.SENSOR_DELAY_UI &&
                            frequency != SensorManager.SENSOR_DELAY_NORMAL)
                    {
                        frequency = SensorManager.SENSOR_DELAY_GAME;
                    }

                    if (prefs.getBoolean(GyroscopeProbe.USE_HANDLER, ContinuousProbe.DEFAULT_USE_HANDLER))
                    {
                        final GyroscopeProbe me = this;
                        final int finalFrequency = frequency;

                        Runnable r = new Runnable()
                        {
                            public void run()
                            {
                                Looper.prepare();

                                GyroscopeProbe._handler = new Handler();

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                                    sensors.registerListener(me, sensor, finalFrequency, 0, GyroscopeProbe._handler);
                                else
                                    sensors.registerListener(me, sensor, finalFrequency, GyroscopeProbe._handler);

                                Looper.loop();
                            }
                        };

                        Thread t = new Thread(r, "gyroscope");
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

                if (GyroscopeProbe._handler != null)
                {
                    Looper loop = GyroscopeProbe._handler.getLooper();
                    loop.quit();

                    GyroscopeProbe._handler = null;
                }
            }
        }
        else
        {
            sensors.unregisterListener(this, sensor);
            this._lastFrequency = -1;

            if (GyroscopeProbe._handler != null)
            {
                Looper loop = GyroscopeProbe._handler.getLooper();
                loop.quit();

                GyroscopeProbe._handler = null;
            }
        }

        return false;

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

        double x = event.values[0];
        double y = event.values[1];
        double z = event.values[2];

        boolean passes = false;

        if (Math.abs(x - this._lastX) >= this.lastThreshold)
            passes = true;
        else if (Math.abs(y - this._lastY) >= this.lastThreshold)
            passes = true;
        else if (Math.abs(z - this._lastZ) >= this.lastThreshold)
            passes = true;

        if (passes)
        {
            this._lastX = x;
            this._lastY = y;
            this._lastZ = z;
        }

        return passes;
    }

    @Override
    public void onSensorChanged(final SensorEvent event)
    {
        if (this.shouldProcessEvent(event) == false)
            return;

        final double now = (double) System.currentTimeMillis();

        synchronized (this) {
            if (this._currentValueBuffer == null) {
                this._bufferCount += 1;

                this._currentValueBuffer = new float[3][BUFFER_SIZE];
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

                for (int i = 0; i < event.values.length; i++)
                {
                    this._currentValueBuffer[i][bufferIndex] = event.values[i];
                }

                final double[] plotValues = { this._currentTimeBuffer[0] / 1000,
                        this._currentValueBuffer[0][bufferIndex],
                        this._currentValueBuffer[1][bufferIndex],
                        this._currentValueBuffer[2][bufferIndex] };

                final GyroscopeProbe me = this;

                if (this._isRefreshing == false) {
                    this._isRefreshing = true;

                    Thread t = new Thread(new Runnable() {
                        @Override
                        public void run() {
                            RealTimeProbeViewActivity.plotIfVisible(me.getTitleResource(), plotValues);

                            me._isRefreshing = false;
                            }
                    });

                    try {
                        t.start();
                    } catch (OutOfMemoryError e) {

                    }
                }

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

                            for (int i = 0; i < fieldNames.length; i++) {
                                data.putFloatArray(fieldNames[i], myValueBuffer[i]);
                            }

                            me.transmitData(me._context, data);

                            if (myTimeBuffer.length > 0) {
                                double x = Double.NaN;
                                double y = Double.NaN;
                                double z = Double.NaN;

                                for (int i = 0; i < fieldNames.length; i++) {
                                    if (fieldNames[i].equals(Continuous3DProbe.X_KEY))
                                        x = myValueBuffer[i][0];
                                    else if (fieldNames[i].equals(Continuous3DProbe.Y_KEY))
                                        y = myValueBuffer[i][0];
                                    else if (fieldNames[i].equals(Continuous3DProbe.Z_KEY))
                                        z = myValueBuffer[i][0];
                                }

                                if (Double.isNaN(x) == false && Double.isNaN(y) == false && Double.isNaN(z) == false) {
                                    Map<String, Object> values = new HashMap<>(4);

                                    values.put(Continuous3DProbe.X_KEY, x);
                                    values.put(Continuous3DProbe.Y_KEY, y);
                                    values.put(Continuous3DProbe.Z_KEY, z);

                                    values.put(ProbeValuesProvider.TIMESTAMP, myTimeBuffer[0] / 1000);

                                    ProbeValuesProvider.getProvider(me._context).insertValue(me._context, GyroscopeProbe.DB_TABLE, me.databaseSchema(), values);
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
    public PreferenceScreen preferenceScreen(Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = super.preferenceScreen(context, manager);

        FlexibleListPreference threshold = new FlexibleListPreference(context);
        threshold.setKey(GyroscopeProbe.THRESHOLD);
        threshold.setDefaultValue(GyroscopeProbe.DEFAULT_THRESHOLD);
        threshold.setEntryValues(R.array.probe_gyroscope_threshold);
        threshold.setEntries(R.array.probe_gyroscope_threshold_labels);
        threshold.setTitle(R.string.probe_noise_threshold_label);
        threshold.setSummary(R.string.probe_noise_threshold_summary);

        screen.addPreference(threshold);

        CheckBoxPreference handler = new CheckBoxPreference(context);
        handler.setTitle(R.string.title_own_sensor_handler);
        handler.setKey(GyroscopeProbe.USE_HANDLER);
        handler.setDefaultValue(ContinuousProbe.DEFAULT_USE_HANDLER);

        screen.addPreference(handler);

        return screen;
    }

    @Override
    public String getPreferenceKey()
    {
        return "gyroscope_built_in";
    }

    @Override
    public String summarizeValue(Context context, Bundle bundle)
    {
        double xReading = bundle.getDoubleArray(Continuous3DProbe.X_KEY)[0];
        double yReading = bundle.getDoubleArray(Continuous3DProbe.Y_KEY)[0];
        double zReading = bundle.getDoubleArray(Continuous3DProbe.Z_KEY)[0];

        return String.format(context.getResources().getString(R.string.summary_gyroscope_probe), xReading, yReading, zReading);
    }

    @Override
    public int getSummaryResource()
    {
        return R.string.summary_gyroscope_probe_desc;
    }

    @Override
    protected String tableName()
    {
        return GyroscopeProbe.DB_TABLE;
    }

    @Override
    protected double getThreshold()
    {
        SharedPreferences prefs = Probe.getPreferences(this._context);
        return Double.parseDouble(prefs.getString(GyroscopeProbe.THRESHOLD, GyroscopeProbe.DEFAULT_THRESHOLD));
    }

    @Override
    protected int getResourceThresholdValues()
    {
        return R.array.probe_gyroscope_threshold;
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

                e.putBoolean(GyroscopeProbe.USE_HANDLER, (Boolean) handler);
                e.apply();
            }
        }
    }
}
