package com.example.app;

import android.Manifest;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
    name = "LocationTracker",
    permissions = {
        @Permission(
            alias = "location",
            strings = {
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            }
        ),
        @Permission(
            alias = "backgroundLocation",
            strings = { Manifest.permission.ACCESS_BACKGROUND_LOCATION }
        )
    }
)
public class LocationTrackerPlugin extends Plugin {
    private static final String TAG = "LocationTrackerPlugin";
    private boolean isRunning = false;
    private String currentAccessToken = "";

    @PluginMethod
    public void startTracking(PluginCall call) {
        String endpoint = call.getString("endpoint", "http://192.168.1.155:3000");
        String accessToken = call.getString("accessToken", "");
        
        // Store the access token
        currentAccessToken = accessToken;

        // Check if we have location permission
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            requestAllPermissions(call, "locationPermissionCallback");
            return;
        }

        // For Android 10+, also need background location permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (!hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                requestPermissionForAlias("backgroundLocation", call, "backgroundPermissionCallback");
                return;
            }
        }

        startService(endpoint, accessToken);

        JSObject result = new JSObject();
        result.put("success", true);
        result.put("message", "Location tracking started");
        call.resolve(result);
    }

    @PermissionCallback
    private void locationPermissionCallback(PluginCall call) {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            // Now request background permission on Android 10+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                if (!hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                    requestPermissionForAlias("backgroundLocation", call, "backgroundPermissionCallback");
                    return;
                }
            }

            String endpoint = call.getString("endpoint", "http://192.168.1.155:3000");
            String accessToken = call.getString("accessToken", "");
            currentAccessToken = accessToken;
            
            startService(endpoint, accessToken);

            JSObject result = new JSObject();
            result.put("success", true);
            result.put("message", "Location tracking started");
            call.resolve(result);
        } else {
            JSObject result = new JSObject();
            result.put("success", false);
            result.put("message", "Location permission denied");
            call.resolve(result);
        }
    }

    @PermissionCallback
    private void backgroundPermissionCallback(PluginCall call) {
        String endpoint = call.getString("endpoint", "http://192.168.1.155:3000");
        String accessToken = call.getString("accessToken", "");
        currentAccessToken = accessToken;

        // Start service even if background permission denied (will work in foreground)
        startService(endpoint, accessToken);

        JSObject result = new JSObject();
        result.put("success", true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            result.put("message", "Location tracking started (foreground only - background permission denied)");
        } else {
            result.put("message", "Location tracking started with full background support");
        }

        call.resolve(result);
    }

    private void startService(String endpoint, String accessToken) {
        if (isRunning) {
            Log.d(TAG, "Service already running");
            return;
        }

        Intent serviceIntent = new Intent(getContext(), LocationForegroundService.class);
        serviceIntent.putExtra("endpoint", endpoint);
        serviceIntent.putExtra("accessToken", accessToken);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getContext().startForegroundService(serviceIntent);
        } else {
            getContext().startService(serviceIntent);
        }

        isRunning = true;
        Log.d(TAG, "Location service started");
    }

    @PluginMethod
    public void stopTracking(PluginCall call) {
        Intent serviceIntent = new Intent(getContext(), LocationForegroundService.class);
        getContext().stopService(serviceIntent);
        isRunning = false;

        JSObject result = new JSObject();
        result.put("success", true);
        result.put("message", "Location tracking stopped");
        call.resolve(result);
    }

    @PluginMethod
    public void updateToken(PluginCall call) {
        String newAccessToken = call.getString("accessToken", "");
        currentAccessToken = newAccessToken;

        // Send the updated token to the running service
        Intent serviceIntent = new Intent(getContext(), LocationForegroundService.class);
        serviceIntent.setAction("UPDATE_TOKEN");
        serviceIntent.putExtra("accessToken", newAccessToken);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getContext().startForegroundService(serviceIntent);
        } else {
            getContext().startService(serviceIntent);
        }

        Log.d(TAG, "Token update sent to service");

        JSObject result = new JSObject();
        result.put("success", true);
        result.put("message", "Access token updated successfully");
        call.resolve(result);
    }

    @PluginMethod
    public void isTracking(PluginCall call) {
        JSObject result = new JSObject();
        result.put("isTracking", isRunning);
        call.resolve(result);
    }
}
