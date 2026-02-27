package com.openclaw.homeassistant;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int RECORD_AUDIO_PERMISSION_CODE = 1;
    
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech textToSpeech;
    private TextView tvStatus;
    private TextView tvRecognizedText;
    private TextView tvAiResponse;
    private Button btnStartStop;
    private boolean isListening = false;
    
    private DashScopeService dashScopeService;
    private ConnectionManager connectionManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        initViews();
        initServices();
        checkPermissions();
    }
    
    private void initViews() {
        tvStatus = findViewById(R.id.tvStatus);
        tvRecognizedText = findViewById(R.id.tvRecognizedText);
        tvAiResponse = findViewById(R.id.tvAiResponse);
        btnStartStop = findViewById(R.id.btnStartStop);
        
        btnStartStop.setOnClickListener(v -> toggleListening());
    }
    
    private void initServices() {
        dashScopeService = new DashScopeService(this);
        connectionManager = new ConnectionManager(this);
        
        textToSpeech = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                textToSpeech.setLanguage(Locale.CHINESE);
            }
        });
        
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                tvStatus.setText("准备就绪，请说话...");
            }
            
            @Override
            public void onBeginningOfSpeech() {
                tvStatus.setText("正在听...");
            }
            
            @Override
            public void onEndOfSpeech() {
                tvStatus.setText("处理中...");
            }
            
            @Override
            public void onError(int error) {
                tvStatus.setText("错误: " + getErrorText(error));
                isListening = false;
                btnStartStop.setText("开始识别");
            }
            
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    tvRecognizedText.setText("识别结果: " + recognizedText);
                    processWithAI(recognizedText);
                }
                isListening = false;
                btnStartStop.setText("开始识别");
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
    
    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.RECORD_AUDIO}, 
                RECORD_AUDIO_PERMISSION_CODE);
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, 
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "权限已授予", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要麦克风权限才能使用语音功能", Toast.LENGTH_SHORT).show();
            }
        }
    }
    
    private void toggleListening() {
        if (isListening) {
            speechRecognizer.stopListening();
            isListening = false;
            btnStartStop.setText("开始识别");
            tvStatus.setText("已停止");
        } else {
            startListening();
        }
    }
    
    private void startListening() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
            == PackageManager.PERMISSION_GRANTED) {
            isListening = true;
            btnStartStop.setText("停止识别");
            
            android.content.Intent intent = new android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
            
            speechRecognizer.startListening(intent);
        } else {
            checkPermissions();
        }
    }
    
    private void processWithAI(String text) {
        tvAiResponse.setText("AI处理中...");
        
        dashScopeService.processQuery(text, new DashScopeService.DashScopeCallback() {
            @Override
            public void onSuccess(String response) {
                runOnUiThread(() -> {
                    tvAiResponse.setText("AI回复: " + response);
                    speakOut(response);
                });
            }
            
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    tvAiResponse.setText("错误: " + error);
                });
            }
        });
    }
    
    private void speakOut(String text) {
        textToSpeech.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
    }
    
    private String getErrorText(int errorCode) {
        switch (errorCode) {
            case 5 /*ERROR_AUDIO_RECORDING*/:
                return "音频录制错误";
            case 6 /*ERROR_CLIENT*/:
                return "客户端错误";
            case 9 /*ERROR_INSUFFICIENT_PERMISSIONS*/:
                return "权限不足";
            case 7 /*ERROR_NETWORK*/:
                return "网络错误";
            case 8: /*NETWORK_TIMEOUT*/
                return "网络超时";
            case 1 /*ERROR_NO_MATCH*/:
                return "无法识别";
            case 4 /*ERROR_RECOGNIZER_BUSY*/:
                return "识别器忙碌";
            case 3 /*ERROR_SERVER*/:
                return "服务器错误";
            case 2 /*ERROR_SPEECH_TIMEOUT*/:
                return "语音超时";
            default:
                return "未知错误";
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
        if (textToSpeech != null) {
            textToSpeech.stop();
            textToSpeech.shutdown();
        }
    }
}