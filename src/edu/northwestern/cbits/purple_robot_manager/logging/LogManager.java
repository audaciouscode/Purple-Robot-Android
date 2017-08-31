package edu.northwestern.cbits.purple_robot_manager.logging;

import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import edu.northwestern.cbits.anthracite.Logger;
import edu.northwestern.cbits.purple_robot_manager.EncryptionManager;
import edu.northwestern.cbits.purple_robot_manager.util.WakeLockManager;

public class LogManager
{
    public static final String ENABLED = "config_enable_log_server";
    private static final boolean ENABLED_DEFAULT = false;

    public static final String URI = "config_log_server_uri";
    private static final String URI_DEFAULT = null;

    public static final String INCLUDE_LOCATION = "config_log_location";
    private static final boolean INCLUDE_LOCATION_DEFAULT = false;

    public static final String UPLOAD_INTERVAL = "config_log_upload_interval";
    private static final long UPLOAD_INTERVAL_DEFAULT = 300000;

    public static final String WIFI_ONLY = "config_restrict_log_wifi";
    private static final boolean WIFI_ONLY_DEFAULT = true;

    public static final String LIBERAL_SSL = "config_http_liberal_ssl";
    private static final boolean LIBERAL_SSL_DEFAULT = false;

    public static final String HEARTBEAT = "config_log_heartbeat";
    private static final boolean HEARTBEAT_DEFAULT = false;

    private static final String CHARGING_ONLY = "config_restrict_log_charging";
    private static final boolean CHARGING_ONLY_DEFAULT = false;

    private static final String LOG_LEVEL = "config_log_level";
    private static final String LOG_LEVEL_DEFAULT = "" + LogManager.DEBUG;
    private static final String LOG_LEVEL_KEY = "pr_log_level";

    private Context _context = null;

    private static LogManager _sharedInstance = null;

    public static int DEBUG = 1;
    public static int INFO = 2;
    public static int WARN = 3;
    public static int ERROR = 4;

    private Logger _logger = null;

    public LogManager(Context context)
    {
        String userHash = EncryptionManager.getInstance().getUserHash(context);

        this._logger = Logger.getInstance(context, userHash);
        this._context = context;

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        this._logger.setDebug(true);
        this._logger.setEnabled(prefs.getBoolean(LogManager.ENABLED, LogManager.ENABLED_DEFAULT));
        this._logger.setHeartbeat(prefs.getBoolean(LogManager.HEARTBEAT, LogManager.HEARTBEAT_DEFAULT));
        this._logger.setIncludeLocation(prefs.getBoolean(LogManager.INCLUDE_LOCATION, LogManager.INCLUDE_LOCATION_DEFAULT));
        this._logger.setWifiOnly(prefs.getBoolean(LogManager.WIFI_ONLY, LogManager.WIFI_ONLY_DEFAULT));
        this._logger.setLiberalSsl(prefs.getBoolean(LogManager.LIBERAL_SSL, LogManager.LIBERAL_SSL_DEFAULT));
        this._logger.setChargingOnly(prefs.getBoolean(LogManager.CHARGING_ONLY, LogManager.CHARGING_ONLY_DEFAULT));

        try
        {
            this._logger.setUploadUri(Uri.parse(prefs.getString(LogManager.URI, LogManager.URI_DEFAULT)));
        }
        catch (NullPointerException e)
        {

        }
        catch (RuntimeException e)
        {
            e.printStackTrace();
        }

        try
        {
            this._logger.setUploadInterval(prefs.getLong(LogManager.UPLOAD_INTERVAL, LogManager.UPLOAD_INTERVAL_DEFAULT));
        }
        catch (ClassCastException e)
        {
            this._logger.setUploadInterval(Long.parseLong(prefs.getString(LogManager.UPLOAD_INTERVAL, "" + LogManager.UPLOAD_INTERVAL_DEFAULT)));
        }
    }

    public String getLogUrl(Context context)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        return prefs.getString(LogManager.URI, LogManager.URI_DEFAULT);
    }

    public static LogManager getInstance(Context context)
    {
        if (LogManager._sharedInstance != null)
            return LogManager._sharedInstance;

        if (context != null)
            LogManager._sharedInstance = new LogManager(context.getApplicationContext());

        LogManager._sharedInstance.log("pr_log_manager_initialized", null);

        return LogManager._sharedInstance;
    }

    public boolean log(String event, Map<String, Object> payload)
    {
        return this.log(LogManager.INFO, event, payload);
    }

    public boolean log(int level, String event, Map<String, Object> payload) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);

        int logLevel = Integer.parseInt(prefs.getString(LogManager.LOG_LEVEL, LogManager.LOG_LEVEL_DEFAULT));

        if (level >= logLevel) {
            if (payload == null) {
                payload = new HashMap<>();
            }

            payload.put(LogManager.LOG_LEVEL_KEY, level);

            return this._logger.log(event, payload);
        }

        return true;
    }

    public void logException(Throwable e)
    {
        this._logger.logException(e);
    }

    public void upload()
    {
        PowerManager.WakeLock lock = WakeLockManager.getInstance(this._context).requestWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "log-transmission");

        Log.e("PR", "----- ATTEMPT LOG UPLOADS ------");

        this._logger.attemptUploads(true);

        lock.release();
    }

    public void setEndpoint(String endpoint)
    {
        this._logger.setUploadUri(Uri.parse(endpoint));
    }

    public String getEndpoint()
    {
        Uri u = this._logger.getUploadUri();

        if (u != null)
            return u.toString();

        return null;
    }

    public boolean getEnabled()
    {
        return this._logger.getEnabled();
    }

    public void setEnabled(boolean enabled)
    {
        this._logger.setEnabled(enabled);
    }

    public void setIncludeLocation(boolean include)
    {
        this._logger.setIncludeLocation(include);
    }

    public void setUploadInterval(long interval)
    {
        this._logger.setUploadInterval(interval);
    }

    public void setWifiOnly(boolean wifiOnly)
    {
        this._logger.setWifiOnly(wifiOnly);
    }

    public void setLiberalSsl(boolean liberal)
    {
        this._logger.setLiberalSsl(liberal);
    }

    public void setHeartbeat(boolean heartbeat)
    {
        this._logger.setHeartbeat(heartbeat);
    }

    public int pendingEventsCount()
    {
        return this._logger.pendingEventsCount();
    }
}
