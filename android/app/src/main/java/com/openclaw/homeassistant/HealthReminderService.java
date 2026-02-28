package com.openclaw.homeassistant;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;

import java.util.Calendar;

/**
 * 健康提醒服务
 * 功能：久坐、喝水、眼保健提醒
 */
public class HealthReminderService {
    
    private static final String TAG = "HealthReminderService";
    private static final String PREFS_NAME = "health_reminders";
    
    private final Context context;
    private final AlarmManager alarmManager;
    private final PowerManager powerManager;
    private final SharedPreferences prefs;
    
    // 默认间隔 (分钟)
    private static final int DEFAULT_SIT_INTERVAL = 60;      // 久坐：60 分钟
    private static final int DEFAULT_WATER_INTERVAL = 120;   // 喝水：120 分钟
    private static final int DEFAULT_EYE_INTERVAL = 45;      // 眼保健：45 分钟
    
    // 开关状态
    private boolean sitReminderEnabled = true;
    private boolean waterReminderEnabled = true;
    private boolean eyeReminderEnabled = true;
    
    // 间隔设置
    private int sitInterval = DEFAULT_SIT_INTERVAL;
    private int waterInterval = DEFAULT_WATER_INTERVAL;
    private int eyeInterval = DEFAULT_EYE_INTERVAL;
    
    // 工作时间段
    private int workStartHour = 9;
    private int workEndHour = 18;
    
    public interface HealthReminderListener {
        void onSitReminder();
        void onWaterReminder();
        void onEyeReminder();
    }
    
    private static HealthReminderListener listener;
    
    public HealthReminderService(Context context) {
        this.context = context.getApplicationContext();
        this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        this.powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        loadSettings();
    }
    
    public static void setListener(HealthReminderListener listener) {
        HealthReminderService.listener = listener;
    }
    
    /**
     * 加载设置
     */
    private void loadSettings() {
        sitReminderEnabled = prefs.getBoolean("sit_enabled", true);
        waterReminderEnabled = prefs.getBoolean("water_enabled", true);
        eyeReminderEnabled = prefs.getBoolean("eye_enabled", true);
        
        sitInterval = prefs.getInt("sit_interval", DEFAULT_SIT_INTERVAL);
        waterInterval = prefs.getInt("water_interval", DEFAULT_WATER_INTERVAL);
        eyeInterval = prefs.getInt("eye_interval", DEFAULT_EYE_INTERVAL);
        
        workStartHour = prefs.getInt("work_start", 9);
        workEndHour = prefs.getInt("work_end", 18);
    }
    
    /**
     * 保存设置
     */
    public void saveSettings() {
        prefs.edit()
            .putBoolean("sit_enabled", sitReminderEnabled)
            .putBoolean("water_enabled", waterReminderEnabled)
            .putBoolean("eye_enabled", eyeReminderEnabled)
            .putInt("sit_interval", sitInterval)
            .putInt("water_interval", waterInterval)
            .putInt("eye_interval", eyeInterval)
            .putInt("work_start", workStartHour)
            .putInt("work_end", workEndHour)
            .apply();
    }
    
    /**
     * 启动所有提醒
     */
    public void startAllReminders() {
        loadSettings();
        
        if (sitReminderEnabled) startSitReminder();
        if (waterReminderEnabled) startWaterReminder();
        if (eyeReminderEnabled) startEyeReminder();
        
        Log.d(TAG, "健康提醒已启动");
    }
    
    /**
     * 停止所有提醒
     */
    public void stopAllReminders() {
        stopSitReminder();
        stopWaterReminder();
        stopEyeReminder();
        
        Log.d(TAG, "健康提醒已停止");
    }
    
    // ============== 久坐提醒 ==============
    
    public void startSitReminder() {
        scheduleRepeatingAlarm("SIT_REMINDER", sitInterval);
        Log.d(TAG, "久坐提醒已启动：" + sitInterval + "分钟");
    }
    
    public void stopSitReminder() {
        cancelAlarm("SIT_REMINDER");
    }
    
    public void triggerSitReminder() {
        Log.d(TAG, "久坐提醒触发");
        if (listener != null) {
            listener.onSitReminder();
        }
    }
    
    // ============== 喝水提醒 ==============
    
