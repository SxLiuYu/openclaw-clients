package com.openclaw.homeassistant;

import android.content.Context;
import android.content.SharedPreferences;
import okhttp3.*;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class DashScopeService {
    private static final String BASE_URL = "https://dashscope.aliyuncs.com/api/v1";
    private static final String API_KEY_PREFERENCE = "dashscope_api_key";
    
    private final OkHttpClient client;
    private final SharedPreferences preferences;
    
    public interface DashScopeCallback {
        void onSuccess(String response);
        void onError(String error);
    }
    
    public interface SuccessCallback {
        void onSuccess(String response);
    }
    
    public interface ErrorCallback {
        void onError(String error);
    }
    
    public DashScopeService(Context context) {
        this.preferences = context.getSharedPreferences("OpenClawPrefs", Context.MODE_PRIVATE);
        this.client = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
    
    public void processQuery(String query, DashScopeCallback callback) {
        String apiKey = preferences.getString(API_KEY_PREFERENCE, "");
        if (apiKey.isEmpty()) {
            callback.onError("API密钥未配置");
            return;
        }
        
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "qwen-max");
            
            JSONObject input = new JSONObject();
            JSONArray messages = new JSONArray();
            
            JSONObject systemMessage = new JSONObject();
            systemMessage.put("role", "system");
            systemMessage.put("content", "你是一个家庭助手，请理解用户的语音指令并提供相应的帮助。");
            messages.put(systemMessage);
            
            JSONObject userMessage = new JSONObject();
            userMessage.put("role", "user");
            userMessage.put("content", query);
            messages.put(userMessage);
            
            input.put("messages", messages);
            requestBody.put("input", input);
            
            JSONObject parameters = new JSONObject();
            parameters.put("temperature", 0.7);
            parameters.put("top_p", 0.8);
            parameters.put("max_tokens", 500);
            requestBody.put("parameters", parameters);
            
            Request request = new Request.Builder()
                .url(BASE_URL + "/services/aigc/text-generation/generation")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                    requestBody.toString(), 
                    MediaType.parse("application/json")
                ))
                .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    callback.onError("网络请求失败: " + e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String aiResponse = jsonResponse
                                .getJSONObject("output")
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                            callback.onSuccess(aiResponse);
                        } else {
                            callback.onError("API错误: " + response.code());
                        }
                    } catch (Exception e) {
                        callback.onError("解析响应失败: " + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            callback.onError("请求构建失败: " + e.getMessage());
        }
    }
    
    /**
     * 处理查询（带自定义消息列表 - 用于多轮对话）
     */
    public void processQueryWithMessages(JSONArray messages, 
                                         SuccessCallback successCallback, 
                                         ErrorCallback errorCallback) {
        String apiKey = preferences.getString(API_KEY_PREFERENCE, "");
        if (apiKey.isEmpty()) {
            errorCallback.onError("API 密钥未配置");
            return;
        }
        
        try {
            JSONObject requestBody = new JSONObject();
            requestBody.put("model", "qwen-max");
            
            JSONObject input = new JSONObject();
            input.put("messages", messages);
            requestBody.put("input", input);
            
            JSONObject parameters = new JSONObject();
            parameters.put("temperature", 0.7);
            parameters.put("top_p", 0.8);
            parameters.put("max_tokens", 500);
            requestBody.put("parameters", parameters);
            
            Request request = new Request.Builder()
                .url(BASE_URL + "/services/aigc/text-generation/generation")
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(
                    requestBody.toString(), 
                    MediaType.parse("application/json")
                ))
                .build();
            
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    errorCallback.onError("网络请求失败：" + e.getMessage());
                }
                
                @Override
                public void onResponse(Call call, Response response) throws IOException {
                    try {
                        String responseBody = response.body().string();
                        if (response.isSuccessful()) {
                            JSONObject jsonResponse = new JSONObject(responseBody);
                            String aiResponse = jsonResponse
                                .getJSONObject("output")
                                .getJSONArray("choices")
                                .getJSONObject(0)
                                .getJSONObject("message")
                                .getString("content");
                            successCallback.onSuccess(aiResponse);
                        } else {
                            errorCallback.onError("API 错误：" + response.code());
                        }
                    } catch (Exception e) {
                        errorCallback.onError("解析响应失败：" + e.getMessage());
                    }
                }
            });
        } catch (Exception e) {
            errorCallback.onError("请求构建失败：" + e.getMessage());
        }
    }
    
    public void saveApiKey(String apiKey) {
        preferences.edit().putString(API_KEY_PREFERENCE, apiKey).apply();
    }
}
