package edu.northwestern.cbits.purple_robot_manager.probes.builtin;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.util.Log;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.probes.Probe;

@SuppressLint("NewApi")
public abstract class BluetoothReceiverProbe extends Probe
{
    public final static String PROBE_NAME = "edu.northwestern.cbits.purple_robot_manager.probes.builtin.BluetoothReceiverProbe";

    private boolean isRegistered = false;

    @Override
    public String name(Context context)
    {
        return "Bluetooth test probe";
    }

    @Override
    public String summary(Context context)
    {
        return "";
    }

    @Override
    public String title(Context context)
    {
        return this.name(context);
    }

    @Override
    public String probeCategory(Context context)
    {
        return "PIZZA PIE";
    }


    @Override
    @SuppressWarnings("deprecation")
    public PreferenceScreen preferenceScreen(Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = super.preferenceScreen(context, manager);

        screen.setTitle(this.title(context));

        return screen;
    }

    @Override
    public void enable(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor e = prefs.edit();
        e.putBoolean("CHANGEME", true);

        e.apply();
    }

    @Override
    public void disable(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor e = prefs.edit();
        e.putBoolean("CHANGEME", false);

        e.apply();
    }

    @Override
    public boolean isEnabled(final Context context)
    {
        final BluetoothReceiverProbe me = this;

        if (!this.isRegistered)
        {
            // BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

            /*
             * BroadcastReceiver mReceiver = new BroadcastReceiver() {
             * 
             * @SuppressLint("NewApi") public void onReceive(Context context,
             * Intent intent) { String action = intent.getAction();
             * 
             * Log.e("PRM", "BT RECEIVER " + action);
             * 
             * if (BluetoothDevice.ACTION_FOUND.equals(action)) { // Get the
             * BluetoothDevice object from the Intent BluetoothDevice device =
             * intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE); // Add
             * the name and address to an array adapter to show in a ListView
             * Log.e("PRM", device.getName() + "\n" + device.getAddress());
             * 
             * device.fetchUuidsWithSdp(); } else
             * if(BluetoothDevice.ACTION_UUID.equals(action)) { BluetoothDevice
             * device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
             * Parcelable[] uuidExtra =
             * intent.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID); for
             * (int i=0; i<uuidExtra.length; i++) { Log.e("PRM", "Device: " +
             * device.getName() + ", " + device + ", Service: " +
             * uuidExtra[i].toString()); } } } }; // Register the
             * BroadcastReceiver IntentFilter filter = new
             * IntentFilter(BluetoothDevice.ACTION_FOUND);
             * filter.addAction(BluetoothDevice.ACTION_UUID);
             * 
             * context.registerReceiver(mReceiver, filter); // Don't forget to
             * unregister during onDestroy
             * 
             * adapter.startDiscovery();
             */

            Thread t = new Thread(new Runnable()
            {
                @Override
                public void run()
                {
                    BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();

                    try
                    {
                        BluetoothServerSocket server = adapter.listenUsingRfcommWithServiceRecord("Test Service", UUID.fromString("0000112f-0000-1000-8000-00805f9b34fb"));
                        // BluetoothServerSocket server =
                        // adapter.listenUsingRfcommWithServiceRecord("Test Service",
                        // UUID.fromString("fa87c0d0-afac-11de-8a39-0800200c9a66"));

                        BluetoothSocket socket = null;

                        while (true)
                        {
                            try
                            {
                                socket = server.accept();
                            }
                            catch (IOException e)
                            {
                                break;
                            }

                            if (socket != null)
                                me.manageSocket(socket);
                        }
                    }
                    catch (IOException e)
                    {
                        LogManager.getInstance(context).logException(e);
                    }
                }
            });

            t.start();

            this.isRegistered = true;
        }
        return true;
    }

    protected void manageSocket(BluetoothSocket socket) throws IOException
    {
        Log.e("PR", "GOT BLUETOOTH CONNECTION: " + socket.getRemoteDevice().getName());

        int read = 0;
        byte[] buffer = new byte[1024];

        InputStream in = socket.getInputStream();

        while ((read = in.read(buffer, 0, buffer.length)) != -1)
        {
            String s = new String(buffer, 0, read);

            Log.e("PR", "READ " + s + " BYTES!");
        }

        socket.close();
    }

    @Override
    public JSONObject fetchSettings(Context context)
    {
        // TODO: Implement!

        return null;
    }

}
