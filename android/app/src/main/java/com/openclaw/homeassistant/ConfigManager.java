package com.openclaw.homeassistant;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 统一配置管理器
 * 功能：
 * 1. 读取/写入 JSON 配置
 * 2. 导入/导出配置文件
 * 3. 二维码生成/识别
 * 4. 配置验证
 */
public class ConfigManager {
    
    private static final String TAG = "ConfigManager";
    private static final String CONFIG_FILE_NAME = "openclaw-config.json";
    private static final String PREFS_NAME = "openclaw_config";
    private static final String PREF_CONFIG_JSON = "config_json";
    
    private final Context context;
    private JSONObject config;
    private OnConfigChangeListener listener;
    
    public interface OnConfigChangeListener {
        void onConfigChanged(JSONObject newConfig);
    }
    
    public ConfigManager(Context context) {
        this.context = context.getApplicationContext();
        loadConfig();
    }
    
    public void setOnConfigChangeListener(OnConfigChangeListener listener) {
        this.listener = listener;
    }
    
    // ============== 加载/保存 ==============
    
    /**
     * 从本地存储加载配置
     */
    public boolean loadConfig() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String configJson = prefs.getString(PREF_CONFIG_JSON, null);
            
            if (configJson != null) {
                config = new JSONObject(configJson);
                Log.d(TAG, "配置加载成功");
                return true;
            }
            
            // 尝试从文件加载
            File configFile = new File(context.getFilesDir(), CONFIG_FILE_NAME);
            if (configFile.exists()) {
                String fileContent = readFile(configFile);
                config = new JSONObject(fileContent);
                saveConfig(); // 保存到 SharedPreferences
                Log.d(TAG, "配置从文件加载成功");
                return true;
            }
            
