package com.openclaw.homeassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 设备同步服务
 * 功能：设备注册、心跳上报、设备列表获取
 * 使用 LeanCloud 后端
 */
public class DeviceSyncService {
    
    private static final String TAG = "DeviceSyncService";
    private static final String PREFS_NAME = "device_sync";
    
    // LeanCloud 配置 (示例，实际使用需要替换)
    private static final String LC_APP_ID = "your-leancloud-app-id";
    private static final String LC_APP_KEY = "your-leancloud-app-key";
    private static final String LC_SERVER = "https://your-app-id.api.lncldglobal.com";
    
    // 本地配置
    private final Context context;
    private final SharedPreferences prefs;
    private final String deviceId;
    private final String deviceName;
    private final String deviceModel;
    
    // 心跳
    private Thread heartbeatThread;
    private boolean isRunning = false;
    private static final long HEARTBEAT_INTERVAL = 60000; // 1 分钟
    
    public interface DeviceSyncListener {
        void onDevicesUpdated(List<DeviceInfo> devices);
        void onRegisterSuccess(String userId);
        void onError(String error);
    }
    
    private static DeviceSyncListener listener;
    
    public DeviceSyncService(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // 获取设备信息
        this.deviceId = getDeviceId();
        this.deviceName = getDeviceName();
        this.deviceModel = Build.BRAND + " " + Build.MODEL;
        
        Log.d(TAG, "设备初始化：" + deviceName + " (" + deviceId + ")");
    }
    
    public static void setListener(DeviceSyncListener listener) {
        DeviceSyncService.listener = listener;
    }
    
    /**
     * 获取设备 ID
     */
    private String getDeviceId() {
        String savedId = prefs.getString("device_id", null);
        if (savedId != null) {
            return savedId;
        }
        
        // 生成新 ID
        String newId = Settings.Secure.getString(
            context.getContentResolver(),
            Settings.Secure.ANDROID_ID
        );
        
        if (newId == null || newId.equals("9774d56d682e549c")) {
            // ANDROID_ID 不可用，使用 UUID
            newId = java.util.UUID.randomUUID().toString();
        }
        
        prefs.edit().putString("device_id", newId).apply();
        return newId;
    }
    
    /**
     * 获取设备名称
     */
    private String getDeviceName() {
        String savedName = prefs.getString("device_name", null);
        if (savedName != null) {
            return savedName;
        }
        
        // 默认名称
        String defaultName = Build.BRAND + " " + Build.MODEL;
        prefs.edit().putString("device_name", defaultName).apply();
        return defaultName;
    }
    
    /**
     * 设置设备名称
     */
    public void setDeviceName(String name) {
        prefs.edit().putString("device_name", name).apply();
    }
    
