package com.focustimer.app;

import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import com.google.android.material.tabs.TabLayout;
import java.text.SimpleDateFormat;
import java.util.*;

public class MainActivity extends AppCompatActivity {

    // ── Views ──────────────────────────────────────────────────
    private TabLayout       tabLayout;
    private FrameLayout     container;

    // Timer views
    private View            timerView;
    private TextView        tvTimerDisplay, tvStatus, tvModeLabel;
    private ProgressBar     progressRing;
    private RadioGroup      rgMode;
    private RadioButton     rbNormal, rbStrict;
    private Button          btnStart, btnStop;
    private LinearLayout    blockedPreview;
    private TextView        tvBlockedCount;
    private View            lockBanner;
    private LinearLayout    activeBlockList;

    // Apps views
    private View            appsView;
    private ListView        appsListView;
    private EditText        etSearch;
    private TextView        tvBlockedSummary;
    private Button          btnBlockAll, btnUnblockAll, btnResetDefault;

    // Stats views
    private View            statsView;
    private TextView        tvTotalFocus, tvCompletionRate, tvNormal, tvStrict;
    private ListView        sessionsListView;
    private Button          btnExport, btnClearHistory;

    // ── State ─────────────────────────────────────────────────
    private PrefsManager    prefs;
    private Set<String>     blockedPackages;
    private List<AppInfo>   allApps = new ArrayList<>();
    private List<AppInfo>   filteredApps = new ArrayList<>();

    private boolean         timerRunning = false;
    private boolean         timerPaused  = false;
    private String          timerMode    = "normal";
    private int             selectedMins = 25;
    private int             remainingSecs = 0;
    private long            startEpoch, endEpoch;
    private String          startIso;

    private Handler         handler = new Handler(Looper.getMainLooper());
    private Runnable        uiUpdater;

    private static final int[] PRESET_MINS = {5, 15, 25, 45, 60};

