package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.format.Formatter;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.settings.FlexibleListPreference;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

public class NetworkProbe extends Probe
{
    private static final String HOSTNAME = "HOSTNAME";
    private static final String IP_ADDRESS = "IP_ADDRESS";
    private static final String IFACE_NAME = "INTERFACE_NAME";
    private static final String IFACE_DISPLAY_NAME = "INTERFACE_DISPLAY";

    private static final boolean DEFAULT_ENABLED = true;
    private static final String ENABLED = "config_probe_network_enabled";
    private static final String FREQUENCY = "config_probe_network_frequency";

    private long _lastCheck = 0;

    @Override
    public String getPreferenceKey() {
        return "built_in_network";
    }

    @Override
    public String name(Context context)
    {
        return "edu.northwestern.cbits.purple_robot_manager.probes.builtin.NetworkProbe";
    }

    @Override
    public String title(Context context)
    {
        return context.getString(R.string.title_network_probe);
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
        e.putBoolean(NetworkProbe.ENABLED, true);

        e.apply();
    }

    @Override
    public void disable(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor e = prefs.edit();
        e.putBoolean(NetworkProbe.ENABLED, false);

        e.apply();
    }

    @Override
    public boolean isEnabled(final Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        if (super.isEnabled(context))
        {
            final long now = System.currentTimeMillis();

            if (prefs.getBoolean(NetworkProbe.ENABLED, NetworkProbe.DEFAULT_ENABLED))
            {
                synchronized (this)
                {
                    long freq = Long.parseLong(prefs.getString(NetworkProbe.FREQUENCY, Probe.DEFAULT_FREQUENCY));

                    if (now - this._lastCheck > freq)
                    {
                        final NetworkProbe me = this;

                        Runnable r = new Runnable()
                        {
                            @Override
                            @SuppressWarnings("deprecation")
                            public void run()
                            {
                                Bundle bundle = new Bundle();
                                bundle.putString("PROBE", me.name(context));
                                bundle.putLong("TIMESTAMP", System.currentTimeMillis() / 1000);

                                WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                                WifiInfo wifiInfo = wifiManager.getConnectionInfo();

                                if (wifiInfo != null)
                                {
                                    int ip = wifiInfo.getIpAddress();

                                    String ipString = Formatter.formatIpAddress(ip);

                                    bundle.putString(NetworkProbe.IP_ADDRESS, ipString);

                                    try
                                    {
                                        NetworkInterface iface = NetworkInterface.getByInetAddress(InetAddress.getByName(ipString));

                                        bundle.putString(NetworkProbe.IFACE_NAME, iface.getName());
                                        bundle.putString(NetworkProbe.IFACE_DISPLAY_NAME, iface.getDisplayName());

                                        bundle.putString(NetworkProbe.HOSTNAME, InetAddress.getByName(ipString).getHostName());
                                    }
                                    catch (UnknownHostException | NullPointerException e)
                                    {
                                        bundle.putString(NetworkProbe.HOSTNAME, ipString);
                                    } catch (SocketException e)
                                    {
                                        e.printStackTrace();
                                    }
                                }
                                else
                                {
                                    try
                                    {
                                        Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();

                                        NetworkInterface iface = null;

                                        while (ifaces.hasMoreElements() && (iface = ifaces.nextElement()) != null && bundle.containsKey(NetworkProbe.IP_ADDRESS) == false)
                                        {
                                            if (iface.getName().equals("lo") == false)
                                            {
                                                Enumeration<InetAddress> ips = iface.getInetAddresses();
                                                InetAddress ipAddr = null;

                                                while (ips.hasMoreElements() && (ipAddr = ips.nextElement()) != null)
                                                {
                                                    bundle.putString(NetworkProbe.IP_ADDRESS, ipAddr.getHostAddress());
                                                    bundle.putString(NetworkProbe.HOSTNAME, ipAddr.getHostName());

                                                    bundle.putString(NetworkProbe.IFACE_NAME, iface.getName());
                                                    bundle.putString(NetworkProbe.IFACE_DISPLAY_NAME, iface.getDisplayName());
                                                }
                                            }
                                        }
                                    }
                                    catch (SocketException e)
                                    {
                                        LogManager.getInstance(context).logException(e);
                                    }
                                }

                                if (bundle.containsKey(NetworkProbe.IP_ADDRESS) == false)
                                {
                                    bundle.putString(NetworkProbe.IP_ADDRESS, "127.0.0.1");
                                    bundle.putString(NetworkProbe.HOSTNAME, "localhost");

                                    try {
                                        NetworkInterface iface = NetworkInterface.getByInetAddress(InetAddress.getByName("127.0.0.1"));

                                        bundle.putString(NetworkProbe.IFACE_NAME, iface.getName());
                                        bundle.putString(NetworkProbe.IFACE_DISPLAY_NAME, iface.getDisplayName());
                                    } catch (SocketException | UnknownHostException e) {
                                        LogManager.getInstance(context).logException(e);
                                    }
                                }

                                me.transmitData(context, bundle);

                                me._lastCheck = now;
                            }
                        };

                        Thread t = new Thread(r);
                        t.start();
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
        String ipAddress = bundle.getString(NetworkProbe.IP_ADDRESS);

        return String.format(context.getResources().getString(R.string.summary_network_probe), ipAddress);
    }

    @Override
    public Map<String, Object> configuration(Context context)
    {
        Map<String, Object> map = super.configuration(context);

        SharedPreferences prefs = Probe.getPreferences(context);

        try
        {
            long freq = Long.parseLong(prefs.getString(NetworkProbe.FREQUENCY, Probe.DEFAULT_FREQUENCY));

            map.put(Probe.PROBE_FREQUENCY, freq);
        }
        catch (NumberFormatException e)
        {
            LogManager.getInstance(context).logException(e);
        }

        return map;
    }

    @Override
    public void updateFromMap(Context context, Map<String, Object> params)
    {
        super.updateFromMap(context, params);

        if (params.containsKey(Probe.PROBE_FREQUENCY))
        {
            Object frequency = params.get(Probe.PROBE_FREQUENCY);

            if ((frequency instanceof Double) == false)
                frequency = Double.valueOf(frequency.toString()).longValue();
            else
                frequency = ((Double) frequency).longValue();

            SharedPreferences prefs = Probe.getPreferences(context);
            Editor e = prefs.edit();

            e.putString(NetworkProbe.FREQUENCY, frequency.toString());
            e.apply();
        }
    }

    @Override
    public String summary(Context context)
    {
        return context.getString(R.string.summary_network_probe_desc);
    }

    @Override
    @SuppressWarnings("deprecation")
    public PreferenceScreen preferenceScreen(Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = super.preferenceScreen(context, manager);

        screen.setTitle(this.title(context));
        screen.setSummary(R.string.summary_network_probe_desc);

        CheckBoxPreference enabled = new CheckBoxPreference(context);
        enabled.setTitle(R.string.title_enable_probe);
        enabled.setKey(NetworkProbe.ENABLED);
        enabled.setDefaultValue(NetworkProbe.DEFAULT_ENABLED);

        screen.addPreference(enabled);

        FlexibleListPreference duration = new FlexibleListPreference(context);
        duration.setKey(NetworkProbe.FREQUENCY);
        duration.setEntryValues(R.array.probe_satellite_frequency_values);
        duration.setEntries(R.array.probe_satellite_frequency_labels);
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

            String[] options = context.getResources().getStringArray(R.array.probe_satellite_frequency_values);

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
        return "network-probe.html";
    }
}