    public void startWaterReminder() {
        scheduleRepeatingAlarm("WATER_REMINDER", waterInterval);
        Log.d(TAG, "喝水提醒已启动：" + waterInterval + "分钟");
    }
    
    public void stopWaterReminder() {
        cancelAlarm("WATER_REMINDER");
    }
    
    public void triggerWaterReminder() {
        Log.d(TAG, "喝水提醒触发");
        if (listener != null) {
            listener.onWaterReminder();
        }
    }
    
    // ============== 眼保健提醒 ==============
    
    public void startEyeReminder() {
        scheduleRepeatingAlarm("EYE_REMINDER", eyeInterval);
        Log.d(TAG, "眼保健提醒已启动：" + eyeInterval + "分钟");
    }
    
    public void stopEyeReminder() {
        cancelAlarm("EYE_REMINDER");
    }
    
    public void triggerEyeReminder() {
        Log.d(TAG, "眼保健提醒触发");
        if (listener != null) {
            listener.onEyeReminder();
        }
    }
    
    // ============== 闹钟调度 ==============
    
    private void scheduleRepeatingAlarm(String action, int intervalMinutes) {
        if (alarmManager == null) return;
        
        Intent intent = new Intent(context, HealthReminderReceiver.class);
        intent.setAction(action);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        // 取消旧的
        alarmManager.cancel(pendingIntent);
        
        // 计算下次触发时间 (下一个整点)
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.add(Calendar.MINUTE, intervalMinutes);
        
        // 检查是否在工作时间段
        if (!isWorkHours(calendar.get(Calendar.HOUR_OF_DAY))) {
            // 跳到下一个工作日开始
            calendar.set(Calendar.HOUR_OF_DAY, workStartHour);
            if (calendar.getTimeInMillis() <= System.currentTimeMillis()) {
                calendar.add(Calendar.DAY_OF_YEAR, 1);
            }
        }
        
        // 设置闹钟
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
            );
        } else {
            alarmManager.setExact(
                AlarmManager.RTC_WAKEUP,
                calendar.getTimeInMillis(),
                pendingIntent
            );
        }
    }
    
    private void cancelAlarm(String action) {
        if (alarmManager == null) return;
        
        Intent intent = new Intent(context, HealthReminderReceiver.class);
        intent.setAction(action);
        
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
            context, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        alarmManager.cancel(pendingIntent);
    }
    
    // ============== 工具方法 ==============
    
    private boolean isWorkHours(int hour) {
        return hour >= workStartHour && hour < workEndHour;
    }
    
    // ============== Getter/Setter ==============
    
    public boolean isSitReminderEnabled() { return sitReminderEnabled; }
    public void setSitReminderEnabled(boolean enabled) { 
        this.sitReminderEnabled = enabled; 
        if (enabled) startSitReminder(); else stopSitReminder();
    }
    
    public boolean isWaterReminderEnabled() { return waterReminderEnabled; }
    public void setWaterReminderEnabled(boolean enabled) { 
        this.waterReminderEnabled = enabled; 
        if (enabled) startWaterReminder(); else stopWaterReminder();
    }
    
    public boolean isEyeReminderEnabled() { return eyeReminderEnabled; }
    public void setEyeReminderEnabled(boolean enabled) { 
        this.eyeReminderEnabled = enabled; 
        if (enabled) startEyeReminder(); else stopEyeReminder();
    }
    
    public int getSitInterval() { return sitInterval; }
    public void setSitInterval(int minutes) { 
        this.sitInterval = minutes; 
        if (sitReminderEnabled) startSitReminder();
    }
    
    public int getWaterInterval() { return waterInterval; }
    public void setWaterInterval(int minutes) { 
        this.waterInterval = minutes; 
        if (waterReminderEnabled) startWaterReminder();
    }
    
    public int getEyeInterval() { return eyeInterval; }
    public void setEyeInterval(int minutes) { 
        this.eyeInterval = minutes; 
        if (eyeReminderEnabled) startEyeReminder();
    }
    
    public int getWorkStartHour() { return workStartHour; }
    public void setWorkStartHour(int hour) { this.workStartHour = hour; }
    
    public int getWorkEndHour() { return workEndHour; }
    public void setWorkEndHour(int hour) { this.workEndHour = hour; }
}
