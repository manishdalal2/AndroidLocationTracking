package com.example.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.location.Location;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
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
    private static final long INTERVAL_MS = 5000; // 30 seconds

    private Handler handler;
    private Runnable locationRunnable;
    private LocationManager locationManager;
    private Location lastLocation;
    private ExecutorService executorService;

    private String serverEndpoint = "http://192.168.1.155:3000";

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");

        handler = new Handler(Looper.getMainLooper());
        executorService = Executors.newSingleThreadExecutor();
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");

        if (intent != null && intent.hasExtra("endpoint")) {
            serverEndpoint = intent.getStringExtra("endpoint");
        }

        // Start as foreground service
        Notification notification = buildNotification("Starting location tracking...");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }

        // Start location updates
        startLocationUpdates();

        // Start periodic task
        startPeriodicLocationSend();

        return START_NOT_STICKY;
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

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted");
            return;
        }

        LocationListener locationListener = new LocationListener() {
            @Override
            public void onLocationChanged(Location location) {
                lastLocation = location;
                Log.d(TAG, "Location updated: " + location.getLatitude() + ", " + location.getLongitude());
            }

            @Override
            public void onStatusChanged(String provider, int status, android.os.Bundle extras) {}

            @Override
            public void onProviderEnabled(String provider) {}

            @Override
            public void onProviderDisabled(String provider) {}
        };

        // Request location updates from GPS
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000, // min time interval (5 sec)
                0,    // min distance
                locationListener,
                Looper.getMainLooper()
            );
        }

        // Also request from network provider as fallback
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            locationManager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                5000,
                0,
                locationListener,
                Looper.getMainLooper()
            );
        }

        // Get last known location initially
        lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        if (lastLocation == null) {
            lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
        }
    }

    private void startPeriodicLocationSend() {
        locationRunnable = new Runnable() {
            @Override
            public void run() {
                sendLocationToServer();
                handler.postDelayed(this, INTERVAL_MS);
            }
        };

        // Start immediately, then repeat every 30 seconds
        handler.post(locationRunnable);
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

        if (handler != null && locationRunnable != null) {
            handler.removeCallbacks(locationRunnable);
        }

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
