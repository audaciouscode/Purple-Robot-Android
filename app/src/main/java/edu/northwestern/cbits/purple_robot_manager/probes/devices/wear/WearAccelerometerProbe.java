package edu.northwestern.cbits.purple_robot_manager.probes.devices.wear;

import android.content.Context;
import android.os.Bundle;

import edu.northwestern.cbits.purple_robot_manager.R;

public class WearAccelerometerProbe extends WearSensorProbe
{
    public String name(Context context)
    {
        return "edu.northwestern.cbits.purple_robot_manager.WearAccelerometerProbe";
    }

    @Override
    public String title(Context context)
    {
        return context.getString(R.string.title_wear_accelerometer_probe);
    }

    @Override
    public String summary(Context context)
    {
        return context.getString(R.string.summary_wear_accelerometer_probe_desc);
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
    public String getPreferenceKey() {
        return "devices_wear_accelerometer";
    }
}
