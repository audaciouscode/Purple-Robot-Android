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
public class RotationProbe extends ContinuousProbe implements SensorEventListener
{
    private static final int BUFFER_SIZE = 1024;

    public static final String DB_TABLE = "rotation_probe";

    private static final String COSINE = "COSINE";
    private static final String ACCURACY = "ACCURACY";

    private static final String[] fieldNames = { Continuous3DProbe.X_KEY, Continuous3DProbe.Y_KEY, Continuous3DProbe.Z_KEY, COSINE, ACCURACY };

    protected static final String DEFAULT_THRESHOLD = "15.0";

    public static final String NAME = "edu.northwestern.cbits.purple_robot_manager.probes.builtin.RotationProbe";

    protected static final String THRESHOLD = "config_probe_rotation_built_in_threshold";
    private static final String FREQUENCY = "config_probe_rotation_built_in_frequency";
    private static final String ENABLED = "config_probe_rotation_built_in_enabled";
    private static final String USE_HANDLER = "config_probe_rotation_built_in_handler";

    private long lastThresholdLookup = 0;
    private double lastThreshold = 0.5;

    private double _lastX = Double.MAX_VALUE;
    private double _lastY = Double.MAX_VALUE;
    private double _lastZ = Double.MAX_VALUE;
    private double _lastC = Double.MAX_VALUE;

    private float[][] _currentValueBuffer = null;
    private int[] _currentAccuracyBuffer = null;
    private double[] _currentTimeBuffer = null;
    private double[] _currentSensorTimeBuffer = null;

    private int _bufferCount = 0;

    private Map<String, String> _schema = null;

    protected int _lastFrequency = -1;

    protected int bufferIndex = 0;

    private static Handler _handler = null;

    @Override
    public boolean getUsesThread()
    {
        SharedPreferences prefs = ContinuousProbe.getPreferences(this._context);

        return prefs.getBoolean(RotationProbe.USE_HANDLER, RotationProbe.DEFAULT_USE_HANDLER);
    }

    @Override
    public Intent viewIntent(Context context)
    {
        Intent i = new Intent(context, WebkitLandscapeActivity.class);

        return i;
    }

    @Override
    public String probeCategory(Context context)
    {
        return context.getString(R.string.probe_sensor_category);
    }

    @Override
    public String contentSubtitle(Context context)
    {
        Cursor c = ProbeValuesProvider.getProvider(context).retrieveValues(context, RotationProbe.DB_TABLE, this.databaseSchema());

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
        if (this._schema == null)
        {
            this._schema = new HashMap<>();

            this._schema.put(Continuous3DProbe.X_KEY, ProbeValuesProvider.REAL_TYPE);
            this._schema.put(Continuous3DProbe.Y_KEY, ProbeValuesProvider.REAL_TYPE);
            this._schema.put(Continuous3DProbe.Z_KEY, ProbeValuesProvider.REAL_TYPE);
            this._schema.put(RotationProbe.COSINE, ProbeValuesProvider.REAL_TYPE);
            this._schema.put(RotationProbe.ACCURACY, ProbeValuesProvider.REAL_TYPE);
        }

        return this._schema;
    }

