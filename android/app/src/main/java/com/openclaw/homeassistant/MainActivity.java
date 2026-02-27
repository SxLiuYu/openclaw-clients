package com.openclaw.homeassistant;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Locale;

/**
 * Android 客户端 - 完整版
 * 功能：
 * 1. 语音识别 + TTS 朗读
 * 2. 文字输入
 * 3. 多轮对话上下文
 * 4. 历史记录查看
 * 5. API 密钥配置
 */
public class MainActivity extends AppCompatActivity {
    
    // UI 组件
    private View statusIndicator;
    private TextView tvStatus;
    private TextView tvConversation;
    private EditText etInput;
    private Button btnSend;
    private Button btnVoice;
    private Button btnHistory;
    private Button btnDeviceData;
    private Button btnBattery;
    private ImageButton btnSettings;
    private Switch switchTTS;
    
    // 服务
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private DashScopeService dashScopeService;
    private ConversationManager conversationManager;
    private DeviceDataReader deviceDataReader;
    
    // 状态
    private boolean isListening = false;
    private boolean isTTSReady = false;
    private boolean isTTSEnabled = true;
    private StringBuilder conversationDisplay = new StringBuilder();
    
    // 权限请求器
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
        setupListeners();
        loadSettings();
    }
    
    private void initViews() {
        statusIndicator = findViewById(R.id.statusIndicator);
        tvStatus = findViewById(R.id.tvStatus);
        tvConversation = findViewById(R.id.tvConversation);
        etInput = findViewById(R.id.etInput);
        btnSend = findViewById(R.id.btnSend);
        btnVoice = findViewById(R.id.btnVoice);
        btnHistory = findViewById(R.id.btnHistory);
        btnDeviceData = findViewById(R.id.btnDeviceData);
        btnBattery = findViewById(R.id.btnBattery);
        btnSettings = findViewById(R.id.btnSettings);
        switchTTS = findViewById(R.id.switchTTS);
        
        updateStatus(false, "未连接");
    }
    
    private void initServices() {
        // 对话管理器
        conversationManager = new ConversationManager(this);
        
        // 设备数据读取器
        deviceDataReader = new DeviceDataReader(this);
        
        // DashScope AI 服务
        dashScopeService = new DashScopeService(this);
        
        // TTS 语音合成
        initTTS();
        
        // 语音识别
        setupSpeechRecognizer();
    }
    
    private void initTTS() {
        try {
            textToSpeech = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.CHINESE);
                    if (result == TextToSpeech.LANG_MISSING_DATA || 
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        runOnUiThread(() -> {
                            Toast.makeText(this, "TTS 不支持中文，请安装中文语音包", Toast.LENGTH_LONG).show();
                            switchTTS.setChecked(false);
                        });
                    } else {
                        isTTSReady = true;
                        runOnUiThread(() -> {
                            Toast.makeText(this, "TTS 初始化成功", Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "TTS 初始化失败", Toast.LENGTH_SHORT).show();
                        switchTTS.setChecked(false);
                    });
                }
            });
        } catch (Exception e) {
            Toast.makeText(this, "TTS 初始化异常：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            switchTTS.setChecked(false);
        }
    }
    
    private void setupSpeechRecognizer() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            btnVoice.setEnabled(false);
            btnVoice.setText("⛔ 不支持语音");
            return;
        }
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                runOnUiThread(() -> {
                    tvStatus.setText("🎤 准备就绪，请说话...");
                    statusIndicator.setBackgroundResource(R.drawable.status_indicator_listening);
                });
            }
            
            @Override
            public void onBeginningOfSpeech() {
                runOnUiThread(() -> tvStatus.setText("👂 正在听..."));
            }
            
            @Override
            public void onEndOfSpeech() {
                runOnUiThread(() -> tvStatus.setText("⏳ 处理中..."));
            }
            
            @Override
            public void onError(int error) {
                runOnUiThread(() -> {
                    tvStatus.setText("错误：" + getErrorText(error));
                    isListening = false;
                    btnVoice.setText("🎤 语音输入");
                    statusIndicator.setBackgroundResource(R.drawable.status_indicator);
                    
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        showPermissionDeniedDialog();
                    }
                });
            }
            
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    appendConversation("👤 你：" + recognizedText);
                    processWithAI(recognizedText);
                }
                runOnUiThread(() -> {
                    isListening = false;
                    btnVoice.setText("🎤 语音输入");
                    statusIndicator.setBackgroundResource(R.drawable.status_indicator);
                });
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
    
    private void setupListeners() {
        // 发送按钮
        btnSend.setOnClickListener(v -> {
            String text = etInput.getText().toString().trim();
            if (!text.isEmpty()) {
                appendConversation("👤 你：" + text);
                processWithAI(text);
                etInput.setText("");
            }
        });
        
        // 语音按钮
        btnVoice.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else {
                checkAndRequestPermission();
            }
        });
        
        // 历史按钮
        btnHistory.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, HistoryActivity.class);
            startActivity(intent);
        });
        
        // 设备数据按钮
        btnDeviceData.setOnClickListener(v -> {
            if (deviceDataReader.hasUsageStatsPermission()) {
                String deviceData = deviceDataReader.getDeviceSummary();
                appendConversation("📊 设备数据:\n" + deviceData);
            } else {
                new AlertDialog.Builder(this)
                    .setTitle("需要权限")
                    .setMessage("读取应用使用时间需要授权。请在设置中开启\"使用情况访问\"权限。")
                    .setPositiveButton("去设置", (dialog, which) -> {
                        deviceDataReader.openUsageStatsSettings();
                    })
                    .setNegativeButton("取消", null)
                    .show();
            }
        });
        
        // 电池按钮
        btnBattery.setOnClickListener(v -> {
            String battery = deviceDataReader.getBatteryStatus();
            String screenTime = deviceDataReader.getScreenTime();
            appendConversation("🔋 " + battery + "\n📱 " + screenTime);
        });
        
        // 设置按钮
        btnSettings.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        });
        
        // TTS 开关
        switchTTS.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isTTSEnabled = isChecked;
            SharedPreferences prefs = getSharedPreferences("OpenClawPrefs", MODE_PRIVATE);
            prefs.edit().putBoolean("tts_enabled", isChecked).apply();
        });
        
        // 输入框回车发送
        etInput.setOnEditorActionListener((v, actionId, event) -> {
            btnSend.performClick();
            return true;
        });
    }
    
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("OpenClawPrefs", MODE_PRIVATE);
        isTTSEnabled = prefs.getBoolean("tts_enabled", true);
        switchTTS.setChecked(isTTSEnabled);
        
        // 检查 API 密钥
        String apiKey = prefs.getString("dashscope_api_key", "");
        if (apiKey.isEmpty()) {
            new AlertDialog.Builder(this)
                .setTitle("⚠️ 需要配置 API 密钥")
                .setMessage("请先在设置中配置 DashScope API 密钥才能使用 AI 对话功能。")
                .setPositiveButton("去设置", (dialog, which) -> {
                    Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
                    startActivity(intent);
                })
                .setNegativeButton("稍后", null)
                .show();
        } else {
            updateStatus(true, "已连接");
        }
    }
    
    private void checkAndRequestPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            startListening();
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO);
        }
    }
    
    private void showPermissionDeniedDialog() {
        new AlertDialog.Builder(this)
            .setTitle("需要麦克风权限")
            .setMessage("语音识别需要麦克风权限。请在设置中手动开启。")
            .setPositiveButton("去设置", (dialog, which) -> {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
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
            btnVoice.setText("⏹️ 停止录音");
        } catch (Exception e) {
            Toast.makeText(this, "启动失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
            isListening = false;
            btnVoice.setText("🎤 语音输入");
        }
    }
    
    private void stopListening() {
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            isListening = false;
            btnVoice.setText("🎤 语音输入");
            tvStatus.setText("已停止");
            statusIndicator.setBackgroundResource(R.drawable.status_indicator);
        }
    }
    
    private void processWithAI(String text) {
        tvStatus.setText("🤖 AI 思考中...");
        
        // 如果是查询设备数据
        if (text.contains("步数") || text.contains("走了多少步")) {
            int steps = deviceDataReader.getStepCount();
            appendConversation("📊 今日步数：" + steps + " 步");
            return;
        }
        
        if (text.contains("电量") || text.contains("电池")) {
            String battery = deviceDataReader.getBatteryStatus();
            appendConversation("🔋 " + battery);
            return;
        }
        
        if (text.contains("屏幕时间") || text.contains("用了多久")) {
            String screenTime = deviceDataReader.getScreenTime();
            appendConversation("📱 " + screenTime);
            return;
        }
        
        if (text.contains("常用应用") || text.contains("应用使用")) {
            if (deviceDataReader.hasUsageStatsPermission()) {
                String apps = deviceDataReader.getFormattedAppUsage();
                appendConversation("📊 常用应用:\n" + apps);
            } else {
                appendConversation("需要先授权应用使用统计权限");
            }
            return;
        }
        
        // 保存到对话上下文
        conversationManager.addToContext("user", text);
        
        // 获取带上下文的请求
        java.util.List<ConversationManager.Message> context = 
            conversationManager.getContextForAPI(10);
        
        // 构建消息列表
        JSONArray messages = new JSONArray();
        for (ConversationManager.Message msg : context) {
            try {
                JSONObject msgObj = new JSONObject();
                msgObj.put("role", msg.role);
                msgObj.put("content", msg.content);
                messages.put(msgObj);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        
        // 调用 API
        dashScopeService.processQueryWithMessages(messages, response -> {
            runOnUiThread(() -> {
                appendConversation("🤖 AI：" + response);
                tvStatus.setText("✅ 完成");
                
                // 保存到上下文
                conversationManager.addToContext("assistant", response);
                
                // TTS 朗读
                if (isTTSEnabled && isTTSReady) {
                    speakOut(response);
                }
            });
        }, error -> {
            runOnUiThread(() -> {
                appendConversation("❌ 错误：" + error);
                tvStatus.setText("❌ 失败");
            });
        });
    }
    
    private void appendConversation(String text) {
        conversationDisplay.append(text).append("\n\n");
        tvConversation.setText(conversationDisplay.toString());
        
        // 滚动到底部
        final ScrollView scrollView = (ScrollView) ((View) tvConversation.getParent()).getParent();
        scrollView.post(() -> scrollView.fullScroll(View.FOCUS_DOWN));
    }
    
    private void speakOut(String text) {
        if (!isTTSEnabled || !isTTSReady) {
            return;
        }
        
        textToSpeech.stop();
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }
    
    private void updateStatus(boolean connected, String text) {
        tvStatus.setText(text);
        if (connected) {
            statusIndicator.setBackgroundResource(R.drawable.status_indicator_connected);
        } else {
            statusIndicator.setBackgroundResource(R.drawable.status_indicator);
        }
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
            case 9: return "权限不足";
            case 7: return "网络错误";
            case 8: return "网络超时";
            case 1: return "无法识别";
            case 4: return "识别器忙碌";
            case 3: return "服务器错误";
            case 2: return "语音超时";
            default: return "未知错误";
        }
    }
}
