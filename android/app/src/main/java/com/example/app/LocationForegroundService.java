package com.example.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import android.location.LocationListener;
import android.location.LocationManager;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocationForegroundService extends Service {
    private static final String TAG = "LocationService";
    private static final String CHANNEL_ID = "location_tracking_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final long INTERVAL_MS = 5 * 60 * 1000; // 5 minutes
    private static final String WAKELOCK_TAG = "LocationTracking:ServiceWakeLock";

    private AlarmManager alarmManager;
    private PendingIntent alarmPendingIntent;
    private PowerManager.WakeLock wakeLock;
    private LocationManager locationManager;
    private Location lastLocation;
    private ExecutorService executorService;
    private LocationListener singleUpdateListener;
    private boolean locationUpdateProcessed = false;

    private String serverEndpoint = "http://192.168.1.155:3000";
    private String accessToken = "";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        executorService = Executors.newSingleThreadExecutor();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);
        alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);

        createNotificationChannel();
        setupAlarm();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        if (intent != null) {
            String action = intent.getAction();

            // Handle fetch location action from alarm
            if ("FETCH_LOCATION".equals(action)) {
                Log.d(TAG, "Alarm triggered - fetching location");
                fetchAndSendLocation();
                return START_STICKY;
            }

            // Handle token update action
            if ("UPDATE_TOKEN".equals(action)) {
                String newToken = intent.getStringExtra("accessToken");
                if (newToken != null) {
                    accessToken = newToken;
                    Log.d(TAG, "Access token updated in service");
                }
                return START_STICKY;
            }

            // Handle initial startup
            if (intent.hasExtra("endpoint")) {
                serverEndpoint = intent.getStringExtra("endpoint");
            }
            if (intent.hasExtra("accessToken")) {
                accessToken = intent.getStringExtra("accessToken");
            }
        }

        // Start as foreground service
        Notification notification = buildNotification("Location tracking active - every 5 minutes");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // Get initial location immediately
        fetchAndSendLocation();

        return START_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Location Tracking",
                NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Shows when location tracking is active");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification buildNotification(String contentText) {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Location Tracking Active")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String text) {
        Notification notification = buildNotification(text);
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.notify(NOTIFICATION_ID, notification);
        }
    }

    private void setupAlarm() {
        Intent alarmIntent = new Intent(this, LocationAlarmReceiver.class);
        alarmPendingIntent = PendingIntent.getBroadcast(
            this,
            0,
            alarmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        // Schedule alarm to trigger every 5 minutes
        // Using setExactAndAllowWhileIdle for precise timing even in Doze mode
        long triggerTime = System.currentTimeMillis() + INTERVAL_MS;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                alarmPendingIntent
            );
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                alarmPendingIntent
            );
        }

        Log.d(TAG, "Alarm scheduled for " + INTERVAL_MS + "ms from now");
    }

    private void fetchAndSendLocation() {
        // Reset the flag for this new cycle
        locationUpdateProcessed = false;
        
        // Acquire wake lock to ensure we can get location
        PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
        wakeLock.acquire(2 * 60 * 1000L); // Hold for up to 2 minutes

        Log.d(TAG, "Fetching fresh location...");
        updateNotification("Getting location...");

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            releaseWakeLock();
            return;
        }

        // Remove any previous listener
        if (singleUpdateListener != null) {
            locationManager.removeUpdates(singleUpdateListener);
        }

        // Create a one-time location listener
        singleUpdateListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                // Only process the first location update we receive
                if (locationUpdateProcessed) {
                    Log.d(TAG, "Location already processed this cycle, ignoring");
                    return;
                }
                
                locationUpdateProcessed = true;
                Log.d(TAG, "Fresh location received: " + location.getLatitude() + ", " + location.getLongitude());
                lastLocation = location;
                
                // Remove this listener after getting the update
                locationManager.removeUpdates(this);
                
                // Send the location
                sendLocationToServer();
                
                // Schedule next alarm (only once per cycle now)
                setupAlarm();
                
                // Release wake lock
                releaseWakeLock();
            }

            @Override
            public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {
                Log.d(TAG, "Provider enabled: " + provider);
            }

            @Override
            public void onProviderDisabled(String provider) {
                Log.d(TAG, "Provider disabled: " + provider);
            }
        };

        // Try GPS first
        boolean requestSent = false;
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                0,
                0,
                singleUpdateListener,
                Looper.getMainLooper()
            );
            requestSent = true;
            Log.d(TAG, "Requested location from GPS");
        }

        // Also try network provider
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                0,
                0,
                singleUpdateListener,
                Looper.getMainLooper()
            );
            requestSent = true;
            Log.d(TAG, "Requested location from Network");
        }

        if (!requestSent) {
            Log.w(TAG, "No location providers available");
            // Try last known location as fallback
            Location lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            if (lastKnown == null) {
                lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
            }
            if (lastKnown != null) {
                lastLocation = lastKnown;
                sendLocationToServer();
            }
            setupAlarm();
            releaseWakeLock();
        }

        // Fallback timeout - if no location after 90 seconds, use last known and reschedule
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            // Only act if we haven't already processed a location
            if (!locationUpdateProcessed) {
                Log.w(TAG, "Location timeout - using last known location");
                locationUpdateProcessed = true;
                
                if (singleUpdateListener != null) {
                    locationManager.removeUpdates(singleUpdateListener);
                }
                
                if (lastLocation != null) {
                    sendLocationToServer();
                }
                
                setupAlarm();
                releaseWakeLock();
            } else {
                Log.d(TAG, "Location timeout fired but location already processed");
            }
        }, 90000); // 90 seconds timeout
    }

    private void releaseWakeLock() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
            Log.d(TAG, "Wake lock released");
        }
    }

    private void sendLocationToServer() {
        if (lastLocation == null) {
            Log.w(TAG, "No location available yet");
            updateNotification("Waiting for GPS fix...");
            return;
        }

        final double latitude = lastLocation.getLatitude();
        final double longitude = lastLocation.getLongitude();

        Log.d(TAG, "Sending location: " + latitude + ", " + longitude);
        updateNotification("Last: " + String.format(Locale.US, "%.6f, %.6f", latitude, longitude));

        executorService.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(serverEndpoint);
                connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                
                // Add authorization header if token is available
                if (accessToken != null && !accessToken.isEmpty()) {
                    connection.setRequestProperty("Authorization", "Bearer " + accessToken);
                    Log.d(TAG, "Authorization header added");
                }
                
                connection.setDoOutput(true);
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                // Create JSON payload
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                String timestamp = sdf.format(new Date());

                String jsonPayload = String.format(Locale.US,
                    "{\"latitude\":%f,\"longitude\":%f,\"timestamp\":\"%s\"}",
                    latitude, longitude, timestamp
                );

                OutputStream os = connection.getOutputStream();
                os.write(jsonPayload.getBytes("UTF-8"));
                os.close();

                int responseCode = connection.getResponseCode();
                Log.d(TAG, "Server response: " + responseCode);

            } catch (Exception e) {
                Log.e(TAG, "Failed to send location: " + e.getMessage());
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        });
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");

        // Cancel the alarm
        if (alarmManager != null && alarmPendingIntent != null) {
            alarmManager.cancel(alarmPendingIntent);
            Log.d(TAG, "Alarm cancelled");
        }

        // Remove location updates
        if (locationManager != null && singleUpdateListener != null) {
            locationManager.removeUpdates(singleUpdateListener);
        }

        // Release wake lock
        releaseWakeLock();

        if (executorService != null) {
            executorService.shutdown();
        }

        super.onDestroy();
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        Log.d(TAG, "App task removed - stopping service");
        // Stop the service when the app is swiped away from recent apps
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
