package com.focustimer.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppMonitorService extends Service {

    private static final String TAG            = "AppMonitorService";
    private static final String CHANNEL_ID     = "focus_timer_channel";
    private static final int    NOTIF_ID       = 1001;
    private static final long   POLL_INTERVAL  = 1000L; // check every second

    public static final String ACTION_START    = "com.focustimer.START";
    public static final String ACTION_STOP     = "com.focustimer.STOP";
    public static final String ACTION_TICK     = "com.focustimer.TICK";
    public static final String EXTRA_REMAINING = "remaining_seconds";
    public static final String EXTRA_MODE      = "mode";

    private Handler          handler;
    private Runnable         pollRunnable;
    private PrefsManager     prefs;
    private UsageStatsManager usageStatsManager;

    private long   endEpoch;
    private String mode;
    private String lastBlockedPackage = "";

    @Override
    public void onCreate() {
        super.onCreate();
        prefs             = new PrefsManager(this);
        usageStatsManager = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        handler           = new Handler(Looper.getMainLooper());
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            endEpoch = prefs.getTimerEndEpoch();
            mode     = prefs.getTimerMode();
            startForeground(NOTIF_ID, buildNotification("Focus session active", 0));
            startPolling();
        } else if (ACTION_STOP.equals(action)) {
            stopPolling();
            stopForeground(true);
            stopSelf();
        }
        return START_STICKY;
    }

    // ── Polling ───────────────────────────────────────────────
    private void startPolling() {
        stopPolling();
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                tick();
                handler.postDelayed(this, POLL_INTERVAL);
            }
        };
        handler.post(pollRunnable);
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            handler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private void tick() {
        long now       = System.currentTimeMillis();
        long remaining = (endEpoch - now) / 1000;

        if (remaining <= 0) {
            // Timer complete
            onTimerComplete();
            return;
        }

        // Update notification
        updateNotification((int) remaining);

        // Broadcast tick to UI
        Intent tick = new Intent(ACTION_TICK);
        tick.putExtra(EXTRA_REMAINING, (int) remaining);
        tick.putExtra(EXTRA_MODE, mode);
        sendBroadcast(tick);

        // Check foreground app and block if needed
        checkAndBlock();
    }

    private void checkAndBlock() {
        String foreground = getForegroundApp();
        if (foreground == null || foreground.equals(getPackageName())) return;
        if (foreground.equals(lastBlockedPackage)) return; // already showed overlay

        Set<String> blocked = prefs.getBlockedPackages();
        if (blocked.contains(foreground)) {
            lastBlockedPackage = foreground;
            showBlockOverlay(foreground);
        } else {
            lastBlockedPackage = "";
        }
    }

    private String getForegroundApp() {
        try {
            long now = System.currentTimeMillis();
            Map<String, UsageStats> stats = usageStatsManager.queryAndAggregateUsageStats(
                now - 5000, now
            );
            String topPackage = null;
            long   topTime    = 0;
            for (Map.Entry<String, UsageStats> entry : stats.entrySet()) {
                UsageStats us = entry.getValue();
                if (us.getLastTimeUsed() > topTime) {
                    topTime    = us.getLastTimeUsed();
                    topPackage = us.getPackageName();
                }
            }
            return topPackage;
        } catch (Exception e) {
            Log.e(TAG, "getForegroundApp: " + e.getMessage());
            return null;
        }
    }

    private void showBlockOverlay(String blockedPackage) {
        Intent i = new Intent(this, BlockOverlayActivity.class);
        i.putExtra("blocked_package", blockedPackage);
        i.putExtra("mode", mode);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
    }

    private void onTimerComplete() {
        stopPolling();
        prefs.clearTimerState();

        // Notify UI
        Intent done = new Intent("com.focustimer.DONE");
        sendBroadcast(done);

        // Show completion notification
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification n = new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🎉 Focus Session Complete!")
            .setContentText("Great work! Your focus session has ended.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build();
        nm.notify(1002, n);

        stopForeground(true);
        stopSelf();
    }

    // ── Notifications ─────────────────────────────────────────
    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "Focus Timer", NotificationManager.IMPORTANCE_LOW
        );
        ch.setDescription("Keeps the focus timer running in background");
        ch.setSound(null, null);
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(ch);
    }

    private Notification buildNotification(String text, int remainingSecs) {
        Intent openApp = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, openApp,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        String timeStr = remainingSecs > 0
            ? String.format("%02d:%02d remaining", remainingSecs / 60, remainingSecs % 60)
            : text;

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("Focus Timer — " + (mode != null ? mode.toUpperCase() : "") + " MODE")
            .setContentText(timeStr)
            .setOngoing(true)
            .setContentIntent(pi)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)
            .build();
    }

    private void updateNotification(int remainingSecs) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIF_ID, buildNotification("", remainingSecs));
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPolling();
    }
}
