package edu.northwestern.cbits.purple_robot_manager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

// import com.squareup.leakcanary.LeakCanary;

import com.samsung.android.knox.EnterpriseDeviceManager;
import com.samsung.android.knox.application.ApplicationPolicy;
import com.samsung.android.knox.license.EnterpriseLicenseManager;
import com.samsung.android.knox.license.KnoxEnterpriseLicenseManager;

import edu.northwestern.cbits.purple_robot_manager.activities.settings.SettingsKeys;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.xsi.XSI;

public class PurpleRobotApplication extends Application
{
    private static Context _context;
    private static long _lastFix = 0;

    @Override
    public void onCreate()
    {
        super.onCreate();

//        if (LeakCanary.isInAnalyzerProcess(this)) {
            // This process is dedicated to LeakCanary for heap analysis.
            // You should not init your app in this process.
//            return;
//        }

//        LeakCanary.install(this);

        PurpleRobotApplication._context = this.getApplicationContext();

        try
        {
            PackageInfo info = PurpleRobotApplication._context.getPackageManager().getPackageInfo(PurpleRobotApplication._context.getPackageName(), 0);
            System.setProperty("http.agent", "Purple Robot " + info.versionName);
        }
        catch (PackageManager.NameNotFoundException e)
        {
            e.printStackTrace();
        }

        try
        {
            PackageInfo info = PurpleRobotApplication._context.getPackageManager().getPackageInfo(PurpleRobotApplication._context.getPackageName(), 0);

            XSI.setUserAgent(PurpleRobotApplication._context.getString(R.string.app_name) + " " + info.versionName);
        }
        catch (PackageManager.NameNotFoundException e)
        {
            LogManager.getInstance(PurpleRobotApplication._context).logException(e);
        }

//        LeakCanary.install(this);

        try {
            BroadcastReceiver licenseReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (EnterpriseLicenseManager.ACTION_LICENSE_STATUS.equals(action)) {
                        PurpleRobotApplication._context.unregisterReceiver(this);

                        Bundle extras = intent.getExtras();
                        String status = extras.getString("com.samsung.android.knox.intent.extra.LICENSE_STATUS");
                        int err_code = extras.getInt(EnterpriseLicenseManager.EXTRA_LICENSE_ERROR_CODE);

                        if (status != null && status.equals("success")) {
                            EnterpriseDeviceManager edm = EnterpriseDeviceManager.getInstance(PurpleRobotApplication._context);

                            ApplicationPolicy appPolicy = edm.getApplicationPolicy();

                            if (appPolicy.getPackagesFromForceStopBlackList().contains("edu.northwestern.cbits.purple_robot_manager") == false) {
                                List<String> list = new ArrayList<String>();

                                list.add("edu.northwestern.cbits.purple_robot_manager");

                                try {
                                    boolean result = appPolicy.addPackagesToForceStopBlackList(list);

                                    if (result) {
                                        LogManager.getInstance(context).log("whitelisted_samsung_app", null);
                                    } else {
                                        // previous behaviour for device still prevails
                                    }
                                } catch (SecurityException ex) {
                                    LogManager.getInstance(context).logException(ex);
                                }
                            }
                        }
                    } else if (KnoxEnterpriseLicenseManager.ACTION_LICENSE_STATUS.equals(action)) {
                        Bundle extras = intent.getExtras();
                        String status = extras.getString(KnoxEnterpriseLicenseManager.EXTRA_LICENSE_STATUS);
                        int err_code = extras.getInt(KnoxEnterpriseLicenseManager.EXTRA_LICENSE_ERROR_CODE);

                        if (status.equals("success")) {
                            EnterpriseDeviceManager edm = EnterpriseDeviceManager.getInstance(PurpleRobotApplication._context);

                            ApplicationPolicy appPolicy = edm.getApplicationPolicy();

                            if (appPolicy.getPackagesFromForceStopBlackList().contains("uk.ac.kcl.sleepsight") == false) {
                                List<String> list = new ArrayList<String>();

                                list.add("edu.northwestern.cbits.purple_robot_manager");

                                try {
                                    boolean result = appPolicy.addPackagesToForceStopBlackList(list);

                                    if (result) {
                                        LogManager.getInstance(context).log("whitelisted_samsung_app", null);
                                   } else {
                                        // previous behaviour for device still prevails
                                    }
                                } catch (SecurityException ex) {
                                    LogManager.getInstance(context).logException(ex);
                                }
                            }
                        }
                    }

                    PurpleRobotApplication._context.unregisterReceiver(this);
                }
            };

