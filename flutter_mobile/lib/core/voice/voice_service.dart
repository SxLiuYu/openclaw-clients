// 语音识别服务 - 集成Flutter语音识别和DashScope API
import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:speech_to_text/speech_to_text.dart' as stt;
import 'package:http/http.dart' as http;
import 'package:shared_preferences/shared_preferences.dart';

class VoiceRecognitionResult {
  final String recognizedText;
  final double confidence;
  final bool isFinal;
  
  VoiceRecognitionResult({
    required this.recognizedText,
    required this.confidence,
    required this.isFinal,
  });
}

class VoiceService {
  static final VoiceService _instance = VoiceService._internal();
  factory VoiceService() => _instance;
  VoiceService._internal();
  
  final stt.SpeechToText _speech = stt.SpeechToText();
  final StreamController<VoiceRecognitionResult> _recognitionController = 
      StreamController<VoiceRecognitionResult>.broadcast();
  late SharedPreferences _prefs;
  
  // DashScope API配置
  static const String _dashScopeApiKey = 'your-dashscope-api-key-here';
  static const String _dashScopeEndpoint = 'https://dashscope.aliyuncs.com/api/v1/services/audio/transcription';
  
  Stream<VoiceRecognitionResult> get recognitionStream => _recognitionController.stream;
  
  Future<void> initialize() async {
    _prefs = await SharedPreferences.getInstance();
    await _speech.initialize(
      onError: _handleError,
      onStatus: _handleStatus,
    );
  }
  
  Future<bool> startListening() async {
    if (!_speech.isAvailable) {
      return false;
    }
    
    try {
      await _speech.listen(
        onResult: _handleResult,
        localeId: 'zh_CN', // 中文识别
        listenFor: const Duration(seconds: 30),
        pauseFor: const Duration(seconds: 2),
        partialResults: true,
        onSoundLevelChange: _handleSoundLevel,
      );
      return true;
    } catch (e) {
      debugPrint('Failed to start listening: $e');
      return false;
    }
  }
  
  void stopListening() {
    _speech.stop();
  }
  
  void cancelListening() {
    _speech.cancel();
  }
  
  // 处理语音识别结果
  void _handleResult(stt.SpeechRecognitionResult result) {
    final voiceResult = VoiceRecognitionResult(
      recognizedText: result.recognizedWords,
      confidence: result.confidence,
      isFinal: result.isFinal,
    );
    
    _recognitionController.add(voiceResult);
    
    // 如果是最终结果，发送到DashScope API进行进一步处理
    if (result.isFinal && result.confidence > 0.7) {
      _processWithDashScope(result.recognizedWords);
    }
  }
  
  // 使用DashScope API处理语音文本
  Future<void> _processWithDashScope(String text) async {
    try {
      final response = await http.post(
        Uri.parse('https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation'),
        headers: {
          'Authorization': 'Bearer $_dashScopeApiKey',
          'Content-Type': 'application/json',
        },
        body: jsonEncode({
          'model': 'qwen-max',
          'input': {
            'messages': [
              {
                'role': 'system',
                'content': '你是一个家庭助手，请理解用户的语音指令并提供相应的帮助。'
              },
              {
                'role': 'user',
                'content': text
              }
            ]
          },
          'parameters': {
            'temperature': 0.7,
            'top_p': 0.8,
          }
        }),
      );
      
      if (response.statusCode == 200) {
        final jsonResponse = jsonDecode(response.body);
        final aiResponse = jsonResponse['output']['choices'][0]['message']['content'];
        
        // 发送AI响应到主应用
        _sendAIResponse(aiResponse);
      }
    } catch (e) {
      debugPrint('DashScope API error: $e');
    }
  }
  
  void _sendAIResponse(String response) {
    // TODO: 实现AI响应的处理逻辑
    debugPrint('AI Response: $response');
  }
  
  void _handleError(stt.SpeechRecognitionError error) {
    debugPrint('Speech recognition error: ${error.errorMsg}');
  }
  
  void _handleStatus(String status) {
    debugPrint('Speech recognition status: $status');
  }
  
  void _handleSoundLevel(double level) {
    // 可用于UI反馈，显示音量级别
  }
  
  bool get isListening => _speech.isListening;
  bool get isAvailable => _speech.isAvailable;
}