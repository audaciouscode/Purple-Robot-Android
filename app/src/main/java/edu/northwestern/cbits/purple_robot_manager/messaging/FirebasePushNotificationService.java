package edu.northwestern.cbits.purple_robot_manager.messaging;

import android.util.Log;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import edu.northwestern.cbits.purple_robot_manager.ManagerService;
import edu.northwestern.cbits.purple_robot_manager.PurpleRobotApplication;

public class FirebasePushNotificationService extends FirebaseMessagingService {
    @Override
    public void onNewToken(String token) {
        PurpleRobotApplication.updateFirebaseDeviceToken(this, token);
    }

    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.e("PURPLE-ROBOT", "From: " + remoteMessage.getFrom());

        // Check if message contains a data payload.
        if (remoteMessage.getData().size() > 0) {
            Log.e("PURPLE-ROBOT", "Message data payload: " + remoteMessage.getData());

            if (/* Check if data needs to be processed by long running job */ true) {
                // For long-running tasks (10 seconds or more) use Firebase Job Dispatcher.
                // scheduleJob();
            } else {
                // Handle message within 10 seconds
                // handleNow();
            }
        }

        // Check if message contains a notification payload.
        if (remoteMessage.getNotification() != null) {
            Log.e("PURPLE-ROBOT", "Message Notification Body: " + remoteMessage.getNotification().getBody());
        }

        ManagerService.setupPeriodicCheck(this);
    }
}