    @Override
    public String getDisplayContent(Activity activity)
    {
        try
        {
            String template = WebkitActivity.stringForAsset(activity, "webkit/chart_spline_full.html");

            ArrayList<Double> x = new ArrayList<>();
            ArrayList<Double> y = new ArrayList<>();
            ArrayList<Double> z = new ArrayList<>();
            ArrayList<Double> c = new ArrayList<>();
            ArrayList<Double> a = new ArrayList<>();
            ArrayList<Double> time = new ArrayList<>();

            Cursor cursor = ProbeValuesProvider.getProvider(activity).retrieveValues(activity, RotationProbe.DB_TABLE, this.databaseSchema());

            int count = -1;

            if (cursor != null)
            {
                count = cursor.getCount();

                while (cursor.moveToNext())
                {
                    double xd = cursor.getDouble(cursor.getColumnIndex(Continuous3DProbe.X_KEY));
                    double yd = cursor.getDouble(cursor.getColumnIndex(Continuous3DProbe.Y_KEY));
                    double zd = cursor.getDouble(cursor.getColumnIndex(Continuous3DProbe.Z_KEY));
                    double cd = cursor.getDouble(cursor.getColumnIndex(RotationProbe.COSINE));
                    double ad = cursor.getDouble(cursor.getColumnIndex(RotationProbe.ACCURACY));

                    double t = cursor.getDouble(cursor.getColumnIndex(ProbeValuesProvider.TIMESTAMP));

                    x.add(xd);
                    y.add(yd);
                    z.add(zd);
                    c.add(cd);
                    a.add(ad);

                    time.add(t);
                }

                cursor.close();
            }

            SplineChart ch = new SplineChart();
            ch.addSeries("X", x);
            ch.addSeries("Y", y);
            ch.addSeries("Z", z);
            ch.addSeries("Cosine", c);
            ch.addSeries("Accuracy", a);

            ch.addTime("tIME", time);

            JSONObject json = ch.dataJson(activity);

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
        double[] x = bundle.getDoubleArray(Continuous3DProbe.X_KEY);
        double[] y = bundle.getDoubleArray(Continuous3DProbe.Y_KEY);
        double[] z = bundle.getDoubleArray(Continuous3DProbe.Z_KEY);
        double[] c = bundle.getDoubleArray(RotationProbe.COSINE);
        double[] a = bundle.getDoubleArray(RotationProbe.ACCURACY);

        ArrayList<String> keys = new ArrayList<>();

        SimpleDateFormat sdf = new SimpleDateFormat(context.getString(R.string.display_date_format));

        if (x == null || y == null || z == null || c == null || a == null)
        {

        }
        else if (eventTimes.length > 1)
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

            formatted.putBundle(context.getString(R.string.display_rotation_readings), readings);
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

        return Long.parseLong(prefs.getString(RotationProbe.FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));
    }

    @Override
    public String name(Context context)
    {
        return RotationProbe.NAME;
    }

    @Override
    public int getTitleResource()
    {
        return R.string.title_rotation_probe;
    }

    @Override
    @SuppressLint("InlinedApi")
    public boolean isEnabled(Context context)
    {
        if (Build.VERSION.SDK_INT < 9)
            return false;

        SharedPreferences prefs = ContinuousProbe.getPreferences(context);

        this._context = context.getApplicationContext();

        final SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        final Sensor sensor = sensors.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        if (super.isEnabled(context))
        {
            if (prefs.getBoolean(RotationProbe.ENABLED, ContinuousProbe.DEFAULT_ENABLED))
            {
                int frequency = Integer.parseInt(prefs.getString(RotationProbe.FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));

                if (this._lastFrequency != frequency)
                {
                    sensors.unregisterListener(this, sensor);

                    if (RotationProbe._handler != null)
                    {
                        Looper loop = RotationProbe._handler.getLooper();
                        loop.quit();

                        RotationProbe._handler = null;
                    }

                    if (frequency != SensorManager.SENSOR_DELAY_FASTEST && frequency != SensorManager.SENSOR_DELAY_UI &&
                            frequency != SensorManager.SENSOR_DELAY_NORMAL)
                    {
                        frequency = SensorManager.SENSOR_DELAY_GAME;
                    }

                    if (prefs.getBoolean(RotationProbe.USE_HANDLER, ContinuousProbe.DEFAULT_USE_HANDLER))
                    {
                        final RotationProbe me = this;
                        final int finalFrequency = frequency;

                        Runnable r = new Runnable()
                        {
                            public void run()
                            {
                                Looper.prepare();

                                RotationProbe._handler = new Handler();

                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT)
                                    sensors.registerListener(me, sensor, finalFrequency, 0, RotationProbe._handler);
                                else
                                    sensors.registerListener(me, sensor, finalFrequency, RotationProbe._handler);

                                Looper.loop();
                            }
                        };

                        Thread t = new Thread(r, "rotation");
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

                if (RotationProbe._handler != null)
                {
                    Looper loop = RotationProbe._handler.getLooper();
                    loop.quit();

                    RotationProbe._handler = null;
                }
            }
        }
        else
        {
            sensors.unregisterListener(this, sensor);
            this._lastFrequency = -1;

            if (RotationProbe._handler != null)
            {
                Looper loop = RotationProbe._handler.getLooper();
                loop.quit();

                RotationProbe._handler = null;
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
        double c = event.values[2];

        boolean passes = false;

        if (Math.abs(x - this._lastX) >= this.lastThreshold)
            passes = true;
        else if (Math.abs(y - this._lastY) >= this.lastThreshold)
            passes = true;
        else if (Math.abs(z - this._lastZ) >= this.lastThreshold)
            passes = true;
        else if (Math.abs(c - this._lastC) >= this.lastThreshold)
            passes = true;

        if (passes)
        {
            this._lastX = x;
            this._lastY = y;
            this._lastZ = z;
            this._lastC = c;
        }

        return passes;
    }

    @Override
    public PreferenceScreen preferenceScreen(Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = super.preferenceScreen(context, manager);

        FlexibleListPreference threshold = new FlexibleListPreference(context);
        threshold.setKey(RotationProbe.THRESHOLD);
        threshold.setDefaultValue(RotationProbe.DEFAULT_THRESHOLD);
        threshold.setEntryValues(R.array.probe_rotation_threshold);
        threshold.setEntries(R.array.probe_rotation_threshold_labels);
        threshold.setTitle(R.string.probe_noise_threshold_label);
        threshold.setSummary(R.string.probe_noise_threshold_summary);

        screen.addPreference(threshold);

        CheckBoxPreference handler = new CheckBoxPreference(context);
        handler.setTitle(R.string.title_own_sensor_handler);
        handler.setKey(RotationProbe.USE_HANDLER);
        handler.setDefaultValue(ContinuousProbe.DEFAULT_USE_HANDLER);

        screen.addPreference(handler);

        return screen;
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

                this._currentValueBuffer = new float[5][BUFFER_SIZE];
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

                    final RotationProbe me = this;

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
                                Double x = null;
                                Double y = null;
                                Double z = null;
                                Double c = null;
                                Double a = null;

                                for (int i = 0; i < fieldNames.length; i++)
                                {
                                    if (fieldNames[i].equals(Continuous3DProbe.X_KEY))
                                        x = (double) myValueBuffer[i][j];
                                    else if (fieldNames[i].equals(Continuous3DProbe.Y_KEY))
                                        y = (double) myValueBuffer[i][j];
                                    else if (fieldNames[i].equals(Continuous3DProbe.Z_KEY))
                                        z = (double) myValueBuffer[i][j];
                                    else if (fieldNames[i].equals(RotationProbe.COSINE))
                                        c = (double) myValueBuffer[i][j];
                                    else if (fieldNames[i].equals(RotationProbe.ACCURACY))
                                        a = (double) myValueBuffer[i][j];
                                }

                                if (x != null && y != null && z != null)
                                {
                                    Map<String, Object> values = new HashMap<>();

                                    values.put(Continuous3DProbe.X_KEY, x);
                                    values.put(Continuous3DProbe.Y_KEY, y);
                                    values.put(Continuous3DProbe.Z_KEY, z);
                                    values.put(RotationProbe.COSINE, c);
                                    values.put(RotationProbe.ACCURACY, a);

                                    values.put(ProbeValuesProvider.TIMESTAMP, myTimeBuffer[j] / 1000);

                                    ProbeValuesProvider.getProvider(me._context).insertValue(me._context, me.dbTable(), me.databaseSchema(), values);
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

    protected String dbTable()
    {
        return RotationProbe.DB_TABLE;
    }

    @Override
    public String getPreferenceKey()
    {
        return "rotation_built_in";
    }

    @Override
    public String summarizeValue(Context context, Bundle bundle)
    {
        double xReading = bundle.getDoubleArray(Continuous3DProbe.X_KEY)[0];
        double yReading = bundle.getDoubleArray(Continuous3DProbe.Y_KEY)[0];
        double zReading = bundle.getDoubleArray(Continuous3DProbe.Z_KEY)[0];
        double cReading = bundle.getDoubleArray(RotationProbe.COSINE)[0];

        return String.format(context.getResources().getString(R.string.summary_rotation_probe), xReading, yReading, zReading, cReading);
    }

    @Override
    public int getSummaryResource()
    {
        return R.string.summary_rotation_probe_desc;
    }

    public boolean isSuperEnabled(Context context)
    {
        return super.isEnabled(context);
    }

    @Override
    protected double getThreshold()
    {
        SharedPreferences prefs = Probe.getPreferences(this._context);

        return Double.parseDouble(prefs.getString(RotationProbe.THRESHOLD, RotationProbe.DEFAULT_THRESHOLD));
    }

    @Override
    protected int getResourceThresholdValues()
    {
        return R.array.probe_rotation_threshold;
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
            settings.put(RotationProbe.USE_HANDLER, handler);
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

        if (params.containsKey(RotationProbe.USE_HANDLER))
        {
            Object handler = params.get(RotationProbe.USE_HANDLER);

            if (handler instanceof Boolean)
            {
                SharedPreferences prefs = Probe.getPreferences(context);
                SharedPreferences.Editor e = prefs.edit();

                e.putBoolean(RotationProbe.USE_HANDLER, (Boolean) handler);
                e.apply();
            }
        }
    }
}
