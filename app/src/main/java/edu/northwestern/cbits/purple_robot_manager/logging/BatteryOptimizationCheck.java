package edu.northwestern.cbits.purple_robot_manager.logging;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

import edu.northwestern.cbits.purple_robot_manager.R;

public class BatteryOptimizationCheck extends SanityCheck
{
    public String name(Context context)
    {
        return context.getString(R.string.name_sanity_android_battery_optimization);
    }

    @SuppressWarnings("deprecation")
    public void runCheck(Context context)
    {
        this._errorMessage = null;
        this._errorLevel = SanityCheck.OK;

        if (Build.VERSION.SDK_INT >= 23) {
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            if (power.isIgnoringBatteryOptimizations(context.getPackageName()) == false) {
                this._errorMessage = context.getString(R.string.name_sanity_android_battery_optimization_error);
                this._errorLevel = SanityCheck.ERROR;
            }
        }
    }

    public Runnable getAction(final Context context)
    {
        final BatteryOptimizationCheck me = this;

        if (Build.VERSION.SDK_INT >= 23) {
            return new Runnable() {
                public void run() {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(intent);

                    SanityManager.getInstance(context).clearAlert(me.name(context));
                }
            };
        }

        return null;
    }
}
