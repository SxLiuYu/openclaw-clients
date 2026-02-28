package com.openclaw.homeassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * 天气预警服务
 * 功能：暴雨/雾霾/高温等预警自动提醒
 */
public class WeatherAlertService {
    
    private static final String TAG = "WeatherAlertService";
    private static final String PREFS_NAME = "weather_alerts";
    
    private final Context context;
    private final SharedPreferences prefs;
    
    // 预警阈值
    private boolean rainAlertEnabled = true;
    private boolean smogAlertEnabled = true;
    private boolean heatAlertEnabled = true;
    private boolean coldAlertEnabled = true;
    
    private int rainThreshold = 50;      // 降雨量 mm
    private int heatThreshold = 35;      // 高温 ℃
    private int coldThreshold = 0;       // 低温 ℃
    private int aqiThreshold = 150;      // AQI
    
    public interface WeatherAlertListener {
        void onWeatherAlert(String alertType, String message);
    }
    
    private static WeatherAlertListener listener;
    
    public WeatherAlertService(Context context) {
        this.context = context.getApplicationContext();
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        loadSettings();
    }
    
    public static void setListener(WeatherAlertListener listener) {
        WeatherAlertService.listener = listener;
    }
    
    /**
     * 加载设置
     */
    private void loadSettings() {
        rainAlertEnabled = prefs.getBoolean("rain_enabled", true);
        smogAlertEnabled = prefs.getBoolean("smog_enabled", true);
        heatAlertEnabled = prefs.getBoolean("heat_enabled", true);
        coldAlertEnabled = prefs.getBoolean("cold_enabled", true);
        
        rainThreshold = prefs.getInt("rain_threshold", 50);
        heatThreshold = prefs.getInt("heat_threshold", 35);
        coldThreshold = prefs.getInt("cold_threshold", 0);
        aqiThreshold = prefs.getInt("aqi_threshold", 150);
    }
    
    /**
     * 检查天气预警
     */
    public void checkWeatherAlerts() {
        try {
            // 获取天气数据
            JSONObject weather = fetchWeather();
            if (weather == null) return;
            
            // 检查降雨
            if (rainAlertEnabled) {
                String weatherDesc = weather.optString("weather", "");
                if (weatherDesc.contains("雨") || weatherDesc.contains("雪")) {
                    triggerAlert("rain", "⚠️ 降雨预警：今天有" + weatherDesc + "，请携带雨具。");
                }
            }
            
            // 检查高温
            if (heatAlertEnabled) {
                int temp = weather.optInt("temp", 25);
                if (temp >= heatThreshold) {
                    triggerAlert("heat", "🌡️ 高温预警：今天温度" + temp + "℃，注意防暑降温。");
                }
            }
            
            // 检查低温
            if (coldAlertEnabled) {
                int temp = weather.optInt("temp", 25);
                if (temp <= coldThreshold) {
                    triggerAlert("cold", "❄️ 低温预警：今天温度" + temp + "℃，注意保暖。");
                }
            }
            
            // 检查雾霾 (简化：使用 AQI)
            if (smogAlertEnabled) {
                int aqi = fetchAQI();
                if (aqi >= aqiThreshold) {
                    triggerAlert("smog", "😷 雾霾预警：AQI " + aqi + "，减少户外活动。");
                }
            }
            
        } catch (Exception e) {
            Log.e(TAG, "检查天气预警失败", e);
        }
    }
    
    /**
     * 获取天气数据
     */
    private JSONObject fetchWeather() {
        try {
            URL url = new URL("http://wttr.in/Beijing?format=j1");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            reader.close();
            
            JSONObject json = new JSONObject(sb.toString());
            JSONObject current = json.getJSONArray("current_condition")
                .getJSONObject(0);
            
            JSONObject result = new JSONObject();
            result.put("weather", current.getJSONObject("weatherDesc")
                .getJSONArray("weatherDesc").getJSONObject(0).getString("value"));
            result.put("temp", current.getInt("temp_C"));
            
            return result;
            
        } catch (Exception e) {
            Log.e(TAG, "获取天气失败", e);
            return null;
        }
    }
    
    /**
     * 获取 AQI (模拟数据)
     */
    private int fetchAQI() {
        // 实际应调用空气质量 API
        // 暂时返回随机值用于测试
        return (int) (Math.random() * 200);
    }
    
    /**
     * 触发预警
     */
    private void triggerAlert(String type, String message) {
        Log.d(TAG, "天气预警：" + type + " - " + message);
        
        if (listener != null) {
            listener.onWeatherAlert(type, message);
        }
        
        // 发送通知
        NotificationHelper.sendHealthNotification(context, "⚠️ 天气预警", message);
    }
    
    // ============== Getter/Setter ==============
    
    public boolean isRainAlertEnabled() { return rainAlertEnabled; }
    public void setRainAlertEnabled(boolean enabled) {
        this.rainAlertEnabled = enabled;
        prefs.edit().putBoolean("rain_enabled", enabled).apply();
    }
    
    public boolean isSmogAlertEnabled() { return smogAlertEnabled; }
    public void setSmogAlertEnabled(boolean enabled) {
        this.smogAlertEnabled = enabled;
        prefs.edit().putBoolean("smog_enabled", enabled).apply();
    }
    
    public boolean isHeatAlertEnabled() { return heatAlertEnabled; }
    public void setHeatAlertEnabled(boolean enabled) {
        this.heatAlertEnabled = enabled;
        prefs.edit().putBoolean("heat_enabled", enabled).apply();
    }
    
    public boolean isColdAlertEnabled() { return coldAlertEnabled; }
    public void setColdAlertEnabled(boolean enabled) {
        this.coldAlertEnabled = enabled;
        prefs.edit().putBoolean("cold_enabled", enabled).apply();
    }
    
    public int getRainThreshold() { return rainThreshold; }
    public void setRainThreshold(int mm) {
        this.rainThreshold = mm;
        prefs.edit().putInt("rain_threshold", mm).apply();
    }
    
    public int getHeatThreshold() { return heatThreshold; }
    public void setHeatThreshold(int celsius) {
        this.heatThreshold = celsius;
        prefs.edit().putInt("heat_threshold", celsius).apply();
    }
    
    public int getColdThreshold() { return coldThreshold; }
    public void setColdThreshold(int celsius) {
        this.coldThreshold = celsius;
        prefs.edit().putInt("cold_threshold", celsius).apply();
    }
    
    public int getAqiThreshold() { return aqiThreshold; }
    public void setAqiThreshold(int aqi) {
        this.aqiThreshold = aqi;
        prefs.edit().putInt("aqi_threshold", aqi).apply();
    }
}
