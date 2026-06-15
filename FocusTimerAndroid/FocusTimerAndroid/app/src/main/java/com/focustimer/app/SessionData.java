package com.focustimer.app;

public class SessionData {
    public long id;
    public String mode;          // "normal" or "strict"
    public int plannedMinutes;
    public int actualMinutes;
    public String startTime;
    public String endTime;
    public boolean completed;
    public int blockedAppsCount;

    public SessionData() {}

    public SessionData(String mode, int plannedMinutes, int actualMinutes,
                       String startTime, String endTime,
                       boolean completed, int blockedAppsCount) {
        this.id = System.currentTimeMillis();
        this.mode = mode;
        this.plannedMinutes = plannedMinutes;
        this.actualMinutes = actualMinutes;
        this.startTime = startTime;
        this.endTime = endTime;
        this.completed = completed;
        this.blockedAppsCount = blockedAppsCount;
    }
}
