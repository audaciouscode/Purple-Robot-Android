package edu.northwestern.cbits.purple_robot_manager.probes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import edu.northwestern.cbits.purple_robot_manager.PersistentService;
import edu.northwestern.cbits.purple_robot_manager.R;
import edu.northwestern.cbits.purple_robot_manager.activities.settings.FlexibleListPreference;
import edu.northwestern.cbits.purple_robot_manager.activities.settings.SettingsKeys;
import edu.northwestern.cbits.purple_robot_manager.activities.settings.RobotPreferenceListener;
import edu.northwestern.cbits.purple_robot_manager.logging.LogManager;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.ActivityDetectionProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.AddressBookDistancesProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.AmbientHumidityProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.ApplicationLaunchProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.AudioFeaturesProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.BatteryProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.BluetoothDevicesProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.CallStateProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.CommunicationEventProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.CommunicationLogProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.ContinuousProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.DateCalendarProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.FusedLocationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.GeomagneticRotationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.GravityProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.HardwareInformationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.LinearAccelerationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.LocationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.MediaRouterDeviceProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.NetworkProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.NfcProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.RandomNoiseProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.RawLocationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.RobotHealthProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.RotationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.RunningSoftwareProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.SaintProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.ScreenProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.ShionProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.SignificantMotionProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.SoftwareInformationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.StepCounterProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.TelephonyProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.TemperatureProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.TouchEventsProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.VisibleSatelliteProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.WakeLockInformationProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.builtin.WifiAccessPointsProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.AndroidWearProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.wear.WearAccelerometerProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.wear.WearBatteryProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.wear.WearGyroscopeProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.wear.WearHeartRateProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.wear.WearLightProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.wear.WearLivewellActivityCountProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.wear.WearMagneticFieldProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.media.AudioCaptureProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.sensors.AccelerometerSensorProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.sensors.LightSensorProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.services.FitbitBetaProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.studies.LivewellActivityCountsProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.studies.LivewellPebbleActivityCountsProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.devices.PebbleProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.features.AccelerometerBasicStatisticsFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.features.AccelerometerFrequencyFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.features.CallHistoryFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.features.DeviceInUseFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.services.GooglePlacesProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.features.GyroscopeBasicStatisticsFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.features.JavascriptFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.features.LightProbeBasicStatisticsFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.features.MagneticFieldBasicStatisticsFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.features.PressureProbeBasicStatisticsFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.features.ProximityProbeBasicStatisticsFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.features.SunriseSunsetFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.features.TemperatureProbeBasicStatisticsFeature;
import edu.northwestern.cbits.purple_robot_manager.probes.services.WeatherUndergroundProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.features.p20.P20FeaturesProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.sample.SampleProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.services.FitbitProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.services.FoursquareProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.services.GitHubProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.services.InstagramProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.services.JawboneProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.services.TwitterProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.services.FacebookEventsProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.services.FacebookProbe;
import edu.northwestern.cbits.purple_robot_manager.probes.services.iHealthProbe;

public class ProbeManager
{
    private static Map<String, Probe> _cachedProbes = new HashMap<>();
    private static List<Probe> _probeInstances = new ArrayList<>();

    private static boolean _initing = false;
    private static boolean _inited = false;

    private static ArrayList<Probe> _allProbes = null;

    public static List<Probe> allProbes(Context context)
    {
        if (ProbeManager._inited == false && ProbeManager._initing == false)
        {
            Probe.loadProbeClasses(context);

            ProbeManager._initing = true;

            for (Class<Probe> probeClass : Probe.availableProbeClasses())
            {
                try
                {
                    Probe probe = probeClass.newInstance();

                    ProbeManager._probeInstances.add(probe);
                }
                catch (InstantiationException e)
                {
                    LogManager.getInstance(context).logException(e);
                }
                catch (IllegalAccessException e)
                {
                    LogManager.getInstance(context).logException(e);
                }
            }

            ProbeManager._inited = true;
            ProbeManager._initing = false;

            ProbeManager._allProbes = new ArrayList<>(ProbeManager._probeInstances);
        }

        return ProbeManager._allProbes;
    }

    public static void nudgeProbes(Context context)
    {
        if (ProbeManager._inited == false)
            return;

        if (context != null && ProbeManager._probeInstances != null)
        {
            for (Probe probe : ProbeManager.allProbes(context))
            {
                probe.nudge(context.getApplicationContext());
            }
        }
    }

