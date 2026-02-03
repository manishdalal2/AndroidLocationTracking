package com.example.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

public class LocationAlarmReceiver extends BroadcastReceiver {
    private static final String TAG = "LocationAlarmReceiver";
    private static final String WAKELOCK_TAG = "LocationTracking:WakeLock";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Alarm triggered - acquiring wake lock");

        // Acquire a wake lock to ensure the device stays awake
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKELOCK_TAG
        );
        
        // Hold wake lock for up to 3 minutes (enough time to get location)
        wakeLock.acquire(3 * 60 * 1000L);

        // Start the service to get location
        Intent serviceIntent = new Intent(context, LocationForegroundService.class);
        serviceIntent.setAction("FETCH_LOCATION");
        context.startService(serviceIntent);

        Log.d(TAG, "Wake lock acquired and service triggered");
    }
}
