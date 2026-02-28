package com.openclaw.homeassistant;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备列表 Activity
 * 功能：查看设备列表、设备状态、登录/注册
 */
public class DeviceListActivity extends AppCompatActivity 
    implements DeviceSyncService.DeviceSyncListener {
    
    private DeviceSyncService deviceSyncService;
    private ListView listView;
    private DevicesAdapter adapter;
    private List<DeviceSyncService.DeviceInfo> deviceList;
    private SwipeRefreshLayout swipeRefresh;
    private TextView tvEmpty;
    private View loginPanel;
    private EditText etUsername;
    private EditText etPassword;
    private Button btnLogin;
    private Button btnRegister;
    private Button btnLogout;
    private TextView tvCurrentUser;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device_list);
        
        deviceSyncService = new DeviceSyncService(this);
        DeviceSyncService.setListener(this);
        
        initViews();
        checkLoginStatus();
    }
    
    private void initViews() {
        listView = findViewById(R.id.listView);
        swipeRefresh = findViewById(R.id.swipeRefresh);
        tvEmpty = findViewById(R.id.tvEmpty);
        loginPanel = findViewById(R.id.loginPanel);
        etUsername = findViewById(R.id.etUsername);
        etPassword = findViewById(R.id.etPassword);
        btnLogin = findViewById(R.id.btnLogin);
        btnRegister = findViewById(R.id.btnRegister);
        btnLogout = findViewById(R.id.btnLogout);
        tvCurrentUser = findViewById(R.id.tvCurrentUser);
        
        deviceList = new ArrayList<>();
        adapter = new DevicesAdapter();
        listView.setAdapter(adapter);
        
        // 下拉刷新
        swipeRefresh.setOnRefreshListener(() -> {
            if (deviceSyncService.isLoggedIn()) {
                deviceSyncService.fetchDeviceList();
            } else {
                swipeRefresh.setRefreshing(false);
            }
        });
        swipeRefresh.setColorSchemeResources(
            android.R.color.holo_blue_bright,
            android.R.color.holo_green_light,
            android.R.color.holo_orange_light
        );
        
        // 登录按钮
        btnLogin.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
                return;
            }
            
            deviceSyncService.loginUser(username, password);
        });
        
        // 注册按钮
        btnRegister.setOnClickListener(v -> {
            String username = etUsername.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            
            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "请输入用户名和密码", Toast.LENGTH_SHORT).show();
                return;
            }
            
            if (password.length() < 6) {
                Toast.makeText(this, "密码至少 6 位", Toast.LENGTH_SHORT).show();
                return;
            }
            
            deviceSyncService.registerUser(username, password);
        });
        
        // 登出按钮
        btnLogout.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("确认登出")
                .setMessage("确定要登出吗？")
                .setPositiveButton("登出", (dialog, which) -> {
                    deviceSyncService.logout();
                    checkLoginStatus();
                    deviceList.clear();
                    adapter.notifyDataSetChanged();
                    updateEmptyView();
                })
                .setNegativeButton("取消", null)
                .show();
        });
    }
    
    private void checkLoginStatus() {
        if (deviceSyncService.isLoggedIn()) {
            // 已登录
            loginPanel.setVisibility(View.GONE);
            btnLogout.setVisibility(View.VISIBLE);
            
            String username = deviceSyncService.getPrefs().getString("username", "用户");
            tvCurrentUser.setText("👤 " + username);
            
            // 获取设备列表
            deviceSyncService.fetchDeviceList();
            
        } else {
            // 未登录
            loginPanel.setVisibility(View.VISIBLE);
            btnLogout.setVisibility(View.GONE);
            tvCurrentUser.setText("");
        }
    }
    
    @Override
    public void onDevicesUpdated(List<DeviceSyncService.DeviceInfo> devices) {
        runOnUiThread(() -> {
            swipeRefresh.setRefreshing(false);
            
            deviceList.clear();
            deviceList.addAll(devices);
            adapter.notifyDataSetChanged();
            
            updateEmptyView();
        });
    }
    
    @Override
    public void onRegisterSuccess(String userId) {
        runOnUiThread(() -> {
            Toast.makeText(this, "✅ 注册成功", Toast.LENGTH_SHORT).show();
            checkLoginStatus();
        });
    }
    
    @Override
    public void onError(String error) {
        runOnUiThread(() -> {
            swipeRefresh.setRefreshing(false);
            Toast.makeText(this, "❌ " + error, Toast.LENGTH_LONG).show();
        });
    }
    
    private void updateEmptyView() {
        if (deviceList.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            listView.setVisibility(View.GONE);
        } else {
            tvEmpty.setVisibility(View.GONE);
            listView.setVisibility(View.VISIBLE);
        }
    }
    
    class DevicesAdapter extends ArrayAdapter<DeviceSyncService.DeviceInfo> {
        DevicesAdapter() {
            super(DeviceListActivity.this, 0, deviceList);
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(DeviceListActivity.this)
                    .inflate(R.layout.item_device, parent, false);
            }
            
            DeviceSyncService.DeviceInfo device = deviceList.get(position);
            
            TextView tvDeviceName = convertView.findViewById(R.id.tvDeviceName);
            TextView tvDeviceModel = convertView.findViewById(R.id.tvDeviceModel);
            TextView tvStatus = convertView.findViewById(R.id.tvStatus);
            TextView tvBattery = convertView.findViewById(R.id.tvBattery);
            ImageView ivStatus = convertView.findViewById(R.id.ivStatus);
            
            tvDeviceName.setText(device.deviceName);
            tvDeviceModel.setText(device.deviceModel);
            tvBattery.setText(device.getBatteryText());
            
            if (device.isOnline()) {
                tvStatus.setText("在线");
                tvStatus.setTextColor(0xFF4CAF50);
                ivStatus.setImageResource(android.R.drawable.presence_online);
            } else {
                tvStatus.setText(device.lastSeen);
                tvStatus.setTextColor(0xFF9E9E9E);
                ivStatus.setImageResource(android.R.drawable.presence_offline);
            }
            
            return convertView;
        }
    }
}
