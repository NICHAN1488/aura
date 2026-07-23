package com.example.musik;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class NotificationReceiver extends BroadcastReceiver {
    private static final String TAG = "NotificationReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.e(TAG, "❌ Intent или action == null");
            return;
        }

        String action = intent.getAction();
        Log.d(TAG, "📩 Получено действие: " + action);

        Intent serviceIntent = new Intent(context, MusicService.class);
        serviceIntent.setAction(action);
        context.startService(serviceIntent);
    }
}