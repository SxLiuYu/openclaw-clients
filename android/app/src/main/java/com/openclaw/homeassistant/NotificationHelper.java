package com.openclaw.homeassistant;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

/**
 * 通知辅助工具
 */
public class NotificationHelper {
    
    private static final String HEALTH_CHANNEL_ID = "health_reminders";
    private static final String AUTOMATION_CHANNEL_ID = "automation_notifications";
    
    /**
     * 发送健康提醒通知
     */
    public static void sendHealthNotification(Context context, String title, String message) {
        createHealthChannel(context);
        
        NotificationManager manager = (NotificationManager) 
            context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        
        Intent intent = new Intent(context, HealthRemindersActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, title.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, HEALTH_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent);
        
        manager.notify(title.hashCode(), builder.build());
    }
    
    /**
     * 创建健康通知渠道
     */
    private static void createHealthChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                HEALTH_CHANNEL_ID,
                "健康提醒",
                NotificationManager.IMPORTANCE_HIGH
            );
            channel.setDescription("久坐、喝水、眼保健提醒");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{100, 200, 100});
            
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
    
    /**
     * 创建自动化通知渠道
     */
    public static void createAutomationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                AUTOMATION_CHANNEL_ID,
                "自动化通知",
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("自动化场景触发的通知");
            
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }
}
