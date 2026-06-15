package com.focustimer.app;

public class AppInfo {
    public String packageName;
    public String appName;
    public String category;
    public boolean isBlocked;

    public AppInfo(String packageName, String appName, String category, boolean isBlocked) {
        this.packageName = packageName;
        this.appName = appName;
        this.category = category;
        this.isBlocked = isBlocked;
    }
}
