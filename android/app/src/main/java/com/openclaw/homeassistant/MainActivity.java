package com.openclaw.homeassistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.SpeechRecognizer;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Android 客户端 - 修复版
 * 修复问题:
 * 1. 权限请求处理不当
 * 2. 添加 TTS 语音合成
 * 3. 添加多轮对话支持
 * 4. 更好的错误提示
 */
public class MainActivity extends AppCompatActivity {
    
    private static final int RECORD_AUDIO_PERMISSION_CODE = 1;
    
    // UI 组件
    private TextView tvStatus;
    private TextView tvRecognizedText;
    private TextView tvAiResponse;
    private Button btnStartStop;
    
    // 服务
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private DashScopeService dashScopeService;
    private ConversationManager conversationManager;
    
    // 状态
    private boolean isListening = false;
    private boolean isTTSReady = false;
    
    // 权限请求器（新 API）
    private final ActivityResultLauncher<String> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                Toast.makeText(this, "✅ 权限已授予", Toast.LENGTH_SHORT).show();
                startListening();
            } else {
                showPermissionDeniedDialog();
            }
        });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        initServices();
        checkAndRequestPermission();
    }
    
    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvRecognizedText = findViewById(R.id.tvRecognizedText);
        tvAiResponse = findViewById(R.id.tvAiResponse);
        btnStartStop = findViewById(R.id.btnStartStop);
        
        btnStartStop.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                checkAndRequestPermission();
            }
        });
        
        tvStatus.setText("点击按钮开始语音识别");
        tvAiResponse.setText("AI 回复将显示在这里");
    }
    
    private void initServices() {
        // 对话管理器（多轮对话）
        conversationManager = new ConversationManager(this);
        
        // DashScope AI 服务
        dashScopeService = new DashScopeService(this);
        
        // TTS 语音合成
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = textToSpeech.setLanguage(Locale.CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || 
                    result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Toast.makeText(this, "TTS 不支持中文", Toast.LENGTH_SHORT).show();
                } else {
                    isTTSReady = true;
                }
            } else {
                Toast.makeText(this, "TTS 初始化失败", Toast.LENGTH_SHORT).show();
            }
        });
        
        // 语音识别
        setupSpeechRecognizer();
    }
    
    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "语音识别不可用", Toast.LENGTH_SHORT).show();
            btnStartStop.setEnabled(false);
            return;
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                tvStatus.setText("🎤 准备就绪，请说话...");
            }
            
            @Override
            public void onBeginningOfSpeech() {
                tvStatus.setText("👂 正在听...");
            }
            
            @Override
            public void onEndOfSpeech() {
                tvStatus.setText("⏳ 处理中...");
            }
            
            @Override
            public void onError(int error) {
                tvStatus.setText("错误：" + getErrorText(error));
                isListening = false;
                btnStartStop.setText("🎤 开始识别");
                
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    showPermissionDeniedDialog();
                }
            }
            
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    tvRecognizedText.setText("识别：" + recognizedText);
                    processWithAI(recognizedText);
                }
                isListening = false;
                btnStartStop.setText("🎤 开始识别");
            }
            
            @Override
            public void onPartialResults(Bundle partialResults) {}
            @Override
            public void onEvent(int eventType, Bundle params) {}
            @Override
            public void onRmsChanged(float rmsdB) {}
            @Override
            public void onBufferReceived(byte[] buffer) {}
        });
    }
    
    private void checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            // 权限已有，直接开始
            startListening();
        } else {
            // 请求权限
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }
    
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
            .setTitle("需要麦克风权限")
            .setMessage("语音识别需要麦克风权限。请在设置中手动开启。")
            .setPositiveButton("去设置", (dialog, which) -> {
                Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", getPackageName(), null));
                startActivity(intent);
            })
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void startListening() {
        if (speechRecognizer == null) {
            Toast.makeText(this, "语音识别未初始化", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            intent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
            
            speechRecognizer.startListening(intent);
            isListening = true;
            btnStartStop.setText("⏹️ 停止识别");
            tvAiResponse.setText("请说话...");
            
        } catch (Exception e) {
            Toast.makeText(this, "启动失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            isListening = false;
            btnStartStop.setText("🎤 开始识别");
        }
    }
    
    private void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            isListening = false;
            btnStartStop.setText("🎤 开始识别");
            tvStatus.setText("已停止");
        }
    }
    
    private void processWithAI(String text) {
        tvAiResponse.setText("🤖 AI 思考中...");
        
        // 保存到对话上下文
        conversationManager.addToContext("user", text);
        
        // 获取带上下文的请求
        java.util.List<ConversationManager.Message> context = 
            conversationManager.getContextForAPI(10);
        
        // 构建消息列表
        org.json.JSONArray messages = new org.json.JSONArray();
        for (ConversationManager.Message msg : context) {
            try {
                org.json.JSONObject msgObj = new org.json.JSONObject();
                msgObj.put("role", msg.role);
                msgObj.put("content", msg.content);
                messages.put(msgObj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // 添加当前消息
        try {
            org.json.JSONObject currentMsg = new org.json.JSONObject();
            currentMsg.put("role", "user");
            currentMsg.put("content", text);
            messages.put(currentMsg);
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        // 调用 API
        dashScopeService.processQueryWithMessages(messages, response -> {
            runOnUiThread(() -> {
                tvAiResponse.setText("AI: " + response);
                
                // 保存到上下文
                conversationManager.addToContext("assistant", response);
                
                // TTS 朗读
                speakOut(response);
            });
        }, error -> {
            runOnUiThread(() -> {
                tvAiResponse.setText("❌ " + error);
            });
        });
    }
    
    private void speakOut(String text) {
        if (!isTTSReady) {
            return;
        }
        
        // 停止当前朗读
        textToSpeech.stop();
        
        // 朗读新内容
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }
    
    @Override
    protected void onDestroy() {
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
        super.onDestroy();
    }
    
    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case 5: return "录音错误";
            case 6: return "客户端错误";
            case 9: return "权限不足，请在设置中开启麦克风权限";
            case 7: return "网络错误";
            case 8: return "网络超时";
            case 1: return "无法识别";
            case 4: return "识别器忙碌";
            case 3: return "服务器错误";
            case 2: return "语音超时";
            default:
                return "未知错误";
        }
    }
}
