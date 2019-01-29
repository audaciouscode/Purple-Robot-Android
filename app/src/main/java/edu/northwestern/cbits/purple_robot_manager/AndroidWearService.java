package edu.northwestern.cbits.purple_robot_manager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.NonNull;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.data.FreezableUtils;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.logging.SanityCheck;
import edu.northwestern.cbits.purple_robot_manager.logging.SanityManager;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.ContinuousProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.AndroidWearProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.wear.WearBatteryProbe;

public class AndroidWearService extends WearableListenerService
{
    private static final String PATH_REQUEST_DATA = "/purple-robot/request-data";
    private static final String PATH_SEND_CONFIG = "/purple-robot/send-config";
    private static final String URI_CRASH_REPORT = "/purple-robot-crash";

    private static GoogleApiClient _apiClient = null;

    private static void initClient(final Context context)
    {
        if (AndroidWearService._apiClient == null)
        {
            Runnable r = new Runnable()
            {
                public void run()
                {
                    GoogleApiClient.Builder builder = new GoogleApiClient.Builder(context);
                    builder.addApi(Wearable.API);

                    AndroidWearService._apiClient = builder.build();

                    ConnectionResult connectionResult = AndroidWearService._apiClient.blockingConnect(30, TimeUnit.SECONDS);

                    if (!connectionResult.isSuccess())
                    {
                        // Log.e("PR", "Failed to connect to GoogleApiClient.");
                    }
                    else
                        AndroidWearService.requestDataFromDevices(context);
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }

    public static void requestDataFromDevices(final Context context)
    {
        Runnable r = new Runnable()
        {
            public void run()
            {
                if (AndroidWearService._apiClient == null)
                    AndroidWearService.initClient(context);

                if (AndroidWearService._apiClient != null && AndroidWearService._apiClient.isConnected())
                {
                    NodeApi.GetConnectedNodesResult nodes = Wearable.NodeApi.getConnectedNodes(AndroidWearService._apiClient).await();

                    byte[] config = AndroidWearService.byteConfig(context);

                    for (Node node : nodes.getNodes())
                    {
                        PendingResult<MessageApi.SendMessageResult> result = Wearable.MessageApi.sendMessage(AndroidWearService._apiClient, node.getId(), PATH_SEND_CONFIG, config);

                        result.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>()
                        {
                            @Override
                            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult)
                            {
                                if (!sendMessageResult.getStatus().isSuccess())
                                {
                                    Log.e("PR", "Failed to send message (CONFIG) with status code: " + sendMessageResult.getStatus().getStatusCode());
                                }
                            }
                        });

                        result = Wearable.MessageApi.sendMessage(AndroidWearService._apiClient, node.getId(), PATH_REQUEST_DATA, new byte[0]);

                        result.setResultCallback(new ResultCallback<MessageApi.SendMessageResult>()
                        {
                            @Override
                            public void onResult(@NonNull MessageApi.SendMessageResult sendMessageResult)
                            {
                                if (!sendMessageResult.getStatus().isSuccess())
                                {
                                    Log.e("PR", "Failed to send message (DATA REQUEST) with status code: " + sendMessageResult.getStatus().getStatusCode());
                                }
                            }
                        });
                    }
                }
            }
        };

        Thread t = new Thread(r);
        t.start();
    }

    private static byte[] byteConfig(Context context)
    {
        byte[] config = new byte[12];

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        if (prefs.getBoolean(AndroidWearProbe.ACCELEROMETER_ENABLED, AndroidWearProbe.ACCELEROMETER_DEFAULT_ENABLED))
            config[0] = 0x01;
        else
            config[0] = 0x00;

        config[1] = Byte.parseByte(prefs.getString(AndroidWearProbe.ACCELEROMETER_FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));

        if (prefs.getBoolean(AndroidWearProbe.GYROSCOPE_ENABLED, AndroidWearProbe.GYROSCOPE_DEFAULT_ENABLED))
            config[2] = 0x01;
        else
            config[2] = 0x00;

        config[3] = Byte.parseByte(prefs.getString(AndroidWearProbe.GYROSCOPE_FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));

        if (prefs.getBoolean(AndroidWearProbe.MAGNETOMETER_ENABLED, AndroidWearProbe.MAGNETOMETER_DEFAULT_ENABLED))
            config[4] = 0x01;
        else
            config[4] = 0x00;

        config[5] = Byte.parseByte(prefs.getString(AndroidWearProbe.MAGNETOMETER_FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));

        if (prefs.getBoolean(AndroidWearProbe.LIGHT_METER_ENABLED, AndroidWearProbe.LIGHT_METER_DEFAULT_ENABLED))
            config[6] = 0x01;
        else
            config[6] = 0x00;

        config[7] = Byte.parseByte(prefs.getString(AndroidWearProbe.LIGHT_METER_FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));

        if (prefs.getBoolean(AndroidWearProbe.HEART_METER_ENABLED, AndroidWearProbe.HEART_METER_DEFAULT_ENABLED))
            config[8] = 0x01;
        else
            config[8] = 0x00;

        config[9] = Byte.parseByte(prefs.getString(AndroidWearProbe.HEART_METER_FREQUENCY, ContinuousProbe.DEFAULT_FREQUENCY));

        config[10] = 0x00;
        config[11] = 0x00;
//        if (prefs.getBoolean(AndroidWearProbe.LIVEWELL_COUNTS_ENABLED, AndroidWearProbe.LIVEWELL_COUNTS_DEFAULT_ENABLED))
//            config[10] = 0x01;
//        else
//            config[10] = 0x00;
//
//        config[11] = Byte.parseByte(prefs.getString(AndroidWearProbe.LIVEWELL_BIN_SIZE, AndroidWearProbe.LIVEWELL_DEFAULT_BIN_SIZE));

        return config;
    }

    public void onDataChanged(DataEventBuffer buffer)
    {
        if (AndroidWearService._apiClient.isConnected())
        {
            final List<DataEvent> events = FreezableUtils.freezeIterable(buffer);
            final AndroidWearService me = this;

            Runnable r = new Runnable()
            {
                public void run()
                {
                    long sleepInterval = 2000 / events.size();

                    for (DataEvent event : events)
                    {
                        if (event.getType() == DataEvent.TYPE_CHANGED)
                        {
                            DataItem item = event.getDataItem();

                            if (item.getUri().getPath().startsWith(AndroidWearProbe.URI_READING_PREFIX))
                            {
                                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                                Bundle data = dataMap.toBundle();

                                UUID uuid = UUID.randomUUID();
                                data.putString("GUID", uuid.toString());

                                LocalBroadcastManager localManager = LocalBroadcastManager.getInstance(me);
                                Intent intent = new Intent(edu.northwestern.cbits.purple_robot_manager.probes.Probe.PROBE_READING);
                                intent.putExtras(data);

                                localManager.sendBroadcast(intent);

                                String probeName = dataMap.getString("PROBE", "");

                                if (probeName.equals(WearBatteryProbe.NAME))
                                {
                                    SanityManager sanity = SanityManager.getInstance(me);
                                    String name = me.getString(R.string.name_sanity_wear_battery);

                                    int level = dataMap.getInt("BATTERY_LEVEL", Integer.MAX_VALUE);

                                    if (level < 30)
                                    {
                                        String message = me.getString(R.string.name_sanity_wear_battery_warning);

                                        sanity.addAlert(SanityCheck.WARNING, name, message, null);
                                    } else
                                        sanity.clearAlert(name);
                                }

                                Wearable.DataApi.deleteDataItems(AndroidWearService._apiClient, item.getUri());
                            }
                           else if (item.getUri().getPath().startsWith(AndroidWearService.URI_CRASH_REPORT))
                            {
                                DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                                HashMap<String, Object> payload = new HashMap<>();

                                for (String key : dataMap.keySet())
                                {
                                    payload.put(key, dataMap.getString(key));
                                }

                                LogManager.getInstance(me).log("android_wear_crash", payload);

                                Wearable.DataApi.deleteDataItems(AndroidWearService._apiClient, item.getUri());
                            }

                            try
                            {
                                Thread.sleep(sleepInterval);
                            }
                            catch (InterruptedException e)
                            {
                                e.printStackTrace();
                            }
                        }
                        else if (event.getType() == DataEvent.TYPE_DELETED)
                        {

                        }
                    }
                }
            };

            Thread t = new Thread(r);
            t.start();
        }
    }
}
