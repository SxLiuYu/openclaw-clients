package com.openclaw.homeassistant;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 配置管理 Activity
 * 功能：
 * 1. API Key 配置
 * 2. TTS 设置
 * 3. 自动化开关
 * 4. 导入/导出配置
 * 5. 二维码生成/扫描
 */
public class ConfigActivity extends AppCompatActivity {
    
    private static final int REQUEST_SCAN_QR = 1001;
    
    // UI 组件
    private EditText etApiKey;
    private Switch switchTTS;
    private Switch switchAutomation;
    private SeekBar seekContextLength;
    private TextView tvContextLength;
    private ImageView ivQRCode;
    private Button btnGenerateQR;
    private Button btnExport;
    private Button btnImport;
    private Button btnScanQR;
    private Button btnSave;
    
    // 管理器
    private ConfigManager configManager;
    private AutomationEngine automationEngine;
    
    // 权限请求器
    private final ActivityResultLauncher<String> permissionLauncher =
        registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
            if (isGranted) {
                exportConfigToFile();
            } else {
                Toast.makeText(this, "需要存储权限以导出配置", Toast.LENGTH_SHORT).show();
            }
        });
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_config);
        
        initManagers();
        initViews();
        loadConfig();
        setupListeners();
    }
    
    private void initManagers() {
        configManager = new ConfigManager(this);
        
        // 初始化自动化引擎（如果启用）
        if (configManager.isAutomationEnabled()) {
            DashScopeService dashScopeService = new DashScopeService(this);
            automationEngine = new AutomationEngine(this, configManager, dashScopeService);
            automationEngine.start();
        }
    }
    
    private void initViews() {
        etApiKey = findViewById(R.id.etApiKey);
        switchTTS = findViewById(R.id.switchTTS);
        switchAutomation = findViewById(R.id.switchAutomation);
        seekContextLength = findViewById(R.id.seekContextLength);
        tvContextLength = findViewById(R.id.tvContextLength);
        ivQRCode = findViewById(R.id.ivQRCode);
        btnGenerateQR = findViewById(R.id.btnGenerateQR);
        btnExport = findViewById(R.id.btnExport);
        btnImport = findViewById(R.id.btnImport);
        btnScanQR = findViewById(R.id.btnScanQR);
        btnSave = findViewById(R.id.btnSave);
        
        // 隐藏二维码图片
        ivQRCode.setVisibility(View.GONE);
        btnGenerateQR.setVisibility(View.GONE);
    }
    
    private void loadConfig() {
        // API Key
        String apiKey = configManager.getApiKey();
        if (apiKey != null) {
            etApiKey.setText(apiKey);
        }
        
        // TTS
        switchTTS.setChecked(configManager.isTTSEnabled());
        
        // 自动化
        switchAutomation.setChecked(configManager.isAutomationEnabled());
        
        // 上下文长度
        int contextLength = configManager.getContextLength();
        seekContextLength.setProgress(contextLength);
        tvContextLength.setText(String.valueOf(contextLength));
    }
    
    private void setupListeners() {
        // 上下文长度滑块
        seekContextLength.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvContextLength.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });
        
        // 生成二维码
        btnGenerateQR.setOnClickListener(v -> generateQRCode());
        
        // 导出配置
        btnExport.setOnClickListener(v -> checkPermissionAndExport());
        
        // 导入配置
        btnImport.setOnClickListener(v -> showImportDialog());
        
        // 扫描二维码
        btnScanQR.setOnClickListener(v -> {
            Toast.makeText(this, "二维码扫描功能待实现", Toast.LENGTH_SHORT).show();
        });
        
        // 保存配置
        btnSave.setOnClickListener(v -> saveConfig());
    }
    
    private void saveConfig() {
        // API Key
        String apiKey = etApiKey.getText().toString().trim();
        if (apiKey.isEmpty()) {
            Toast.makeText(this, "请输入 API Key", Toast.LENGTH_SHORT).show();
            return;
        }
        configManager.setApiKey(apiKey);
        
        // TTS
        configManager.setTTSEnabled(switchTTS.isChecked());
        
        // 自动化
        boolean automationEnabled = switchAutomation.isChecked();
        configManager.setAutomationEnabled(automationEnabled);
        
        // 上下文长度
        configManager.setContextLength(seekContextLength.getProgress());
        
        // 重启自动化引擎（如果状态变更）
        if (automationEnabled) {
            if (automationEngine == null) {
                DashScopeService dashScopeService = new DashScopeService(this);
                automationEngine = new AutomationEngine(this, configManager, dashScopeService);
            }
            automationEngine.reloadRules();
        } else {
            if (automationEngine != null) {
                automationEngine.stop();
                automationEngine = null;
            }
        }
        
        Toast.makeText(this, "配置已保存", Toast.LENGTH_SHORT).show();
        finish();
    }
    
    private void generateQRCode() {
        Bitmap qrBitmap = configManager.generateQRCode(512);
        if (qrBitmap != null) {
            ivQRCode.setImageBitmap(qrBitmap);
            ivQRCode.setVisibility(View.VISIBLE);
            Toast.makeText(this, "二维码已生成，可截图分享", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "生成二维码失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void checkPermissionAndExport() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ 使用存储访问框架
            exportConfigToFile();
        } else {
            // 旧版本请求权限
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            } else {
                exportConfigToFile();
            }
        }
    }
    
    private void exportConfigToFile() {
        try {
            File exportDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOCUMENTS), "OpenClaw");
            if (!exportDir.exists()) {
                exportDir.mkdirs();
            }
            
            File exportFile = new File(exportDir, "openclaw-config.json");
            
            // 复制内部文件到外部
            File internalFile = new File(getFilesDir(), "openclaw-config.json");
            if (internalFile.exists()) {
                copyFile(internalFile, exportFile);
                Toast.makeText(this, "配置已导出到：" + exportFile.getAbsolutePath(), Toast.LENGTH_LONG).show();
                
                // 分享文件
                shareConfigFile(exportFile);
            } else {
                Toast.makeText(this, "配置文件不存在", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void copyFile(File src, File dst) throws Exception {
        InputStream in = new FileInputStream(src);
        OutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        in.close();
        out.close();
    }
    
    private void shareConfigFile(File file) {
        try {
            Uri uri = FileProvider.getUriForFile(this,
                getPackageName() + ".fileprovider", file);
            
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("application/json");
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, "OpenClaw 配置文件");
            shareIntent.putExtra(Intent.EXTRA_TEXT, "这是我的 OpenClaw 配置文件，请导入使用。");
            
            startActivity(Intent.createChooser(shareIntent, "分享配置文件"));
            
        } catch (Exception e) {
            Toast.makeText(this, "分享失败", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void showImportDialog() {
        new AlertDialog.Builder(this)
            .setTitle("导入配置")
            .setMessage("选择导入方式：\n\n1. 扫描二维码（待实现）\n2. 从文件导入")
            .setPositiveButton("从文件导入", (dialog, which) -> pickConfigFile())
            .setNegativeButton("取消", null)
            .show();
    }
    
    private void pickConfigFile() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("application/json");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, REQUEST_SCAN_QR);
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        
        if (requestCode == REQUEST_SCAN_QR && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                importConfigFromUri(uri);
            }
        }
    }
    
    private void importConfigFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                Toast.makeText(this, "无法读取文件", Toast.LENGTH_SHORT).show();
                return;
            }
            
            byte[] buffer = new byte[inputStream.available()];
            inputStream.read(buffer);
            inputStream.close();
            
            String jsonStr = new String(buffer);
            
            if (configManager.importFromJson(jsonStr)) {
                Toast.makeText(this, "配置导入成功", Toast.LENGTH_SHORT).show();
                loadConfig(); // 重新加载显示
            } else {
                Toast.makeText(this, "配置导入失败", Toast.LENGTH_SHORT).show();
            }
            
        } catch (Exception e) {
            Toast.makeText(this, "导入失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    protected void onDestroy() {
        if (automationEngine != null) {
            automationEngine.stop();
        }
        super.onDestroy();
    }
}
