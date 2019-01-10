package edu.northwestern.cbits.purple_robot_manager.logging;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import android.support.v4.app.NotificationCompat;

import edu.northwestern.cbits.purple_robot_manager.ManagerService;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.DiagnosticActivity;
import edu.northwestern.cbits.purple_robot_manager.activities.PermissionsActivity;
import edu.northwestern.cbits.purple_robot_manager.activities.StartActivity;

public class SanityManager
{
    public static final int NOTE_ID = 457284567;

    private static SanityManager _sharedInstance = null;

    private Context _context = null;

    private final HashMap<String, String> _errors = new HashMap<>();
    private final HashMap<String, String> _warnings = new HashMap<>();
    private final HashMap<String, Runnable> _actions = new HashMap<>();

    private int _lastStatus = -1;

    private String _lastTitle = null;
    private String _lastMessage = null;

    private SanityManager(Context context)
    {
        this._context = context;

        AlarmManager alarms = (AlarmManager) this._context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(ManagerService.REFRESH_ERROR_STATE_INTENT);
        PendingIntent pending = PendingIntent.getService(this._context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        alarms.setInexactRepeating(AlarmManager.RTC, 0, 60000, pending);
    }

    public static SanityManager getInstance(Context context)
    {
        if (SanityManager._sharedInstance != null)
            return SanityManager._sharedInstance;

        if (context != null)
            SanityManager._sharedInstance = new SanityManager(context.getApplicationContext());


        SanityManager._sharedInstance._context = context.getApplicationContext();

        return SanityManager._sharedInstance;
    }

    @SuppressWarnings("rawtypes")
    @SuppressLint("NewApi")
    public void refreshState(Context context)
    {
        String packageName = this.getClass().getPackage().getName();

        String[] checkClasses = this._context.getResources().getStringArray(R.array.sanity_check_classes);

        for (String className : checkClasses)
        {
            Class checkClass = null;

            try
            {
                checkClass = Class.forName(packageName + "." + className);
            }
            catch (ClassNotFoundException e)
            {
                try
                {
                    checkClass = Class.forName(className);
                }
                catch (ClassNotFoundException ee)
                {
                    LogManager.getInstance(this._context).logException(ee);
                }
            }

            if (checkClass != null)
            {
                try
                {
                    SanityCheck check = (SanityCheck) checkClass.newInstance();

                    check.runCheck(this._context);

                    int error = check.getErrorLevel();

                    if (error == SanityCheck.ERROR)
                        this.addAlert(SanityCheck.ERROR, check.name(this._context), check.getErrorMessage(),
                                check.getAction(context));
                    else if (error == SanityCheck.WARNING)
                        this.addAlert(SanityCheck.WARNING, check.name(this._context), check.getErrorMessage(),
                                check.getAction(context));
                    else
                        this.clearAlert(check.name(context));
                }
                catch (InstantiationException | ClassCastException | IllegalAccessException e)
                {
                    LogManager.getInstance(this._context).logException(e);
                }
            }
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this._context);

        if (prefs.getBoolean("config_mute_warnings", false) == false)
        {
            this._lastStatus = this.getErrorLevel();

            int issueCount = this._errors.size() + this._warnings.size();

            PendingIntent contentIntent = PendingIntent.getActivity(this._context, SanityManager.NOTE_ID, new Intent(this._context, StartActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

            if (this._lastStatus != SanityCheck.OK)
                contentIntent = PendingIntent.getActivity(this._context, SanityManager.NOTE_ID, new Intent(this._context, DiagnosticActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);

            NotificationManager noteManager = (NotificationManager) this._context.getSystemService(Context.NOTIFICATION_SERVICE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this._context);
            builder = builder.setContentIntent(contentIntent);
            builder = builder.setContentTitle(this._context.getString(R.string.notify_running_title));
            builder.setOngoing(true);

            builder = builder.setSmallIcon(R.drawable.ic_note_normal);
            builder = builder.setContentText(this._context.getString(R.string.pr_errors_none_label));

            if (this._lastStatus != SanityCheck.OK)
            {
                if (issueCount == 1)
                {
                    builder = builder.setContentText(this._context.getString(R.string.note_purple_robot_message_single));
                    builder = builder.setTicker(this._context.getString(R.string.note_purple_robot_message_single));
                }
                else
                {
                    builder = builder.setContentText(this._context.getString(R.string.note_purple_robot_message_multiple, issueCount));
                    builder = builder.setTicker(this._context.getString(R.string.note_purple_robot_message_multiple, issueCount));
                }

                if (this._lastStatus == SanityCheck.ERROR)
                    builder = builder.setSmallIcon(R.drawable.ic_note_error);
                else
                    builder = builder.setSmallIcon(R.drawable.ic_note_warning);

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN)
                {
                    NotificationCompat.InboxStyle style = new NotificationCompat.InboxStyle();

                    style = style.setBigContentTitle(this._context.getString(R.string.note_purple_robot_status));

                    if (issueCount == 1)
                        style = style.setSummaryText(this._context.getString(R.string.note_purple_robot_message_single));
                    else
                        style = style.setSummaryText(this._context.getString(R.string.note_purple_robot_message_multiple, issueCount));

                    synchronized(this._errors)
                    {
                        for (String key : this._errors.keySet())
                            style = style.addLine(this._errors.get(key));
                    }

                    synchronized(this._warnings) {
                        for (String key : this._warnings.keySet())
                            style = style.addLine(this._warnings.get(key));
                    }

                    builder = builder.setStyle(style);
                }
            }

            Notification note = builder.build();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
                note.color = 0xff4e015c;

            noteManager.notify(SanityManager.NOTE_ID, note);
        }
    }

    public int getErrorLevel()
    {
        if (this._errors.size() > 0)
            return SanityCheck.ERROR;
        else if (this._warnings.size() > 0)
            return SanityCheck.WARNING;

        return SanityCheck.OK;
    }

    public int getErrorIconResource()
    {
        switch (this.getErrorLevel())
        {
            case SanityCheck.ERROR:
                return R.drawable.action_error;
            case SanityCheck.WARNING:
                return R.drawable.action_warning;
        }

        return R.drawable.action_about;
    }

    public void addAlert(int level, String name, String message, Runnable action)
    {
        boolean alert = false;

        if (level == SanityCheck.WARNING && this._warnings.containsKey(name) == false)
        {
            synchronized(this._warnings) {
                this._warnings.put(name, message);
                alert = true;
            }
        }
        else if (this._warnings.containsKey(name) == false)
        {
            synchronized(this._errors) {
                this._errors.put(name, message);
                alert = true;
            }
        }

        if (action != null)
        {
            synchronized(this._actions) {
                this._actions.put(name, action);
            }
        }

        if (alert)
        {
            if (name.equals(this._lastTitle) && message.equals(this._lastMessage))
            {

            }
            else
            {
                Intent pebbleIntent = new Intent("com.getpebble.action.SEND_NOTIFICATION");

                HashMap<String, String> data = new HashMap<>();
                data.put("title", name);
                data.put("body", message);

                JSONObject jsonData = new JSONObject(data);
                String notificationData = new JSONArray().put(jsonData).toString();

                pebbleIntent.putExtra("messageType", "PEBBLE_ALERT");
                pebbleIntent.putExtra("sender", this._context.getString(R.string.app_name));
                pebbleIntent.putExtra("notificationData", notificationData);

                // this._context.sendBroadcast(pebbleIntent);

                this._lastMessage = message;
                this._lastTitle = name;
            }
        }
    }

    public void runActionForAlert(String name)
    {
        Runnable r = this._actions.get(name);

        if (r != null)
        {
            Thread t = new Thread(r);
            t.start();
        }
    }

    public void runActionForAlert(String name, Activity activity)
    {
        this._context = activity;

        Runnable r = this._actions.get(name);

        if (r != null)
        {
            Thread t = new Thread(r);
            t.start();
        }
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> errors()
    {
        return (Map<String, String>) this._errors.clone();
    }

    @SuppressWarnings("unchecked")
    public Map<String, String> warnings()
    {
        return (Map<String, String>) this._warnings.clone();
    }

    public void clearAlert(String title)
    {
        synchronized(this._warnings) {
            this._warnings.remove(title);
        }

        synchronized(this._errors) {
            this._errors.remove(title);
        }

        synchronized (this._actions) {
            this._actions.remove(title);
        }
    }

    public void addPermissionAlert(String requester, String permission, String rationale, Runnable r)
    {
        final SanityManager me = this;

        if (r == null) {
            r = new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(me._context, PermissionsActivity.class);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    me._context.startActivity(intent);
                }
            };
        }

        String title = requester + ": " + PermissionsActivity.getTitle(this._context, permission);

        this.addAlert(SanityCheck.ERROR, title, rationale, r);
    }

    public void clearPermissionAlert(String permission)
    {
        String title = PermissionsActivity.getTitle(this._context, permission);

        this.clearAlert(title);
    }
}