    private BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            remainingSecs = intent.getIntExtra(AppMonitorService.EXTRA_REMAINING, 0);
            updateTimerUI();
        }
    };

    private BroadcastReceiver doneReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            onTimerFinished(true);
        }
    };

    // ── Lifecycle ─────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs           = new PrefsManager(this);
        blockedPackages = prefs.getBlockedPackages();
        timerRunning    = prefs.isTimerRunning();
        if (timerRunning) {
            endEpoch      = prefs.getTimerEndEpoch();
            timerMode     = prefs.getTimerMode();
            remainingSecs = (int) Math.max(0, (endEpoch - System.currentTimeMillis()) / 1000);
        }

        inflateViews();
        buildTabs();
        loadInstalledApps();
        checkPermissions();

        registerReceiver(tickReceiver, new IntentFilter(AppMonitorService.ACTION_TICK), Context.RECEIVER_NOT_EXPORTED);
        registerReceiver(doneReceiver, new IntentFilter("com.focustimer.DONE"), Context.RECEIVER_NOT_EXPORTED);

        showTab(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (timerRunning) updateTimerUI();
        refreshStats();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(tickReceiver); } catch (Exception ignored) {}
        try { unregisterReceiver(doneReceiver); } catch (Exception ignored) {}
    }

    // ── Views setup ───────────────────────────────────────────
    private void inflateViews() {
        tabLayout  = findViewById(R.id.tabLayout);
        container  = findViewById(R.id.container);

        LayoutInflater inf = LayoutInflater.from(this);

        // Timer tab
        timerView       = inf.inflate(R.layout.tab_timer, container, false);
        tvTimerDisplay  = timerView.findViewById(R.id.tvTimerDisplay);
        tvStatus        = timerView.findViewById(R.id.tvStatus);
        tvModeLabel     = timerView.findViewById(R.id.tvModeLabel);
        rgMode          = timerView.findViewById(R.id.rgMode);
        rbNormal        = timerView.findViewById(R.id.rbNormal);
        rbStrict        = timerView.findViewById(R.id.rbStrict);
        btnStart        = timerView.findViewById(R.id.btnStart);
        btnStop         = timerView.findViewById(R.id.btnStop);
        blockedPreview  = timerView.findViewById(R.id.blockedPreview);
        tvBlockedCount  = timerView.findViewById(R.id.tvBlockedCount);
        lockBanner      = timerView.findViewById(R.id.lockBanner);
        activeBlockList = timerView.findViewById(R.id.activeBlockList);

        // Build preset duration buttons
        LinearLayout presetRow = timerView.findViewById(R.id.presetRow);
        for (int mins : PRESET_MINS) {
            Button b = new Button(this);
            b.setText(mins + "m");
            b.setTag(mins);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(4, 0, 4, 0);
            b.setLayoutParams(lp);
            b.setOnClickListener(v -> {
                if (timerRunning || timerPaused) return;
                selectedMins  = (int) v.getTag();
                remainingSecs = selectedMins * 60;
                updateTimerUI();
                highlightPreset(presetRow, (int) v.getTag());
            });
            presetRow.addView(b);
        }
        selectedMins  = 25;
        remainingSecs = 25 * 60;

        rgMode.setOnCheckedChangeListener((group, id) -> {
            if (timerRunning || timerPaused) { return; }
            timerMode = (id == R.id.rbStrict) ? "strict" : "normal";
            updateModeUI();
        });

        btnStart.setOnClickListener(v -> handleStartPause());
        btnStop.setOnClickListener(v -> handleStop());
        blockedPreview.setOnClickListener(v -> showTab(1));

        tvTimerDisplay.setText(formatSecs(remainingSecs));
        updateModeUI();
        updateTimerUI();

        // Apps tab
        appsView       = inf.inflate(R.layout.tab_apps, container, false);
        appsListView   = appsView.findViewById(R.id.appsListView);
        etSearch       = appsView.findViewById(R.id.etSearch);
        tvBlockedSummary = appsView.findViewById(R.id.tvBlockedSummary);
        btnBlockAll    = appsView.findViewById(R.id.btnBlockAll);
        btnUnblockAll  = appsView.findViewById(R.id.btnUnblockAll);
        btnResetDefault = appsView.findViewById(R.id.btnResetDefault);

        btnBlockAll.setOnClickListener(v -> {
            for (AppInfo a : allApps) { a.isBlocked = true; blockedPackages.add(a.packageName); }
            saveAndRefreshApps();
        });
        btnUnblockAll.setOnClickListener(v -> {
            for (AppInfo a : allApps) { a.isBlocked = false; blockedPackages.clear(); }
            saveAndRefreshApps();
        });
        btnResetDefault.setOnClickListener(v -> {
            prefs.setBlockedPackages(new HashSet<>());
            blockedPackages = prefs.getBlockedPackages(); // triggers default
            // mark defaults
            for (AppInfo a : allApps) {
                a.isBlocked = blockedPackages.contains(a.packageName);
            }
            saveAndRefreshApps();
        });
        etSearch.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            public void onTextChanged(CharSequence s, int st, int b, int c) { filterApps(s.toString()); }
            public void afterTextChanged(android.text.Editable s) {}
        });

        // Stats tab
        statsView       = inf.inflate(R.layout.tab_stats, container, false);
        tvTotalFocus    = statsView.findViewById(R.id.tvTotalFocus);
        tvCompletionRate = statsView.findViewById(R.id.tvCompletionRate);
        tvNormal        = statsView.findViewById(R.id.tvNormal);
        tvStrict        = statsView.findViewById(R.id.tvStrict);
        sessionsListView = statsView.findViewById(R.id.sessionsListView);
        btnExport       = statsView.findViewById(R.id.btnExport);
        btnClearHistory = statsView.findViewById(R.id.btnClearHistory);

        btnExport.setOnClickListener(v -> exportSessions());
        btnClearHistory.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("Clear History")
                .setMessage("Delete all session records?")
                .setPositiveButton("Delete", (d, w) -> {
                    prefs.clearSessions();
                    refreshStats();
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }

    private void buildTabs() {
        tabLayout.addTab(tabLayout.newTab().setText("⏱ Timer"));
        tabLayout.addTab(tabLayout.newTab().setText("🚫 Block Apps"));
        tabLayout.addTab(tabLayout.newTab().setText("📊 Sessions"));
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            public void onTabSelected(TabLayout.Tab tab) { showTab(tab.getPosition()); }
            public void onTabUnselected(TabLayout.Tab tab) {}
            public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void showTab(int pos) {
        tabLayout.selectTab(tabLayout.getTabAt(pos));
        container.removeAllViews();
        switch (pos) {
            case 0: container.addView(timerView); updateTimerUI(); break;
            case 1: container.addView(appsView);  refreshAppsUI(); break;
            case 2: container.addView(statsView); refreshStats(); break;
        }
    }

    // ── Timer logic ───────────────────────────────────────────
   private void handleStartPause() {
        if (!timerRunning && !timerPaused) {
            if (!hasUsagePermission()) {
                requestUsagePermission();
                return;
            }
            startTimer();
        } else if (timerRunning && !"strict".equals(timerMode)) {
            pauseTimer();
        } else if (timerRunning && "strict".equals(timerMode)) {
            // strict mode running — do nothing, just ignore tap
            return;
        } else if (timerPaused) {
            resumeTimer();
        }
    }

    private void startTimer() {
        startEpoch    = System.currentTimeMillis();
        startIso      = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date());
        endEpoch      = startEpoch + (long) selectedMins * 60 * 1000;
        remainingSecs = selectedMins * 60;
        timerRunning  = true;
        timerPaused   = false;

        prefs.saveTimerState(true, timerMode, selectedMins, endEpoch, startIso);
        startService(new Intent(this, AppMonitorService.class).setAction(AppMonitorService.ACTION_START));
        updateTimerUI();
    }

    private void pauseTimer() {
        timerRunning = false;
        timerPaused  = true;
        stopService(new Intent(this, AppMonitorService.class).setAction(AppMonitorService.ACTION_STOP));
        updateTimerUI();
    }

    private void resumeTimer() {
        // Recalculate end time from remaining
        endEpoch      = System.currentTimeMillis() + (long) remainingSecs * 1000;
        timerRunning  = true;
        timerPaused   = false;
        prefs.saveTimerState(true, timerMode, selectedMins, endEpoch, startIso);
        startService(new Intent(this, AppMonitorService.class).setAction(AppMonitorService.ACTION_START));
        updateTimerUI();
    }

    private void handleStop() {
        if ("strict".equals(timerMode) && timerRunning) return;
        int actual = selectedMins - (remainingSecs / 60);
        onTimerFinished(false);
        // Save partial session
        if (startIso != null && !startIso.isEmpty()) {
            SessionData s = new SessionData(timerMode, selectedMins, Math.max(1, actual),
                startIso,
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date()),
                false, blockedPackages.size());
            prefs.addSession(s);
        }
    }

    private void onTimerFinished(boolean completed) {
        if (completed && startIso != null && !startIso.isEmpty()) {
            SessionData s = new SessionData(timerMode, selectedMins, selectedMins,
                startIso,
                new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault()).format(new Date()),
                true, blockedPackages.size());
            prefs.addSession(s);
            showCompletionDialog();
        }
        stopService(new Intent(this, AppMonitorService.class).setAction(AppMonitorService.ACTION_STOP));
        prefs.clearTimerState();
        timerRunning  = false;
        timerPaused   = false;
        remainingSecs = selectedMins * 60;
        startIso      = null;
        updateTimerUI();
    }

    private void showCompletionDialog() {
        new AlertDialog.Builder(this)
            .setTitle("🎉 Session Complete!")
            .setMessage("Great work! You completed a " + selectedMins + "m " + timerMode + " session.")
            .setPositiveButton("Continue", null)
            .show();
    }

    private void updateTimerUI() {
        if (tvTimerDisplay == null) return;
        tvTimerDisplay.setText(formatSecs(remainingSecs));

        boolean canStop = (timerRunning || timerPaused) && !("strict".equals(timerMode) && timerRunning);
        btnStop.setEnabled(canStop);
        btnStop.setAlpha(canStop ? 1f : 0.3f);

        if (!timerRunning && !timerPaused) {
            tvStatus.setText("Ready");
            btnStart.setText("▶  Start Focus");
            rgMode.setEnabled(true);
            rbNormal.setEnabled(true);
            rbStrict.setEnabled(true);
            lockBanner.setVisibility(View.GONE);
            activeBlockList.setVisibility(View.GONE);
        } else if (timerPaused) {
            tvStatus.setText("Paused");
            btnStart.setText("▶  Resume");
            lockBanner.setVisibility(View.GONE);
        } else {
            // Running
            if ("strict".equals(timerMode)) {
                tvStatus.setText("LOCKED IN");
                btnStart.setText("🔒  Locked");
                btnStart.setEnabled(false);
                btnStart.setAlpha(0.5f);
                lockBanner.setVisibility(View.VISIBLE);
            } else {
                tvStatus.setText("Focusing...");
                btnStart.setText("⏸  Pause");
                btnStart.setEnabled(true);
                lockBanner.setVisibility(View.GONE);
            }
            rgMode.setEnabled(false);
            rbNormal.setEnabled(false);
            rbStrict.setEnabled(false);
            showActiveBlockList();
        }

        updateBlockedPreview();
    }

    private void updateModeUI() {
        if ("strict".equals(timerMode)) {
            tvModeLabel.setText("🔒 Strict — locked until timer ends");
        } else {
            tvModeLabel.setText("🌿 Normal — pause or stop anytime");
        }
    }

    private void showActiveBlockList() {
        activeBlockList.setVisibility(View.VISIBLE);
        activeBlockList.removeAllViews();
        TextView label = new TextView(this);
        label.setText("🚫 Blocking during this session:");
        label.setTextSize(12f);
        label.setPadding(0, 0, 0, 8);
        activeBlockList.addView(label);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int count = 0;
        for (AppInfo a : allApps) {
            if (!a.isBlocked) continue;
            TextView chip = new TextView(this);
            chip.setText(a.appName);
            chip.setTextSize(11f);
            chip.setPadding(12, 4, 12, 4);
            chip.setBackgroundResource(R.drawable.chip_bg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 6, 6);
            chip.setLayoutParams(lp);
            row.addView(chip);
            count++;
            if (count >= 8) break;
        }
        activeBlockList.addView(row);
    }

    private void updateBlockedPreview() {
        int n = blockedPackages.size();
        tvBlockedCount.setText(n + " apps will be blocked");
    }

    private void highlightPreset(LinearLayout row, int selected) {
        for (int i = 0; i < row.getChildCount(); i++) {
            View child = row.getChildAt(i);
            if (child instanceof Button) {
                child.setSelected((int) child.getTag() == selected);
            }
        }
    }

    // ── Apps tab ──────────────────────────────────────────────
    private void loadInstalledApps() {
    new Thread(() -> {
        PackageManager pm = getPackageManager();
        Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
        mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<android.content.pm.ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
        List<AppInfo> apps = new ArrayList<>();
        for (android.content.pm.ResolveInfo ri : resolveInfos) {
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(getPackageName())) continue;
            String name = ri.loadLabel(pm).toString();
            boolean blocked = blockedPackages.contains(pkg);
            apps.add(new AppInfo(pkg, name, "App", blocked));
        }
        apps.sort((a, b) -> a.appName.compareToIgnoreCase(b.appName));
        handler.post(() -> {
            allApps.clear();
            allApps.addAll(apps);
            filterApps("");
        });
    }).start();
}

    private void filterApps(String query) {
        filteredApps.clear();
        for (AppInfo a : allApps) {
            if (query.isEmpty() || a.appName.toLowerCase().contains(query.toLowerCase())) {
                filteredApps.add(a);
            }
        }
        refreshAppsUI();
    }

    private void refreshAppsUI() {
        if (appsListView == null) return;
        int blocked = 0;
        for (AppInfo a : allApps) if (a.isBlocked) blocked++;
        tvBlockedSummary.setText(blocked + " of " + allApps.size() + " apps will be blocked");

        AppListAdapter adapter = new AppListAdapter();
        appsListView.setAdapter(adapter);
    }

    private void saveAndRefreshApps() {
        prefs.setBlockedPackages(blockedPackages);
        refreshAppsUI();
        updateBlockedPreview();
    }

    class AppListAdapter extends BaseAdapter {
        @Override public int getCount() { return filteredApps.size(); }
        @Override public AppInfo getItem(int i) { return filteredApps.get(i); }
        @Override public long getItemId(int i) { return i; }

        @Override
        public View getView(int pos, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(MainActivity.this)
                    .inflate(R.layout.item_app, parent, false);
            }
            AppInfo app = filteredApps.get(pos);
            TextView tvName = convertView.findViewById(R.id.tvAppName);
            Switch   sw     = convertView.findViewById(R.id.swBlocked);
            TextView tvPkg  = convertView.findViewById(R.id.tvPkgName);

            tvName.setText(app.appName);
            tvPkg.setText(app.packageName);
            sw.setOnCheckedChangeListener(null);
            sw.setChecked(app.isBlocked);
            sw.setEnabled(!timerRunning);

            sw.setOnCheckedChangeListener((btn, checked) -> {
                if (timerRunning) { btn.setChecked(!checked); return; }
                app.isBlocked = checked;
                if (checked) blockedPackages.add(app.packageName);
                else         blockedPackages.remove(app.packageName);
                saveAndRefreshApps();
            });

            convertView.setOnClickListener(v -> {
                if (timerRunning) return;
                sw.toggle();
            });
            return convertView;
        }
    }

    // ── Stats tab ─────────────────────────────────────────────
    private void refreshStats() {
        if (tvTotalFocus == null) return;
        List<SessionData> sessions = prefs.getSessions();
        int total    = 0, comp = 0, norm = 0, str = 0;
        for (SessionData s : sessions) {
            total += s.actualMinutes;
            if (s.completed) comp++;
            if ("normal".equals(s.mode)) norm++; else str++;
        }
        tvTotalFocus.setText(formatMins(total));
        tvCompletionRate.setText(sessions.isEmpty() ? "—" :
            Math.round((comp * 100f) / sessions.size()) + "%");
        tvNormal.setText(String.valueOf(norm));
        tvStrict.setText(String.valueOf(str));

        sessionsListView.setAdapter(new SessionAdapter(sessions));
    }

    class SessionAdapter extends BaseAdapter {
        List<SessionData> list;
        SessionAdapter(List<SessionData> l) { list = l; }
        @Override public int getCount() { return list.size(); }
        @Override public SessionData getItem(int i) { return list.get(i); }
        @Override public long getItemId(int i) { return list.get(i).id; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            if (cv == null)
                cv = LayoutInflater.from(MainActivity.this).inflate(R.layout.item_session, parent, false);
            SessionData s = list.get(pos);
            ((TextView) cv.findViewById(R.id.tvSMode)).setText(s.mode.toUpperCase());
            ((TextView) cv.findViewById(R.id.tvSDuration)).setText(formatMins(s.actualMinutes));
            ((TextView) cv.findViewById(R.id.tvSTime)).setText(
                s.startTime.replace("T", " ").substring(0, 16) + " → " +
                s.endTime.replace("T", " ").substring(11, 16));
            ((TextView) cv.findViewById(R.id.tvSStatus)).setText(s.completed ? "✓ Done" : "⚠ Early stop");
            return cv;
        }
    }

    private void exportSessions() {
        List<SessionData> sessions = prefs.getSessions();
        if (sessions.isEmpty()) {
            Toast.makeText(this, "No sessions to export", Toast.LENGTH_SHORT).show();
            return;
        }
        // Build CSV
        StringBuilder csv = new StringBuilder();
        csv.append("Session,Mode,Date,Start,End,Planned (min),Actual (min),Completed,Blocked Apps\n");
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
        for (int i = 0; i < sessions.size(); i++) {
            SessionData s = sessions.get(i);
            csv.append(i + 1).append(",")
               .append(s.mode).append(",")
               .append(s.startTime.substring(0, 10)).append(",")
               .append(s.startTime.substring(11, 16)).append(",")
               .append(s.endTime.substring(11, 16)).append(",")
               .append(s.plannedMinutes).append(",")
               .append(s.actualMinutes).append(",")
               .append(s.completed ? "Yes" : "No").append(",")
               .append(s.blockedAppsCount).append("\n");
        }
        // Save to Downloads
        try {
            String filename = "focus_sessions_" + sdf.format(new Date()) + ".csv";
            java.io.File dir = android.os.Environment.getExternalStoragePublicDirectory(
                android.os.Environment.DIRECTORY_DOWNLOADS);
            java.io.File file = new java.io.File(dir, filename);
            java.io.FileWriter fw = new java.io.FileWriter(file);
            fw.write(csv.toString());
            fw.close();
            Toast.makeText(this, "Saved to Downloads/" + filename, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Export failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ── Permissions ───────────────────────────────────────────
    private void checkPermissions() {
        if (!hasUsagePermission()) {
            new AlertDialog.Builder(this)
                .setTitle("Permission Required")
                .setMessage("Focus Timer needs Usage Access permission to detect and block apps.\n\nTap OK to open Settings → Usage Access → enable Focus Timer.")
                .setPositiveButton("Open Settings", (d, w) -> requestUsagePermission())
                .setNegativeButton("Later", null)
                .show();
        }
        if (!Settings.canDrawOverlays(this)) {
            new AlertDialog.Builder(this)
                .setTitle("Overlay Permission")
                .setMessage("Focus Timer needs to draw over other apps to show the block screen.\n\nTap OK to grant permission.")
                .setPositiveButton("Open Settings", (d, w) -> {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                })
                .setNegativeButton("Later", null)
                .show();
        }
    }

    private boolean hasUsagePermission() {
        AppOpsManager aom = (AppOpsManager) getSystemService(APP_OPS_SERVICE);
        int mode = aom.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(), getPackageName());
        return mode == AppOpsManager.MODE_ALLOWED;
    }

    private void requestUsagePermission() {
        startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
    }

    // ── Helpers ───────────────────────────────────────────────
    private String formatSecs(int secs) {
        return String.format(Locale.getDefault(), "%02d:%02d", secs / 60, secs % 60);
    }

    private String formatMins(int mins) {
        if (mins >= 60) return (mins / 60) + "h " + (mins % 60) + "m";
        return mins + "m";
    }
}