    public static Probe probeForName(String name, Context context)
    {
        if (ProbeManager._inited == false)
            return null;

        if (ProbeManager._cachedProbes.containsKey(name))
            return ProbeManager._cachedProbes.get(name);

        Probe match = null;

        for (Probe probe : ProbeManager.allProbes(context))
        {
            boolean found = false;

            if (probe instanceof ContinuousProbe)
            {
                ContinuousProbe continuous = (ContinuousProbe) probe;

                if (continuous.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof VisibleSatelliteProbe)
            {
                VisibleSatelliteProbe satellite = (VisibleSatelliteProbe) probe;

                if (satellite.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof BluetoothDevicesProbe)
            {
                BluetoothDevicesProbe bluetooth = (BluetoothDevicesProbe) probe;

                if (bluetooth.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof AddressBookDistancesProbe)
            {
                AddressBookDistancesProbe distances = (AddressBookDistancesProbe) probe;

                if (distances.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof NetworkProbe)
            {
                NetworkProbe network = (NetworkProbe) probe;

                if (network.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof SoftwareInformationProbe)
            {
                SoftwareInformationProbe software = (SoftwareInformationProbe) probe;

                if (software.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof HardwareInformationProbe)
            {
                HardwareInformationProbe hardware = (HardwareInformationProbe) probe;

                if (hardware.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof TelephonyProbe)
            {
                TelephonyProbe telephony = (TelephonyProbe) probe;

                if (telephony.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof RobotHealthProbe)
            {
                RobotHealthProbe robot = (RobotHealthProbe) probe;

                if (robot.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof ScreenProbe)
            {
                ScreenProbe screen = (ScreenProbe) probe;

                if (screen.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof BatteryProbe)
            {
                BatteryProbe battery = (BatteryProbe) probe;

                if (battery.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof SignificantMotionProbe)
            {
                SignificantMotionProbe motion = (SignificantMotionProbe) probe;

                if (motion.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof AccelerometerBasicStatisticsFeature)
            {
                AccelerometerBasicStatisticsFeature stats = (AccelerometerBasicStatisticsFeature) probe;

                if (stats.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof GyroscopeBasicStatisticsFeature)
            {
                GyroscopeBasicStatisticsFeature stats = (GyroscopeBasicStatisticsFeature) probe;

                if (stats.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof LightProbeBasicStatisticsFeature)
            {
                LightProbeBasicStatisticsFeature stats = (LightProbeBasicStatisticsFeature) probe;

                if (stats.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof MagneticFieldBasicStatisticsFeature)
            {
                MagneticFieldBasicStatisticsFeature stats = (MagneticFieldBasicStatisticsFeature) probe;

                if (stats.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof LocationProbe)
            {
                LocationProbe location = (LocationProbe) probe;

                if (location.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof RawLocationProbe)
            {
                RawLocationProbe location = (RawLocationProbe) probe;

                if (location.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof FusedLocationProbe)
            {
                FusedLocationProbe location = (FusedLocationProbe) probe;

                if (location.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof MediaRouterDeviceProbe)
            {
                MediaRouterDeviceProbe devices = (MediaRouterDeviceProbe) probe;

                if (devices.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof WifiAccessPointsProbe)
            {
                WifiAccessPointsProbe wifi = (WifiAccessPointsProbe) probe;

                if (wifi.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof CommunicationLogProbe)
            {
                CommunicationLogProbe comms = (CommunicationLogProbe) probe;

                if (comms.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof DateCalendarProbe)
            {
                DateCalendarProbe comms = (DateCalendarProbe) probe;

                if (comms.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof CallStateProbe)
            {
                CallStateProbe callState = (CallStateProbe) probe;

                if (callState.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof JavascriptFeature)
            {
                JavascriptFeature jsFeature = (JavascriptFeature) probe;

                if (jsFeature.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof DeviceInUseFeature)
            {
                DeviceInUseFeature device = (DeviceInUseFeature) probe;

                if (device.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof WeatherUndergroundProbe)
            {
                WeatherUndergroundProbe weather = (WeatherUndergroundProbe) probe;

                if (weather.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof SunriseSunsetFeature)
            {
                SunriseSunsetFeature sunrise = (SunriseSunsetFeature) probe;

                if (sunrise.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof CallHistoryFeature)
            {
                CallHistoryFeature call = (CallHistoryFeature) probe;

                if (call.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof RunningSoftwareProbe)
            {
                RunningSoftwareProbe software = (RunningSoftwareProbe) probe;

                if (software.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof RandomNoiseProbe)
            {
                RandomNoiseProbe software = (RandomNoiseProbe) probe;

                if (software.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof LabelProbe)
            {
                LabelProbe label = (LabelProbe) probe;

                if (label.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof PressureProbeBasicStatisticsFeature)
            {
                PressureProbeBasicStatisticsFeature stats = (PressureProbeBasicStatisticsFeature) probe;

                if (stats.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof ProximityProbeBasicStatisticsFeature)
            {
                ProximityProbeBasicStatisticsFeature stats = (ProximityProbeBasicStatisticsFeature) probe;

                if (stats.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof TemperatureProbe)
            {
                TemperatureProbe temp = (TemperatureProbe) probe;

                if (temp.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof TemperatureProbeBasicStatisticsFeature)
            {
                TemperatureProbeBasicStatisticsFeature stats = (TemperatureProbeBasicStatisticsFeature) probe;

                if (stats.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof AccelerometerFrequencyFeature)
            {
                AccelerometerFrequencyFeature stats = (AccelerometerFrequencyFeature) probe;

                if (stats.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof GooglePlacesProbe)
            {
                GooglePlacesProbe places = (GooglePlacesProbe) probe;

                if (places.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof FitbitProbe)
            {
                FitbitProbe fitbit = (FitbitProbe) probe;

                if (fitbit.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof FitbitBetaProbe)
            {
                FitbitBetaProbe fitbit = (FitbitBetaProbe) probe;

                if (fitbit.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof JawboneProbe)
            {
                JawboneProbe jawbone = (JawboneProbe) probe;

                if (jawbone.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof GitHubProbe)
            {
                GitHubProbe github = (GitHubProbe) probe;

                if (github.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof TwitterProbe)
            {
                TwitterProbe twitter = (TwitterProbe) probe;

                if (twitter.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof AudioFeaturesProbe)
            {
                AudioFeaturesProbe audio = (AudioFeaturesProbe) probe;

                if (audio.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof CommunicationEventProbe)
            {
                CommunicationEventProbe comm = (CommunicationEventProbe) probe;

                if (comm.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof ApplicationLaunchProbe)
            {
                ApplicationLaunchProbe apps = (ApplicationLaunchProbe) probe;

                if (apps.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof GravityProbe)
            {
                GravityProbe gravity = (GravityProbe) probe;

                if (gravity.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof StepCounterProbe)
            {
                StepCounterProbe steps = (StepCounterProbe) probe;

                if (steps.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof LinearAccelerationProbe)
            {
                LinearAccelerationProbe linear = (LinearAccelerationProbe) probe;

                if (linear.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof GeomagneticRotationProbe)
            {
                GeomagneticRotationProbe rotation = (GeomagneticRotationProbe) probe;

                if (rotation.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof RotationProbe)
            {
                RotationProbe rotation = (RotationProbe) probe;

                if (rotation.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof AmbientHumidityProbe)
            {
                AmbientHumidityProbe humid = (AmbientHumidityProbe) probe;

                if (humid.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof FacebookProbe)
            {
                FacebookProbe facebook = (FacebookProbe) probe;

                if (facebook.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof FacebookEventsProbe)
            {
                FacebookEventsProbe facebook = (FacebookEventsProbe) probe;

                if (facebook.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof InstagramProbe)
            {
                InstagramProbe instagram = (InstagramProbe) probe;

                if (instagram.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof FoursquareProbe)
            {
                FoursquareProbe foursquare = (FoursquareProbe) probe;

                if (foursquare.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof ActivityDetectionProbe)
            {
                ActivityDetectionProbe activity = (ActivityDetectionProbe) probe;

                if (activity.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof ShionProbe)
            {
                ShionProbe shion = (ShionProbe) probe;

                if (shion.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof SaintProbe)
            {
                SaintProbe saint = (SaintProbe) probe;

                if (saint.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof TouchEventsProbe)
            {
                TouchEventsProbe touch = (TouchEventsProbe) probe;

                if (touch.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof SampleProbe)
            {
                SampleProbe sample = (SampleProbe) probe;

                if (sample.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof P20FeaturesProbe)
            {
                P20FeaturesProbe p20 = (P20FeaturesProbe) probe;

                if (p20.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof NfcProbe)
            {
                NfcProbe nfc = (NfcProbe) probe;

                if (nfc.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof PebbleProbe)
            {
                PebbleProbe pebble = (PebbleProbe) probe;

                if (pebble.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof LivewellPebbleActivityCountsProbe)
            {
                LivewellPebbleActivityCountsProbe pebble = (LivewellPebbleActivityCountsProbe) probe;

                if (pebble.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof LivewellActivityCountsProbe)
            {
                LivewellActivityCountsProbe livewell = (LivewellActivityCountsProbe) probe;

                if (livewell.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof iHealthProbe)
            {
                iHealthProbe ihealth = (iHealthProbe) probe;

                if (ihealth.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof AndroidWearProbe)
            {
                AndroidWearProbe wear = (AndroidWearProbe) probe;

                if (wear.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof WearLightProbe)
            {
                WearLightProbe wear = (WearLightProbe) probe;

                if (wear.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof WearBatteryProbe)
            {
                WearBatteryProbe wear = (WearBatteryProbe) probe;

                if (wear.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof WearLivewellActivityCountProbe)
            {
                WearLivewellActivityCountProbe wear = (WearLivewellActivityCountProbe) probe;

                if (wear.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof WearHeartRateProbe)
            {
                WearHeartRateProbe wear = (WearHeartRateProbe) probe;

                if (wear.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof WearAccelerometerProbe)
            {
                WearAccelerometerProbe wear = (WearAccelerometerProbe) probe;

                if (wear.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof WearGyroscopeProbe)
            {
                WearGyroscopeProbe wear = (WearGyroscopeProbe) probe;

                if (wear.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof WearMagneticFieldProbe)
            {
                WearMagneticFieldProbe wear = (WearMagneticFieldProbe) probe;

                if (wear.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof WakeLockInformationProbe)
            {
                WakeLockInformationProbe wakelock = (WakeLockInformationProbe) probe;

                if (wakelock.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof AudioCaptureProbe)
            {
                AudioCaptureProbe audio = (AudioCaptureProbe) probe;

                if (audio.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof AccelerometerSensorProbe)
            {
                AccelerometerSensorProbe accelerometer = (AccelerometerSensorProbe) probe;

                if (accelerometer.name(context).equalsIgnoreCase(name))
                    found = true;
            }
            else if (probe instanceof LightSensorProbe)
            {
                LightSensorProbe light = (LightSensorProbe) probe;

                if (light.name(context).equalsIgnoreCase(name))
                    found = true;
            }

            if (found)
            {
                ProbeManager._cachedProbes.put(name, probe);
                match = probe;
            }
        }

        return match;
    }

    public static PreferenceScreen buildPreferenceScreen(Context context, PreferenceManager manager)
    {
        PreferenceScreen screen = manager.createPreferenceScreen(context);
        screen.setOrder(0);
        screen.setTitle(R.string.title_preference_probes_screen);
        screen.setKey(SettingsKeys.PROBES_SCREEN_KEY);

        HashMap<String, ArrayList<PreferenceScreen>> probeMap = new HashMap<>();

        for (Probe probe : ProbeManager.allProbes(context))
        {
            PreferenceScreen probeScreen = probe.preferenceScreen(context, manager);

            if (probeScreen != null)
            {
                String key = probe.probeCategory(context);

                ArrayList<PreferenceScreen> screens = new ArrayList<>();

                if (probeMap.containsKey(key))
                    screens = probeMap.get(key);
                else
                    probeMap.put(key, screens);

                screens.add(probeScreen);
            }
        }

        PreferenceCategory globalCategory = new PreferenceCategory(context);

        globalCategory.setTitle(R.string.title_preference_probes_global_category);
        globalCategory.setKey("config_all_probes_options");

        screen.addPreference(globalCategory);

        CheckBoxPreference enabled = new CheckBoxPreference(context);
        enabled.setTitle(R.string.title_preference_probes_enable_probes);
        enabled.setKey("config_probes_enabled");
        enabled.setDefaultValue(false);

        globalCategory.addPreference(enabled);

        RobotPreferenceListener listener = new RobotPreferenceListener(context);
        FlexibleListPreference nudgeInterval = new FlexibleListPreference(context);
        nudgeInterval.setTitle(R.string.title_preference_probes_nudge_interval);
        nudgeInterval.setSummary(R.string.summary_preference_probes_nudge_interval);
        nudgeInterval.setKey(PersistentService.PROBE_NUDGE_INTERVAL);
        nudgeInterval.setDefaultValue(PersistentService.PROBE_NUDGE_INTERVAL_DEFAULT);
        nudgeInterval.setEntries(R.array.probe_nudge_interval_labels);
        nudgeInterval.setEntryValues(R.array.probe_nudge_interval_values);
        nudgeInterval.setOnPreferenceChangeListener(listener);

        screen.addPreference(nudgeInterval);

        Preference disableAll = new Preference(context);
        disableAll.setTitle(R.string.title_preference_probes_disable_each_probe);
        disableAll.setKey(SettingsKeys.PROBES_DISABLE_EACH_KEY);
        disableAll.setOnPreferenceClickListener(listener);

        globalCategory.addPreference(disableAll);

        PreferenceCategory probesCategory = new PreferenceCategory(context);
        probesCategory.setTitle(R.string.title_preference_probes_available_category);
        probesCategory.setKey("config_all_probes_list");

        screen.addPreference(probesCategory);

        ArrayList<String> probeCategories = new ArrayList<>();
        probeCategories.add(context.getString(R.string.probe_sensor_category));
        probeCategories.add(context.getString(R.string.probe_device_info_category));
        probeCategories.add(context.getString(R.string.probe_other_devices_category));
        probeCategories.add(context.getString(R.string.probe_external_environment_category));
        probeCategories.add(context.getString(R.string.probe_personal_info_category));
        probeCategories.add(context.getString(R.string.probe_external_services_category));
        probeCategories.add(context.getString(R.string.probe_misc_category));
        probeCategories.add(context.getString(R.string.probe_studies_category));

        for (String key : probeMap.keySet())
        {
            if (probeCategories.contains(key) == false)
                probeCategories.add(key);
        }

        for (String key : probeCategories)
        {
            PreferenceScreen probesScreen = manager.createPreferenceScreen(context);
            probesScreen.setTitle(key);

            for (PreferenceScreen probeScreen : probeMap.get(key))
                probesScreen.addPreference(probeScreen);

            probesCategory.addPreference(probesScreen);
        }

        return screen;
    }

    public static void clearFeatures()
    {
        if (ProbeManager._inited == false)
            return;

        ArrayList<Probe> toRemove = new ArrayList<>();

        for (Probe p : ProbeManager._probeInstances)
        {
            if (p instanceof JavascriptFeature)
            {
                JavascriptFeature js = (JavascriptFeature) p;

                if (js.embedded() == false)
                    toRemove.add(js);
            }
        }

        ProbeManager._probeInstances.removeAll(toRemove);

        ProbeManager._cachedProbes.clear();
    }

    public static void addFeature(String title, String name, String script, String formatter, List<String> sources, boolean b)
    {
        if (ProbeManager._inited == false)
            return;

        JavascriptFeature feature = new JavascriptFeature(title, name, script, formatter, sources, false);

        ProbeManager._probeInstances.add(feature);
    }

    public static void disableProbes(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor editor = prefs.edit();
        editor.putBoolean("config_probes_enabled", false);
        editor.apply();

        ProbeManager.nudgeProbes(context);
    }

    public static void enableProbes(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        Editor editor = prefs.edit();
        editor.putBoolean("config_probes_enabled", true);
        editor.apply();

        ProbeManager.nudgeProbes(context);
    }

    public static boolean probesState(Context context)
    {
        SharedPreferences prefs = Probe.getPreferences(context);

        return prefs.getBoolean("config_probes_enabled", false);
    }

    public static void enableProbe(Context context, String probeName)
    {
        Probe p = ProbeManager.probeForName(probeName, context);

        if (p != null)
        {
            p.enable(context);

            ProbeManager.nudgeProbes(context);
        }
    }

    public static void disableProbe(Context context, String probeName)
    {
        Probe p = ProbeManager.probeForName(probeName, context);

        if (p != null)
        {
            p.disable(context);

            ProbeManager.nudgeProbes(context);
        }
    }

    public static List<Map<String, Object>> probeConfigurations(Context context)
    {
        List<Map<String, Object>> configs = new ArrayList<>();

        for (Probe p : ProbeManager.allProbes(context))
        {
            Map<String, Object> config = p.configuration(context);

            configs.add(config);
        }

        return configs;
    }

    public static boolean updateProbe(Context context, String probeName, Map<String, Object> params)
    {
        Probe p = ProbeManager.probeForName(probeName, context);

        if (p != null)
        {
            p.updateFromMap(context, params);

            ProbeManager.nudgeProbes(context);

            return true;
        }

        return false;
    }

    public static void disableEachProbe(Context context)
    {
        if (ProbeManager._inited == false)
            return;

        if (context != null && ProbeManager._probeInstances != null)
        {
            for (Probe probe : ProbeManager.allProbes(context))
            {
                probe.disable(context);
            }
        }
    }

    public static Map<String, Object> probeConfiguration(Context context, String name)
    {
        for (Map<String, Object> map : ProbeManager.probeConfigurations(context))
        {
            if (map.get("name").equals(name))
                return map;
        }

        return null;
    }
}
