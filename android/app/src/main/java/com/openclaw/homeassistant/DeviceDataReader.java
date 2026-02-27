package com.openclaw.homeassistant;

import android.app.usage.UsageEvents;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.provider.Settings;

import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * 设备数据读取器
 * 功能：步数、应用使用时间、电池状态、网络状态等
 */
public class DeviceDataReader {
    
    private final Context context;
    
    public DeviceDataReader(Context context) {
        this.context = context;
    }
    
    /**
     * 读取今日步数（需要健康数据权限）
     */
    public int getStepCount() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                // 尝试从 Google Fit 或其他健康应用读取
                // 注意：需要 READ_HEALTH_DATA 权限
                return 0; // 需要集成 Google Fit API
            } catch (Exception e) {
                return 0;
            }
        }
        return 0;
    }
    
    /**
     * 获取应用使用时间（今天）
     */
    public long getAppUsageTime(String packageName) {
        UsageStatsManager usageStatsManager = (UsageStatsManager) 
            context.getSystemService(Context.USAGE_STATS_SERVICE);
        
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - 24 * 60 * 60 * 1000; // 24 小时前
        
        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, currentTime);
        long totalTime = 0;
        
        UsageEvents.Event event = new UsageEvents.Event();
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            if (event.getPackageName().equals(packageName) && 
                event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                // 简单估算（实际需要更复杂的逻辑）
                totalTime += 5 * 60 * 1000; // 假设每次使用 5 分钟
            }
        }
        
        return totalTime;
    }
    
    /**
     * 获取所有应用使用时间排名
     */
    public String getTopApps() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) 
            context.getSystemService(Context.USAGE_STATS_SERVICE);
        
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - 24 * 60 * 60 * 1000;
        
        SortedMap<Long, UsageEvents.Event> sortedMap = new TreeMap<>();
        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, currentTime);
        
        UsageEvents.Event event = new UsageEvents.Event();
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                sortedMap.put(event.getTimeStamp(), event);
            }
        }
        
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (UsageEvents.Event e : sortedMap.values()) {
            if (count >= 5) break;
            sb.append("• ").append(e.getPackageName()).append("\n");
            count++;
        }
        
        return sb.length() > 0 ? sb.toString() : "无数据";
    }
    
    /**
     * 获取电池状态
     */
    public String getBatteryStatus() {
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        Intent batteryStatus = context.registerReceiver(null, ifilter);
        
        if (batteryStatus == null) {
            return "电池信息不可用";
        }
        
        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int batteryPct = (int) ((level / (float) scale) * 100);
        
        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        boolean isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                            status == BatteryManager.BATTERY_STATUS_FULL;
        
        return String.format("电量：%d%% (%s)", batteryPct, isCharging ? "充电中" : "放电中");
    }
    
    /**
     * 获取屏幕使用时间（今天）
     */
    public String getScreenTime() {
        // 简化版本，实际需要更精确的计算
        UsageStatsManager usageStatsManager = (UsageStatsManager) 
            context.getSystemService(Context.USAGE_STATS_SERVICE);
        
        long currentTime = System.currentTimeMillis();
        long startTime = currentTime - 24 * 60 * 60 * 1000;
        
        UsageEvents usageEvents = usageStatsManager.queryEvents(startTime, currentTime);
        long screenOnTime = 0;
        
        UsageEvents.Event event = new UsageEvents.Event();
        long lastScreenOn = 0;
        
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event);
            if (event.getEventType() == UsageEvents.Event.SCREEN_INTERACTIVE) {
                lastScreenOn = event.getTimeStamp();
            } else if (event.getEventType() == UsageEvents.Event.SCREEN_NON_INTERACTIVE && lastScreenOn > 0) {
                screenOnTime += (event.getTimeStamp() - lastScreenOn);
                lastScreenOn = 0;
            }
        }
        
        long hours = screenOnTime / (1000 * 60 * 60);
        long minutes = (screenOnTime % (1000 * 60 * 60)) / (1000 * 60);
        
        return String.format("屏幕使用时间：%d 小时 %d 分钟", hours, minutes);
    }
    
    /**
     * 检查是否有 UsageStats 权限
     */
    public boolean hasUsageStatsPermission() {
        UsageStatsManager usageStatsManager = (UsageStatsManager) 
            context.getSystemService(Context.USAGE_STATS_SERVICE);
        
        long currentTime = System.currentTimeMillis();
        List<android.app.usage.UsageStats> stats = 
            usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, 
                currentTime - 1000 * 10, currentTime);
        
        return stats != null && !stats.isEmpty();
    }
    
    /**
     * 打开 UsageStats 权限设置
     */
    public void openUsageStatsSettings() {
        Intent intent = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
    
    /**
     * 获取设备信息摘要
     */
    public String getDeviceSummary() {
        StringBuilder sb = new StringBuilder();
        
        // 电池
        sb.append(getBatteryStatus()).append("\n");
        
        // 屏幕使用时间
        sb.append(getScreenTime()).append("\n");
        
        // 常用应用
        sb.append("\n常用应用:\n").append(getTopApps());
        
        return sb.toString();
    }
}
