package edu.northwestern.cbits.purple_robot_manager.probes.devices;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.hardware.SensorEvent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import com.getpebble.android.kit.PebbleKit;
import com.getpebble.android.kit.PebbleKit.FirmwareVersionInfo;
import com.getpebble.android.kit.PebbleKit.PebbleDataLogReceiver;
import com.getpebble.android.kit.util.PebbleDictionary;

import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.RealTimeProbeViewActivity;
import edu.northwestern.cbits.purple_robot_manager.calibration.PebbleCalibrationHelper;
import edu.northwestern.cbits.purple_robot_manager.db.ProbeValuesProvider;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.Continuous3DProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.ContinuousProbe;

public class PebbleProbe extends Continuous3DProbe
{
    public final static String PROBE_NAME = "edu.northwestern.cbits.purple_robot_manager.probes.devices.PebbleProbe";

    private static final String FIRMWARE_VERSION = "FIRMWARE_VERSION";
    private static final String BUNDLE_IS_CHARGING = "IS_CHARGING";
    private static final String BUNDLE_CHARGE_LEVEL = "CHARGE_LEVEL";
    private static final byte COMMAND_FETCH_BATTERY = 0x00;

    private static UUID WATCHAPP_UUID = UUID.fromString("3cab0453-ff04-4594-8223-fa357112c305");

    public static final String ENABLED = "config_probe_pebble_enabled";
    public static final boolean DEFAULT_ENABLED = false;

    private static final int BUFFER_SIZE = 20;

    private static final String DB_TABLE = "pebble_probe";

    private final double valueBuffer[][] = new double[3][BUFFER_SIZE];
    private final double timeBuffer[] = new double[BUFFER_SIZE];

    private PebbleDataLogReceiver _logReceiver = null;
    private int _index = 0;

    private Map<String, String> _schema = null;

    private PebbleKit.PebbleDataReceiver _messageReceiver = null;
    private PebbleKit.PebbleNackReceiver _nackReceiver = null;
    private PebbleKit.PebbleAckReceiver _ackReceiver = null;
    private boolean _isCharging = false;
    private int _chargeLevel = -1;
    private long _lastRefresh = 0;

    @Override
    public boolean getUsesThread()
    {
        return false;
    }

    private static class AccelData
    {
        // TODO: Credit https://github.com/kramimus/pebble-accel-analyzer

        final private int x;
        final private int y;
        final private int z;

        private long timestamp = 0;
        final private boolean didVibrate;

        public AccelData(byte[] data)
        {
            x = (data[0] & 0xff) | (data[1] << 8);
            y = (data[2] & 0xff) | (data[3] << 8);
            z = (data[4] & 0xff) | (data[5] << 8);
            didVibrate = data[6] != 0;

            for (int i = 0; i < 8; i++)
            {
                timestamp |= ((long) (data[i + 7] & 0xff)) << (i * 8);
            }
        }

        @SuppressWarnings("unused")
        public JSONObject toJson(Context context)
        {
            JSONObject json = new JSONObject();

            try
            {
                json.put("x", x);
                json.put("y", y);
                json.put("z", z);
                json.put("ts", timestamp);
                json.put("v", didVibrate);

                return json;
            }
            catch (JSONException e)
            {
                LogManager.getInstance(context).logException(e);
            }

            return null;
        }

        public static List<AccelData> fromDataArray(byte[] data)
        {
            List<AccelData> accels = new ArrayList<>();

            for (int i = 0; i < data.length; i += 15)
            {
                accels.add(new AccelData(Arrays.copyOfRange(data, i, i + 15)));
            }
            return accels;
        }

        public long getTimestamp()
        {
            return timestamp;
        }

        public void applyTimezone(TimeZone tz)
        {
            timestamp -= tz.getOffset(timestamp);
        }
    }

    @Override
    public String name(Context context)
    {
        return "edu.northwestern.cbits.purple_robot_manager.probes.devices.PebbleProbe";
    }

    @Override
    public String probeCategory(Context context)
    {
        return context.getResources().getString(R.string.probe_other_devices_category);
    }

