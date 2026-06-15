package com.focustimer.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class PrefsManager {
    private static final String PREFS_NAME     = "FocusTimerPrefs";
    private static final String KEY_SESSIONS   = "sessions";
    private static final String KEY_BLOCKED    = "blocked_packages";
    private static final String KEY_MODE       = "timer_mode";
    private static final String KEY_DURATION   = "timer_duration";
    private static final String KEY_RUNNING    = "timer_running";
    private static final String KEY_END_TIME   = "timer_end_epoch";
    private static final String KEY_START_TIME = "timer_start_iso";

    private final SharedPreferences prefs;
    private final Gson gson = new Gson();

    public PrefsManager(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ── Sessions ──────────────────────────────────────────────
    public List<SessionData> getSessions() {
        String json = prefs.getString(KEY_SESSIONS, "[]");
        Type type = new TypeToken<List<SessionData>>(){}.getType();
        List<SessionData> list = gson.fromJson(json, type);
        return list != null ? list : new ArrayList<>();
    }

    public void addSession(SessionData s) {
        List<SessionData> list = getSessions();
        list.add(0, s);
        prefs.edit().putString(KEY_SESSIONS, gson.toJson(list)).apply();
    }

    public void clearSessions() {
        prefs.edit().remove(KEY_SESSIONS).apply();
    }

    // ── Blocked packages ──────────────────────────────────────
    public Set<String> getBlockedPackages() {
        String json = prefs.getString(KEY_BLOCKED, null);
        if (json == null) return getDefaultBlockedPackages();
        Type type = new TypeToken<Set<String>>(){}.getType();
        Set<String> set = gson.fromJson(json, type);
        return set != null ? set : new HashSet<>();
    }

    public void setBlockedPackages(Set<String> packages) {
        prefs.edit().putString(KEY_BLOCKED, gson.toJson(packages)).apply();
    }

    private Set<String> getDefaultBlockedPackages() {
        Set<String> defaults = new HashSet<>();
        // Social
        defaults.add("com.instagram.android");
        defaults.add("com.facebook.katana");
        defaults.add("com.twitter.android");
        defaults.add("com.snapchat.android");
        defaults.add("com.zhiliaoapp.musically"); // TikTok
        defaults.add("com.reddit.frontpage");
        defaults.add("com.pinterest");
        defaults.add("com.discord");
        // Messaging (distracting)
        defaults.add("com.whatsapp");
        defaults.add("org.telegram.messenger");
        defaults.add("com.facebook.orca"); // Messenger
        // Entertainment
        defaults.add("com.google.android.youtube");
        defaults.add("com.netflix.mediaclient");
        defaults.add("com.spotify.music");
        defaults.add("in.startv.hotstar");
        defaults.add("tv.twitch.android.app");
        // Games (common)
        defaults.add("com.tencent.ig"); // PUBG
        defaults.add("com.dena.a12026418"); // Free Fire
        defaults.add("com.king.candycrushsaga");
        defaults.add("com.supercell.clashofclans");
        defaults.add("com.roblox.client");
        // Shopping
        defaults.add("com.amazon.mShop.android.shopping");
        defaults.add("com.flipkart.android");
        defaults.add("com.myntra.android");
        // News
        defaults.add("com.dailyhunt.transit");
        defaults.add("com.eterno");
        return defaults;
    }

    // ── Active timer state ────────────────────────────────────
    public void saveTimerState(boolean running, String mode, int durationMins,
                               long endEpoch, String startIso) {
        prefs.edit()
            .putBoolean(KEY_RUNNING, running)
            .putString(KEY_MODE, mode)
            .putInt(KEY_DURATION, durationMins)
            .putLong(KEY_END_TIME, endEpoch)
            .putString(KEY_START_TIME, startIso)
            .apply();
    }

    public void clearTimerState() {
        prefs.edit()
            .putBoolean(KEY_RUNNING, false)
            .remove(KEY_END_TIME)
            .remove(KEY_START_TIME)
            .apply();
    }

    public boolean isTimerRunning()   { return prefs.getBoolean(KEY_RUNNING, false); }
    public String  getTimerMode()     { return prefs.getString(KEY_MODE, "normal"); }
    public int     getTimerDuration() { return prefs.getInt(KEY_DURATION, 25); }
    public long    getTimerEndEpoch() { return prefs.getLong(KEY_END_TIME, 0); }
    public String  getTimerStartIso() { return prefs.getString(KEY_START_TIME, ""); }
}
