package com.openclaw.homeassistant;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/**
 * 对话管理器 - 处理多轮对话上下文和历史记录
 */
public class ConversationManager {
    private static final String PREF_CONVERSATION = "conversation_history";
    private static final String PREF_HISTORY = "chat_history";
    private static final int MAX_CONTEXT_SIZE = 20; // 最多保存 20 条上下文
    
    private final SharedPreferences contextPrefs;
    private final SharedPreferences historyPrefs;
    private List<Message> conversationContext;
    private List<ChatHistory> chatHistory;
    
    public static class Message {
        public String role;
        public String content;
        public long timestamp;
        
        public Message(String role, String content) {
            this.role = role;
            this.content = content;
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    public static class ChatHistory {
        public String id;
        public String preview;
        public long timestamp;
        public List<Message> messages;
        
        public ChatHistory(String preview, List<Message> messages) {
            this.id = String.valueOf(System.currentTimeMillis());
            this.preview = preview;
            this.timestamp = System.currentTimeMillis();
            this.messages = messages;
        }
    }
    
    public ConversationManager(Context context) {
        contextPrefs = context.getSharedPreferences(PREF_CONVERSATION, Context.MODE_PRIVATE);
        historyPrefs = context.getSharedPreferences(PREF_HISTORY, Context.MODE_PRIVATE);
        loadContext();
        loadHistory();
    }
    
    private void loadContext() {
        conversationContext = new ArrayList<>();
        String json = contextPrefs.getString("context", "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                conversationContext.add(new Message(
                    obj.getString("role"),
                    obj.getString("content")
                ));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void loadHistory() {
        chatHistory = new ArrayList<>();
        String json = historyPrefs.getString("history", "[]");
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.getJSONObject(i);
                ChatHistory history = new ChatHistory(
                    obj.getString("preview"),
                    new ArrayList<>()
                );
                history.timestamp = obj.getLong("timestamp");
                history.id = obj.getString("id");
                
                JSONArray messages = obj.getJSONArray("messages");
                for (int j = 0; j < messages.length(); j++) {
                    JSONObject msg = messages.getJSONObject(j);
                    history.messages.add(new Message(
                        msg.getString("role"),
                        msg.getString("content")
                    ));
                }
                chatHistory.add(history);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void addToContext(String role, String content) {
        conversationContext.add(new Message(role, content));
        
        // 限制上下文大小
        if (conversationContext.size() > MAX_CONTEXT_SIZE) {
            conversationContext = conversationContext.subList(
                conversationContext.size() - MAX_CONTEXT_SIZE,
                conversationContext.size()
            );
        }
        
        saveContext();
    }
    
    public void saveContext() {
        try {
            JSONArray array = new JSONArray();
            for (Message msg : conversationContext) {
                JSONObject obj = new JSONObject();
                obj.put("role", msg.role);
                obj.put("content", msg.content);
                array.put(obj);
            }
            contextPrefs.edit().putString("context", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public List<Message> getContext() {
        return new ArrayList<>(conversationContext);
    }
    
    public void clearContext() {
        conversationContext.clear();
        saveContext();
    }
    
    public void addToHistory(String preview, List<Message> messages) {
        ChatHistory history = new ChatHistory(preview, messages);
        chatHistory.add(0, history); // 新记录添加到开头
        
        // 限制历史记录数量（最近 50 条）
        if (chatHistory.size() > 50) {
            chatHistory = chatHistory.subList(0, 50);
        }
        
        saveHistory();
    }
    
    public void saveHistory() {
        try {
            JSONArray array = new JSONArray();
            for (ChatHistory history : chatHistory) {
                JSONObject obj = new JSONObject();
                obj.put("id", history.id);
                obj.put("preview", history.preview);
                obj.put("timestamp", history.timestamp);
                
                JSONArray messages = new JSONArray();
                for (Message msg : history.messages) {
                    JSONObject msgObj = new JSONObject();
                    msgObj.put("role", msg.role);
                    msgObj.put("content", msg.content);
                    messages.put(msgObj);
                }
                obj.put("messages", messages);
                array.put(obj);
            }
            historyPrefs.edit().putString("history", array.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public List<ChatHistory> getHistory() {
        return new ArrayList<>(chatHistory);
    }
    
    public void clearHistory() {
        chatHistory.clear();
        saveHistory();
    }
    
    public List<Message> getContextForAPI(int maxMessages) {
        List<Message> result = new ArrayList<>();
        result.add(new Message("system", "你是一个家庭助手，请理解用户的语音指令并提供相应的帮助。保持对话连贯性。"));
        
        // 添加最近的上下文
        int start = Math.max(0, conversationContext.size() - maxMessages);
        for (int i = start; i < conversationContext.size(); i++) {
            result.add(conversationContext.get(i));
        }
        
        return result;
    }
}
