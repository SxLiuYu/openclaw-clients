package com.openclaw.homeassistant;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 历史记录界面
 */
public class HistoryActivity extends AppCompatActivity {
    
    private ListView listHistory;
    private Button btnClearHistory;
    private ConversationManager conversationManager;
    private List<ConversationManager.ChatHistory> historyList;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        
        conversationManager = new ConversationManager(this);
        historyList = conversationManager.getHistory();
        
        initViews();
        setupListeners();
    }
    
    private void initViews() {
        listHistory = findViewById(R.id.listHistory);
        btnClearHistory = findViewById(R.id.btnClearHistory);
        
        if (historyList.isEmpty()) {
            listHistory.setVisibility(View.GONE);
            TextView emptyView = new TextView(this);
            emptyView.setText("📜 暂无历史记录");
            emptyView.setTextSize(18);
            emptyView.setPadding(32, 64, 32, 64);
            ((ViewGroup) listHistory.getParent()).addView(emptyView);
        } else {
            listHistory.setAdapter(new HistoryAdapter());
        }
    }
    
    private void setupListeners() {
        btnClearHistory.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                .setTitle("确认清空")
                .setMessage("确定要清空所有历史记录吗？此操作不可恢复。")
                .setPositiveButton("清空", (dialog, which) -> {
                    conversationManager.clearHistory();
                    Toast.makeText(this, "历史记录已清空", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
        });
        
        listHistory.setOnItemClickListener((parent, view, position, id) -> {
            ConversationManager.ChatHistory session = historyList.get(position);
            new AlertDialog.Builder(this)
                .setTitle("查看历史对话")
                .setMessage(session.preview + "\n\n确定要加载这段对话吗？")
                .setPositiveButton("加载", (dialog, which) -> {
                    // 加载历史对话到当前上下文
                    for (ConversationManager.Message msg : session.messages) {
                        conversationManager.addToContext(msg.role, msg.content);
                    }
                    Toast.makeText(this, "对话已加载", Toast.LENGTH_SHORT).show();
                    finish();
                })
                .setNegativeButton("取消", null)
                .show();
        });
    }
    
    private class HistoryAdapter extends BaseAdapter {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("MM-dd HH:mm", Locale.CHINA);
        
        @Override
        public int getCount() {
            return historyList.size();
        }
        
        @Override
        public Object getItem(int position) {
            return historyList.get(position);
        }
        
        @Override
        public long getItemId(int position) {
            return position;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(android.R.layout.simple_list_item_2, parent, false);
            }
            
            ConversationManager.ChatHistory session = historyList.get(position);
            
            TextView text1 = convertView.findViewById(android.R.id.text1);
            TextView text2 = convertView.findViewById(android.R.id.text2);
            
            text1.setText(session.preview);
            text2.setText(dateFormat.format(new Date(session.timestamp)) + " · " + session.messages.size() + " 条消息");
            
            return convertView;
        }
    }
}