    @Override
    @SuppressWarnings("deprecation")
    public PreferenceScreen preferenceScreen(final Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = super.preferenceScreen(context, manager, false);

        screen.setTitle(this.title(context));
        screen.setSummary(R.string.summary_pebble_probe_desc);

        CheckBoxPreference enabled = new CheckBoxPreference(context);
        enabled.setTitle(R.string.title_enable_probe);
        enabled.setKey(PebbleProbe.ENABLED);
        enabled.setDefaultValue(PebbleProbe.DEFAULT_ENABLED);

        screen.addPreference(enabled);

        Preference installWatchApp = new Preference(context);
        installWatchApp.setTitle(R.string.probe_pebble_install_label);
        installWatchApp.setIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(context.getString(R.string.probe_pebble_install_url))));

        screen.addPreference(installWatchApp);

        return screen;
    }

    @Override
    public void enable(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor e = prefs.edit();
        e.putBoolean(PebbleProbe.ENABLED, true);

        e.apply();
    }

    @Override
    public void disable(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor e = prefs.edit();
        e.putBoolean(PebbleProbe.ENABLED, false);

        e.apply();
    }

    @Override
    public boolean isEnabled(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        if (super.isEnabled(context))
        {
            if (prefs.getBoolean(PebbleProbe.ENABLED, PebbleProbe.DEFAULT_ENABLED))
            {
                PebbleCalibrationHelper.check(context, true);

                PebbleKit.startAppOnPebble(context, PebbleProbe.WATCHAPP_UUID);

                if (this._logReceiver == null)
                {
                    final PebbleProbe me = this;

                    this._logReceiver = new PebbleDataLogReceiver(WATCHAPP_UUID)
                    {
                        @Override
                        public void receiveData(final Context context, UUID logUuid, final Long timestamp, final Long tag, final byte[] payload)
                        {
                            synchronized (me)
                            {
                                TimeZone here = Calendar.getInstance().getTimeZone();

                                List<AccelData> accels = AccelData.fromDataArray(payload);

                                for (AccelData accel : accels)
                                {
                                    if (me._index >= PebbleProbe.BUFFER_SIZE)
                                    {
                                        long now = System.currentTimeMillis();

                                        Bundle data = new Bundle();
                                        data.putDouble(Probe.BUNDLE_TIMESTAMP, now / 1000);
                                        data.putString(Probe.BUNDLE_PROBE, me.name(me._context));

                                        data.putBoolean(PebbleProbe.BUNDLE_IS_CHARGING, me._isCharging);
                                        data.putInt(PebbleProbe.BUNDLE_CHARGE_LEVEL, me._chargeLevel);

                                        FirmwareVersionInfo info = PebbleKit.getWatchFWVersion(context);

                                        if (info != null)
                                            data.putString(PebbleProbe.FIRMWARE_VERSION, "" + info.getMajor() + "." + info.getMinor() + "." + info.getPoint());

                                        data.putDoubleArray(ContinuousProbe.SENSOR_TIMESTAMP, timeBuffer);

                                        for (int i = 0; i < fieldNames.length; i++)
                                        {
                                            data.putDoubleArray(fieldNames[i], valueBuffer[i]);
                                        }

                                        me.transmitData(context, data);

                                        me._index = 0;
                                    }

                                    accel.applyTimezone(here);

                                    timeBuffer[me._index] = accel.getTimestamp();

                                    double x = 9.807 * ((double) accel.x) / 1000;
                                    double y = 9.807 * ((double) accel.y) / 1000;
                                    double z = 9.807 * ((double) accel.z) / 1000;

                                    valueBuffer[0][me._index] = x;
                                    valueBuffer[1][me._index] = y;
                                    valueBuffer[2][me._index] = z;

                                    if (me._index % 10 == 0)
                                    {
                                        Map<String, Object> values = new HashMap<>(4);

                                        values.put(Continuous3DProbe.X_KEY, x);
                                        values.put(Continuous3DProbe.Y_KEY, y);
                                        values.put(Continuous3DProbe.Z_KEY, z);

                                        values.put(ProbeValuesProvider.TIMESTAMP, (double) (accel.getTimestamp() / 1000));

                                        ProbeValuesProvider.getProvider(context).insertValue(context, PebbleProbe.DB_TABLE, me.databaseSchema(), values);

                                        double[] plotValues = { timeBuffer[0] / 1000, x, y, z };

                                        RealTimeProbeViewActivity.plotIfVisible(me.getTitleResource(), plotValues);
                                    }

                                    me._index += 1;
                                }
                            }
                        }
                    };

                    PebbleKit.registerDataLogReceiver(context, this._logReceiver);
                }

                if (this._ackReceiver == null)
                {
                    this._ackReceiver = new PebbleKit.PebbleAckReceiver(PebbleProbe.WATCHAPP_UUID)
                    {
                        public void receiveAck(Context context, int i)
                        {

                        }
                    };

                    PebbleKit.registerReceivedAckHandler(context, this._ackReceiver);
                }

                if (this._nackReceiver == null)
                {
                    this._nackReceiver = new PebbleKit.PebbleNackReceiver(PebbleProbe.WATCHAPP_UUID)
                    {
                        public void receiveNack(Context context, int i)
                        {

                        }
                    };

                    PebbleKit.registerReceivedNackHandler(context, this._nackReceiver);
                }

                if (this._messageReceiver == null)
                {
                    final PebbleProbe me = this;

                    this._messageReceiver =  new PebbleKit.PebbleDataReceiver(PebbleProbe.WATCHAPP_UUID)
                    {
                        public void receiveData(final Context context, final int transactionId, final PebbleDictionary dictionary)
                        {
                            PebbleKit.sendAckToPebble(context, transactionId);

                            byte[] payload = dictionary.getBytes(1);

                            me._isCharging = ((payload[0] & 0x80) == 0x80);
                            me._chargeLevel = payload[0] & 0x7f;
                        }
                    };

                    PebbleKit.registerReceivedDataHandler(context, this._messageReceiver);
                }

                long now = System.currentTimeMillis();

                if (now - this._lastRefresh > 5 * 60 * 1000)
                {
                    PebbleDictionary data = new PebbleDictionary();
                    data.addUint8(0, PebbleProbe.COMMAND_FETCH_BATTERY);

                    PebbleKit.sendDataToPebble(context, PebbleProbe.WATCHAPP_UUID, data);
                }

                return true;
            }

            if (this._logReceiver != null)
            {
                try
                {
                    context.unregisterReceiver(this._logReceiver);
                }
                catch (IllegalArgumentException e)
                {
                    // Do nothing - receiver not registered...
                }

                this._logReceiver = null;
            }
        }

        PebbleCalibrationHelper.check(context, false);

        return false;
    }

    @Override
    protected String tableName()
    {
        return PebbleProbe.DB_TABLE;
    }

    @Override
    protected Map<String, String> databaseSchema()
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
    public long getFrequency()
    {
        return 0;
    }

    @Override
    public int getTitleResource()
    {
        return R.string.title_pebble_probe;
    }

    @Override
    public int getSummaryResource()
    {
        return R.string.summary_pebble_probe_desc;
    }

    @Override
    public String getPreferenceKey()
    {
        return "pebble";
    }

    @Override
    protected boolean passesThreshold(SensorEvent event)
    {
        return true;
    }

    @Override
    public String summarizeValue(Context context, Bundle bundle)
    {
        double xReading = bundle.getDoubleArray("X")[0];
        double yReading = bundle.getDoubleArray("Y")[0];
        double zReading = bundle.getDoubleArray("Z")[0];

        return String.format(context.getResources().getString(R.string.summary_accelerator_probe), xReading, yReading, zReading);
    }

    @Override
    protected double getThreshold()
    {
        return 0;
    }

    @Override
    protected int getResourceThresholdValues()
    {
        return -1;
    }

    @Override
    public int getResourceFrequencyValues()
    {
        return -1;
    }
}
