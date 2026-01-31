package com.example.app;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(LocationTrackerPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        // Stop location service when app is destroyed
        Intent serviceIntent = new Intent(this, LocationForegroundService.class);
        stopService(serviceIntent);
        super.onDestroy();
    }
}