            // 创建默认配置
            config = createDefaultConfig();
            Log.d(TAG, "创建默认配置");
            return false;
            
        } catch (Exception e) {
            Log.e(TAG, "加载配置失败", e);
            config = createDefaultConfig();
            return false;
        }
    }
    
    /**
     * 保存配置到本地存储
     */
    public boolean saveConfig() {
        try {
            if (config == null) return false;
            
            // 保存到 SharedPreferences
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(PREF_CONFIG_JSON, config.toString(2)).apply();
            
            // 同时保存到文件（用于导出）
            File configFile = new File(context.getFilesDir(), CONFIG_FILE_NAME);
            writeFile(configFile, config.toString(2));
            
            Log.d(TAG, "配置保存成功");
            
            if (listener != null) {
                listener.onConfigChanged(config);
            }
            
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "保存配置失败", e);
            return false;
        }
    }
    
    // ============== 导入/导出 ==============
    
    /**
     * 从 JSON 字符串导入配置
     */
    public boolean importFromJson(String jsonStr) {
        try {
            JSONObject newConfig = new JSONObject(jsonStr);
            
            // 验证配置
            if (!validateConfig(newConfig)) {
                Log.e(TAG, "配置验证失败");
                return false;
            }
            
            config = newConfig;
            saveConfig();
            Log.d(TAG, "配置导入成功");
            return true;
            
        } catch (Exception e) {
            Log.e(TAG, "导入配置失败", e);
            return false;
        }
    }
    
    /**
     * 导出配置为 JSON 字符串
     */
    @Nullable
    public String exportToJson() {
        if (config == null) return null;
        try {
            return config.toString(2);
        } catch (JSONException e) {
            Log.e(TAG, "导出 JSON 失败", e);
            return null;
        }
    }
    
    /**
     * 导出配置为文件
     */
    @Nullable
    public File exportToFile() {
        try {
            if (config == null) return null;
            
            File exportFile = new File(context.getExternalFilesDir(null), CONFIG_FILE_NAME);
            writeFile(exportFile, config.toString(2));
            Log.d(TAG, "配置导出到文件：" + exportFile.getAbsolutePath());
            return exportFile;
            
        } catch (Exception e) {
            Log.e(TAG, "导出文件失败", e);
            return null;
        }
    }
    
    /**
     * 从文件导入配置
     */
    public boolean importFromFile(File file) {
        try {
            if (!file.exists()) {
                Log.e(TAG, "文件不存在：" + file.getAbsolutePath());
                return false;
            }
            
            String content = readFile(file);
            return importFromJson(content);
            
        } catch (Exception e) {
            Log.e(TAG, "从文件导入失败", e);
            return false;
        }
    }
    
    // ============== 二维码功能 ==============
    
    /**
     * 生成配置二维码
     * @param size 二维码尺寸 (像素)
     * @return Bitmap 二维码图片
     */
    @Nullable
    public Bitmap generateQRCode(int size) {
        try {
            String configJson = exportToJson();
            if (configJson == null) return null;
            
            // 压缩 JSON（移除空格）
            String compressed = config.toString();
            
            // 检查是否超出二维码容量限制（约 3KB）
            if (compressed.length() > 2500) {
                Log.w(TAG, "配置数据过大，可能无法生成二维码");
            }
            
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 1);
            
            BitMatrix bitMatrix = writer.encode(compressed, BarcodeFormat.QR_CODE, size, size, hints);
            
            Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565);
            for (int x = 0; x < size; x++) {
                for (int y = 0; y < size; y++) {
                    bitmap.setPixel(x, y, bitMatrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            
            Log.d(TAG, "二维码生成成功，尺寸：" + size + "x" + size);
            return bitmap;
            
        } catch (WriterException e) {
            Log.e(TAG, "生成二维码失败", e);
            return null;
        }
    }
    
    /**
     * 从 Base64 编码的二维码数据导入配置
     */
    public boolean importFromQRData(String qrData) {
        try {
            // 解码 Base64
            byte[] decoded = Base64.decode(qrData, Base64.DEFAULT);
            String jsonStr = new String(decoded, StandardCharsets.UTF_8);
            return importFromJson(jsonStr);
            
        } catch (Exception e) {
            Log.e(TAG, "从二维码导入失败", e);
            return false;
        }
    }
    
    // ============== 配置访问 ==============
    
    /**
     * 获取 API Key
     */
    @Nullable
    public String getApiKey() {
        try {
            return config.getJSONObject("core").getString("api_key");
        } catch (JSONException e) {
            return null;
        }
    }
    
    /**
     * 设置 API Key
     */
    public void setApiKey(String apiKey) {
        try {
            config.getJSONObject("core").put("api_key", apiKey);
            saveConfig();
        } catch (JSONException e) {
            Log.e(TAG, "设置 API Key 失败", e);
        }
    }
    
    /**
     * 获取上下文长度
     */
    public int getContextLength() {
        try {
            return config.getJSONObject("core").optInt("context_length", 20);
        } catch (JSONException e) {
            return 20;
        }
    }
    
    /**
     * 设置上下文长度
     */
    public void setContextLength(int length) {
        try {
            config.getJSONObject("core").put("context_length", length);
            saveConfig();
        } catch (JSONException e) {
            Log.e(TAG, "设置上下文长度失败", e);
        }
    }
    
    /**
     * TTS 是否启用
     */
    public boolean isTTSEnabled() {
        try {
            return config.optJSONObject("tts")
                    .optBoolean("enabled", true);
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 设置 TTS 启用状态
     */
    public void setTTSEnabled(boolean enabled) {
        try {
            if (!config.has("tts")) {
                config.put("tts", new JSONObject());
            }
            config.getJSONObject("tts").put("enabled", enabled);
            saveConfig();
        } catch (JSONException e) {
            Log.e(TAG, "设置 TTS 状态失败", e);
        }
    }
    
    /**
     * 获取自动化规则
     */
    @Nullable
    public JSONArray getAutomationRules() {
        try {
            JSONObject automation = config.optJSONObject("automation");
            if (automation == null || !automation.optBoolean("enabled", true)) {
                return null;
            }
            return automation.optJSONArray("rules");
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * 自动化是否启用
     */
    public boolean isAutomationEnabled() {
        try {
            return config.optJSONObject("automation")
                    .optBoolean("enabled", true);
        } catch (Exception e) {
            return true;
        }
    }
    
    /**
     * 设置自动化启用状态
     */
    public void setAutomationEnabled(boolean enabled) {
        try {
            if (!config.has("automation")) {
                config.put("automation", new JSONObject());
            }
            config.getJSONObject("automation").put("enabled", enabled);
            saveConfig();
        } catch (JSONException e) {
            Log.e(TAG, "设置自动化状态失败", e);
        }
    }
    
    /**
     * 获取完整配置对象
     */
    @Nullable
    public JSONObject getConfig() {
        return config;
    }
    
    // ============== 私有方法 ==============
    
    @NonNull
    private JSONObject createDefaultConfig() {
        try {
            JSONObject defaultConfig = new JSONObject();
            defaultConfig.put("version", "1.0");
            defaultConfig.put("profile_name", "默认配置");
            
            JSONObject core = new JSONObject();
            core.put("api_key", "");
            core.put("api_provider", "dashscope");
            core.put("model", "qwen-max");
            core.put("context_length", 20);
            defaultConfig.put("core", core);
            
            JSONObject tts = new JSONObject();
            tts.put("enabled", true);
            tts.put("speed", 1.0);
            tts.put("volume", 0.8);
            defaultConfig.put("tts", tts);
            
            // 默认自动化规则
            JSONObject automation = new JSONObject();
            automation.put("enabled", true);
            automation.put("rules", createDefaultAutomationRules());
            defaultConfig.put("automation", automation);
            
            JSONObject ui = new JSONObject();
            ui.put("theme", "auto");
            ui.put("language", "zh-CN");
            ui.put("font_size", "medium");
            defaultConfig.put("ui", ui);
            
            return defaultConfig;
            
        } catch (JSONException e) {
            Log.e(TAG, "创建默认配置失败", e);
            return new JSONObject();
        }
    }
    
    @NonNull
    private JSONArray createDefaultAutomationRules() {
        try {
            JSONArray rules = new JSONArray();
            
            // 规则 1: 早晨提醒 (7:00)
            JSONObject morningRule = new JSONObject();
            morningRule.put("id", "morning_routine");
            morningRule.put("name", "☀️ 早晨提醒");
            morningRule.put("enabled", true);
            JSONArray morningTriggers = new JSONArray();
            JSONObject timeTrigger = new JSONObject();
            timeTrigger.put("type", "time");
            timeTrigger.put("time", "07:00");
            morningTriggers.put(timeTrigger);
            morningRule.put("triggers", morningTriggers);
            JSONArray morningActions = new JSONArray();
            JSONObject speakAction = new JSONObject();
            speakAction.put("type", "speak");
            speakAction.put("template", "weather_commute");
            morningActions.put(speakAction);
            morningRule.put("actions", morningActions);
            rules.put(morningRule);
            
            // 规则 2: 低电量提醒 (<20%)
            JSONObject batteryRule = new JSONObject();
            batteryRule.put("id", "low_battery");
            batteryRule.put("name", "🔋 低电量提醒");
            batteryRule.put("enabled", true);
            JSONArray batteryTriggers = new JSONArray();
            JSONObject batteryTrigger = new JSONObject();
            batteryTrigger.put("type", "battery");
            batteryTrigger.put("level_below", 20);
            batteryTriggers.put(batteryTrigger);
            batteryRule.put("triggers", batteryTriggers);
            JSONArray batteryActions = new JSONArray();
            JSONObject notifyAction = new JSONObject();
            notifyAction.put("type", "notify");
            notifyAction.put("title", "电量低");
            notifyAction.put("message", "电量低于 20%，建议充电");
            batteryActions.put(notifyAction);
            batteryRule.put("actions", batteryActions);
            rules.put(batteryRule);
            
            // 规则 3: 睡前提醒 (23:00 + 充电中)
            JSONObject bedtimeRule = new JSONObject();
            bedtimeRule.put("id", "bedtime");
            bedtimeRule.put("name", "🌙 睡前提醒");
            bedtimeRule.put("enabled", true);
            JSONArray bedtimeTriggers = new JSONArray();
            JSONObject bedtimeTimeTrigger = new JSONObject();
            bedtimeTimeTrigger.put("type", "time");
            bedtimeTimeTrigger.put("time", "23:00");
            bedtimeTriggers.put(bedtimeTimeTrigger);
            bedtimeRule.put("triggers", bedtimeTriggers);
            JSONArray bedtimeActions = new JSONArray();
            JSONObject bedtimeSpeak = new JSONObject();
            bedtimeSpeak.put("type", "speak");
            bedtimeSpeak.put("template", "tomorrow_weather");
            bedtimeActions.put(bedtimeSpeak);
            bedtimeRule.put("actions", bedtimeActions);
            rules.put(bedtimeRule);
            
            return rules;
            
        } catch (JSONException e) {
            Log.e(TAG, "创建默认规则失败", e);
            return new JSONArray();
        }
    }
    
    private boolean validateConfig(JSONObject config) {
        try {
            // 必须包含 version 和 core
            if (!config.has("version") || !config.has("core")) {
                return false;
            }
            
            // core 必须包含 api_key
            JSONObject core = config.getJSONObject("core");
            if (!core.has("api_key")) {
                return false;
            }
            
            return true;
            
        } catch (JSONException e) {
            return false;
        }
    }
    
    private String readFile(File file) throws IOException {
        StringBuilder sb = new StringBuilder();
        java.io.BufferedReader reader = new java.io.BufferedReader(
            new java.io.FileReader(file));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }
    
    private void writeFile(File file, String content) throws IOException {
        FileWriter writer = new FileWriter(file);
        writer.write(content);
        writer.close();
    }
}
