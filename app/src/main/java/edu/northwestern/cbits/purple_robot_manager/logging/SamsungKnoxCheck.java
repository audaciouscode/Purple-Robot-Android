package edu.northwestern.cbits.purple_robot_manager.logging;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;

import com.samsung.android.knox.license.EnterpriseLicenseManager;
import com.samsung.android.knox.license.KnoxEnterpriseLicenseManager;

import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.DiagnosticActivity;
import edu.northwestern.cbits.purple_robot_manager.util.KnoxReceiver;

public class SamsungKnoxCheck extends SanityCheck
{
    public String name(Context context)
    {
        return context.getString(R.string.name_sanity_samsung_knox_optimization);
    }

    @SuppressWarnings("deprecation")
    public void runCheck(Context context)
    {
        this._errorMessage = null;
        this._errorLevel = SanityCheck.OK;

        try {
            KnoxEnterpriseLicenseManager.getInstance(context);
            EnterpriseLicenseManager.getInstance(context);

            DevicePolicyManager dpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);

            final ComponentName deviceAdmin = new ComponentName(context, KnoxReceiver.class);

            if (dpm.isAdminActive(deviceAdmin) == false) {
                this._errorMessage = context.getString(R.string.name_sanity_knox_optimization_error);
                this._errorLevel = SanityCheck.ERROR;
            }
        } catch (NoClassDefFoundError ex) {
            this._errorMessage = null;
            this._errorLevel = SanityCheck.OK;
        }
    }

    public Runnable getAction(final Context context)
    {
        final SamsungKnoxCheck me = this;

        return new Runnable() {
            public void run() {
                Log.e("PURPLE-ROBOT", "SHOW KNOX " + context);

                final ComponentName deviceAdmin = new ComponentName(context, KnoxReceiver.class);

                Intent intent = new Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN);
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, deviceAdmin);

                context.startActivity(intent);

                SanityManager.getInstance(context).clearAlert(me.name(context));
            }
        };
    }
}
