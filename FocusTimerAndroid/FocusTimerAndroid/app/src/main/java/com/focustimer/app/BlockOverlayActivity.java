package com.focustimer.app;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

public class BlockOverlayActivity extends Activity {

    private TextView tvTimer;
    private TextView tvAppName;
    private TextView tvMode;
    private Button   btnBack;
    private String   mode;
    private Handler  handler  = new Handler(Looper.getMainLooper());
    private Runnable updater;

    private BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            int remaining = intent.getIntExtra(AppMonitorService.EXTRA_REMAINING, 0);
            updateTimer(remaining);
        }
    };

    private BroadcastReceiver doneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Make this appear over lock screen and other apps
        getWindow().addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        setContentView(R.layout.activity_block_overlay);

        tvTimer   = findViewById(R.id.tvTimer);
        tvAppName = findViewById(R.id.tvAppName);
        tvMode    = findViewById(R.id.tvMode);
        btnBack   = findViewById(R.id.btnBack);

        mode = getIntent().getStringExtra("mode");
        String blockedPkg = getIntent().getStringExtra("blocked_package");

        // Get the blocked app's name
        String appLabel = getAppName(blockedPkg);
        tvAppName.setText("🚫 " + appLabel + " is blocked");

        if ("strict".equals(mode)) {
            tvMode.setText("STRICT MODE — You cannot leave until the timer ends");
            btnBack.setVisibility(View.GONE);
        } else {
            tvMode.setText("NORMAL MODE — You chose to block this app");
            btnBack.setVisibility(View.VISIBLE);
            btnBack.setOnClickListener(v -> goBackToTimer());
        }

        // Vibrate to alert user
        vibrate();

        // Register receivers
        registerReceiver(tickReceiver, new IntentFilter(AppMonitorService.ACTION_TICK));
        registerReceiver(doneReceiver, new IntentFilter("com.focustimer.DONE"));

        // Update timer from prefs
        PrefsManager prefs = new PrefsManager(this);
        long remaining = (prefs.getTimerEndEpoch() - System.currentTimeMillis()) / 1000;
        updateTimer((int) Math.max(0, remaining));
    }

    private String getAppName(String packageName) {
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo info = pm.getApplicationInfo(packageName, 0);
            return pm.getApplicationLabel(info).toString();
        } catch (Exception e) {
            return packageName;
        }
    }

    private void updateTimer(int remainingSecs) {
        String timeStr = String.format("%02d:%02d", remainingSecs / 60, remainingSecs % 60);
        tvTimer.setText(timeStr);
    }

    private void goBackToTimer() {
        Intent i = new Intent(this, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(i);
        finish();
    }

    private void vibrate() {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 200, 100, 200}, -1));
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void onBackPressed() {
        // In strict mode, back button does nothing — user is trapped
        if (!"strict".equals(mode)) {
            goBackToTimer();
        }
        // strict: intentionally do nothing
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(tickReceiver);
            unregisterReceiver(doneReceiver);
        } catch (Exception ignored) {}
    }
}
