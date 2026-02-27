package com.openclaw.homeassistant;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * 设置界面
 */
public class SettingsActivity extends AppCompatActivity {
    
    private EditText etApiKey;
    private Spinner spinnerContextLength;
    private Button btnSave;
    private Button btnCancel;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        
        initViews();
        loadSettings();
        setupListeners();
    }
    
    private void initViews() {
        etApiKey = findViewById(R.id.etApiKey);
        spinnerContextLength = findViewById(R.id.spinnerContextLength);
        btnSave = findViewById(R.id.btnSave);
        btnCancel = findViewById(R.id.btnCancel);
        
        // 设置上下文长度选项
        String[] options = {"最近 5 条", "最近 10 条", "最近 20 条"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, 
            android.R.layout.simple_spinner_item, options);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerContextLength.setAdapter(adapter);
    }
    
    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("OpenClawPrefs", MODE_PRIVATE);
        String apiKey = prefs.getString("dashscope_api_key", "");
        int contextLength = prefs.getInt("context_length", 10);
        
        etApiKey.setText(apiKey);
        spinnerContextLength.setSelection(contextLength == 5 ? 0 : contextLength == 20 ? 2 : 1);
    }
    
    private void setupListeners() {
        btnSave.setOnClickListener(v -> saveSettings());
        btnCancel.setOnClickListener(v -> finish());
    }
    
    private void saveSettings() {
        String apiKey = etApiKey.getText().toString().trim();
        int contextLength = spinnerContextLength.getSelectedItemPosition() == 0 ? 5 : 
                           spinnerContextLength.getSelectedItemPosition() == 2 ? 20 : 10;
        
        SharedPreferences prefs = getSharedPreferences("OpenClawPrefs", MODE_PRIVATE);
        prefs.edit()
            .putString("dashscope_api_key", apiKey)
            .putInt("context_length", contextLength)
            .apply();
        
        Toast.makeText(this, "✅ 设置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
}