    /**
     * 注册用户
     */
    public void registerUser(String username, String password) {
        new Thread(() -> {
            try {
                JSONObject userData = new JSONObject();
                userData.put("username", username);
                userData.put("password", password);
                
                JSONObject response = sendRequest("/1.1/users", userData, "POST");
                
                if (response != null) {
                    String userId = response.getString("objectId");
                    String sessionToken = response.getString("sessionToken");
                    
                    prefs.edit()
                        .putString("user_id", userId)
                        .putString("session_token", sessionToken)
                        .putString("username", username)
                        .apply();
                    
                    // 注册设备
                    registerDevice(userId);
                    
                    if (listener != null) {
                        listener.onRegisterSuccess(userId);
                    }
                    
                    Log.d(TAG, "用户注册成功：" + userId);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "用户注册失败", e);
                if (listener != null) {
                    listener.onError("注册失败：" + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 登录用户
     */
    public void loginUser(String username, String password) {
        new Thread(() -> {
            try {
                JSONObject userData = new JSONObject();
                userData.put("username", username);
                userData.put("password", password);
                
                JSONObject response = sendRequest("/1.1/login", userData, "POST");
                
                if (response != null) {
                    String userId = response.getString("objectId");
                    String sessionToken = response.getString("sessionToken");
                    
                    prefs.edit()
                        .putString("user_id", userId)
                        .putString("session_token", sessionToken)
                        .putString("username", username)
                        .apply();
                    
                    // 注册/更新设备
                    registerDevice(userId);
                    
                    Log.d(TAG, "用户登录成功：" + userId);
                }
                
            } catch (Exception e) {
                Log.e(TAG, "用户登录失败", e);
                if (listener != null) {
                    listener.onError("登录失败：" + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 注册设备
     */
    private void registerDevice(String userId) {
        try {
            String sessionToken = prefs.getString("session_token", null);
            if (sessionToken == null) return;
            
            JSONObject deviceData = new JSONObject();
            deviceData.put("device_id", deviceId);
            deviceData.put("device_name", deviceName);
            deviceData.put("device_model", deviceModel);
            deviceData.put("app_version", "1.4");
            deviceData.put("os_version", Build.VERSION.RELEASE);
            deviceData.put("user_id", userId);
            deviceData.put("status", "online");
            
            // 检查设备是否已存在
            JSONObject query = new JSONObject();
            query.put("device_id", deviceId);
            
            JSONObject response = sendRequest(
                "/1.1/classes/Device?where=" + 
                java.net.URLEncoder.encode(query.toString(), "UTF-8"),
                null,
                "GET"
            );
            
            JSONArray results = null;
            if (response != null) {
                results = response.optJSONArray("results");
            }
            
            if (results == null || results.length() == 0) {
                // 新设备，创建
                sendRequest("/1.1/classes/Device", deviceData, "POST");
                Log.d(TAG, "设备注册成功");
            } else {
                // 设备已存在，更新
                if (results.length() > 0) {
                    String objectId = results.getJSONObject(0).getString("objectId");
                    sendRequest("/1.1/classes/Device/" + objectId, deviceData, "PUT");
                    Log.d(TAG, "设备更新成功");
                }
            }
            
            // 启动心跳
            startHeartbeat();
            
        } catch (Exception e) {
            Log.e(TAG, "设备注册失败", e);
        }
    }
    
    /**
     * 启动心跳
     */
    public void startHeartbeat() {
        if (isRunning) return;
        
        isRunning = true;
        heartbeatThread = new Thread(() -> {
            while (isRunning) {
                try {
                    sendHeartbeat();
                    Thread.sleep(HEARTBEAT_INTERVAL);
                } catch (Exception e) {
                    Log.e(TAG, "心跳失败", e);
                }
            }
        });
        heartbeatThread.start();
        
        Log.d(TAG, "心跳已启动");
    }
    
    /**
     * 停止心跳
     */
    public void stopHeartbeat() {
        isRunning = false;
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
        Log.d(TAG, "心跳已停止");
    }
    
    /**
     * 发送心跳
     */
    private void sendHeartbeat() {
        try {
            String sessionToken = prefs.getString("session_token", null);
            if (sessionToken == null) return;
            
            // 查询设备
            JSONObject query = new JSONObject();
            query.put("device_id", deviceId);
            
            JSONObject response = sendRequest(
                "/1.1/classes/Device?where=" + 
                java.net.URLEncoder.encode(query.toString(), "UTF-8"),
                null,
                "GET"
            );
            
            if (response != null) {
                JSONArray results = response.getJSONArray("results");
                if (results.length() > 0) {
                    JSONObject device = results.getJSONObject(0);
                    String objectId = device.getString("objectId");
                    
                    // 更新心跳时间
                    JSONObject updateData = new JSONObject();
                    updateData.put("status", "online");
                    updateData.put("last_seen", System.currentTimeMillis() / 1000);
                    
                    // 添加电量信息
                    android.content.IntentFilter ifilter = 
                        new android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED);
                    android.content.Intent batteryStatus = 
                        context.registerReceiver(null, ifilter);
                    if (batteryStatus != null) {
                        int level = batteryStatus.getIntExtra(
                            android.os.BatteryManager.EXTRA_LEVEL, -1);
                        int scale = batteryStatus.getIntExtra(
                            android.os.BatteryManager.EXTRA_SCALE, -1);
                        int batteryPct = (int) ((level / (float) scale) * 100);
                        updateData.put("battery", batteryPct);
                    }
                    
                    sendRequest("/1.1/classes/Device/" + objectId, updateData, "PUT");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "发送心跳失败", e);
        }
    }
    
    /**
     * 获取设备列表
     */
    public void fetchDeviceList() {
        new Thread(() -> {
            try {
                String sessionToken = prefs.getString("session_token", null);
                if (sessionToken == null) {
                    if (listener != null) {
                        listener.onError("未登录");
                    }
                    return;
                }
                
                String userId = prefs.getString("user_id", null);
                if (userId == null) {
                    if (listener != null) {
                        listener.onError("用户 ID 不存在");
                    }
                    return;
                }
                
                // 查询用户的所有设备
                JSONObject query = new JSONObject();
                query.put("user_id", userId);
                
                JSONObject response = sendRequest(
                    "/1.1/classes/Device?where=" + 
                    java.net.URLEncoder.encode(query.toString(), "UTF-8") + 
                    "&order=-last_seen",
                    null,
                    "GET"
                );
                
                if (response != null) {
                    JSONArray results = response.getJSONArray("results");
                    List<DeviceInfo> devices = new ArrayList<>();
                    
                    long currentTime = System.currentTimeMillis() / 1000;
                    
                    for (int i = 0; i < results.length(); i++) {
                        JSONObject device = results.getJSONObject(i);
                        
                        DeviceInfo info = new DeviceInfo();
                        info.objectId = device.getString("objectId");
                        info.deviceId = device.getString("device_id");
                        info.deviceName = device.getString("device_name");
                        info.deviceModel = device.optString("device_model", "未知设备");
                        info.status = device.optString("status", "offline");
                        info.battery = device.optInt("battery", -1);
                        
                        long lastSeen = device.optLong("last_seen", 0);
                        long diff = currentTime - lastSeen;
                        
                        if (diff < 120) {
                            info.status = "online";
                            info.lastSeen = "刚刚";
                        } else if (diff < 3600) {
                            info.status = "offline";
                            info.lastSeen = (diff / 60) + "分钟前";
                        } else if (diff < 86400) {
                            info.status = "offline";
                            info.lastSeen = (diff / 3600) + "小时前";
                        } else {
                            info.status = "offline";
                            info.lastSeen = (diff / 86400) + "天前";
                        }
                        
                        devices.add(info);
                    }
                    
                    if (listener != null) {
                        listener.onDevicesUpdated(devices);
                    }
                }
                
            } catch (Exception e) {
                Log.e(TAG, "获取设备列表失败", e);
                if (listener != null) {
                    listener.onError("获取设备列表失败：" + e.getMessage());
                }
            }
        }).start();
    }
    
    /**
     * 发送 LeanCloud 请求
     */
    private JSONObject sendRequest(String path, JSONObject data, String method) {
        try {
            URL url = new URL(LC_SERVER + path);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(method);
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            
            // 设置请求头
            conn.setRequestProperty("X-LC-Id", LC_APP_ID);
            conn.setRequestProperty("X-LC-Key", LC_APP_KEY);
            conn.setRequestProperty("Content-Type", "application/json");
            
            String sessionToken = prefs.getString("session_token", null);
            if (sessionToken != null) {
                conn.setRequestProperty("X-LC-Session", sessionToken);
            }
            
            // 发送数据
            if (data != null && (method.equals("POST") || method.equals("PUT"))) {
                conn.setDoOutput(true);
                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = data.toString().getBytes("UTF-8");
                    os.write(input, 0, input.length);
                }
            }
            
            // 读取响应
            int responseCode = conn.getResponseCode();
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                    responseCode >= 400 ? conn.getErrorStream() : conn.getInputStream()
                )
            );
            
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            
            if (responseCode >= 200 && responseCode < 300) {
                return new JSONObject(sb.toString());
            } else {
                Log.e(TAG, "请求失败：" + responseCode + " - " + sb.toString());
                return null;
            }
            
        } catch (Exception e) {
            Log.e(TAG, "网络请求失败", e);
            return null;
        }
    }
    
    /**
     * 检查是否已登录
     */
    public boolean isLoggedIn() {
        return prefs.getString("session_token", null) != null;
    }
    
    /**
     * 获取当前用户 ID
     */
    public String getUserId() {
        return prefs.getString("user_id", null);
    }
    
    /**
     * 登出
     */
    public void logout() {
        stopHeartbeat();
        prefs.edit().clear().apply();
        Log.d(TAG, "已登出");
    }
    
    /**
     * 获取 SharedPreferences (包内访问)
     */
    SharedPreferences getPrefs() {
        return prefs;
    }
    
    /**
     * 设备信息
     */
    public static class DeviceInfo {
        public String objectId;
        public String deviceId;
        public String deviceName;
        public String deviceModel;
        public String status;
        public int battery;
        public String lastSeen;
        
        public boolean isOnline() {
            return "online".equals(status);
        }
        
        public String getBatteryText() {
            if (battery < 0) return "";
            return battery + "%";
        }
    }
}