            IntentFilter filter = new IntentFilter();
            filter.addAction(KnoxEnterpriseLicenseManager.ACTION_LICENSE_STATUS);
            filter.addAction(EnterpriseLicenseManager.ACTION_LICENSE_STATUS);

            PurpleRobotApplication._context.registerReceiver(licenseReceiver, filter);

            try {
                KnoxEnterpriseLicenseManager.getInstance(PurpleRobotApplication._context).activateLicense(PurpleRobotApplication._context.getString(R.string.samsung_knox));
            } catch (NoClassDefFoundError ex) {

            }

            try {
                EnterpriseLicenseManager.getInstance(PurpleRobotApplication._context).activateLicense(PurpleRobotApplication._context.getString(R.string.samsung_knox_legacy));
            } catch (NoClassDefFoundError ex) {

            }
        } catch (RuntimeException ex) {

        }
    }

    public static Context getAppContext()
    {
        return PurpleRobotApplication._context;
    }

    public static boolean updateFromMap(Context context, Map<String, Object> config)
    {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        Editor e = prefs.edit();

        for (String key : config.keySet())
        {
            Object value = config.get(key);

            if (value instanceof String)
            {
                if (SettingsKeys.CONFIG_URL.equals(key))
                    EncryptionManager.getInstance().setConfigUri(context, Uri.parse(value.toString()));
                else if (SettingsKeys.USER_ID_KEY.equals(key))
                    EncryptionManager.getInstance().setUserId(context, value.toString());
                else
                    e.putString(key, value.toString());
            }
            else if (value instanceof Boolean)
                e.putBoolean(key, (Boolean) value);
        }

        boolean success = e.commit();

        return success;
    }

    public static Map<String, Object> configuration(Context context)
    {
        Map<String, Object> map = new HashMap<>();

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);

        try
        {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();

            factory.setNamespaceAware(false);

            XmlPullParser xpp = context.getResources().getXml(R.xml.settings);
            int eventType = xpp.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT)
            {
                if (eventType == XmlPullParser.START_TAG)
                {
                    String name = xpp.getName();
                    String key = xpp.getAttributeValue("http://schemas.android.com/apk/res/android", "key");

                    if (prefs.contains(key))
                    {
                        if ("EditTextPreference".equals(name) || "edu.northwestern.cbits.purple_robot_manager.activities.settings.FlexibleEditTextPreference".equals(name))
                            map.put(key, prefs.getString(key, null));
                        else if ("ListPreference".equals(name) || "edu.northwestern.cbits.purple_robot_manager.activities.settings.FlexibleListPreference".equals(name))
                            map.put(key, prefs.getString(key, null));
                        else if ("CheckBoxPreference".equals(name))
                        {
                            try
                            {
                                map.put(key, prefs.getBoolean(key, false));
                            }
                            catch (ClassCastException e)
                            {
                                String value = prefs.getString(key, null);

                                if (value != null && "true".equals(value.toLowerCase(Locale.ENGLISH)))
                                    map.put(key, true);
                                else
                                    map.put(key, false);
                            }
                        }
                        else if ("Preference".equals(name))
                            map.put(key, prefs.getString(key, null));
                    }
                }

                eventType = xpp.next();
            }
        }
        catch (XmlPullParserException | IOException e)
        {
            LogManager.getInstance(context).logException(e);
        }

        map.put("config_probes_enabled", prefs.getBoolean("config_probes_enabled", false));

        return map;
    }

    public static void fixPreferences(Context context, boolean force)
    {
        if (force)
            PurpleRobotApplication._lastFix = 0;

        long now = System.currentTimeMillis();

        if (now - PurpleRobotApplication._lastFix > 60000)
        {
            Map<String, Object> values = PurpleRobotApplication.configuration(context);

            SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            Editor e = prefs.edit();

            for (String key : values.keySet())
            {
                Object value = values.get(key);

                if (value instanceof Boolean)
                {
                    Boolean boolValue = (Boolean) value;

                    e.putBoolean(key, boolValue);
                }
            }

            e.apply();

            PurpleRobotApplication._lastFix = now;
        }
    }
}
