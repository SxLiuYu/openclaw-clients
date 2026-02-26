// DashScope API服务 - 阿里云大模型API集成
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:flutter_dotenv/flutter_dotenv.dart';

class DashScopeService {
  static final DashScopeService _instance = DashScopeService._internal();
  factory DashScopeService() => _instance;
  DashScopeService._internal();

  late String _apiKey;
  final String _baseUrl = 'https://dashscope.aliyuncs.com/api/v1';

  Future<void> initialize() async {
    await dotenv.load(fileName: ".env");
    _apiKey = dotenv.env['DASHSCOPE_API_KEY'] ?? '';
    
    if (_apiKey.isEmpty) {
      throw Exception('DASHSCOPE_API_KEY not found in .env file');
    }
  }

  // 处理用户查询
  Future<String> processQuery(String query) async {
    try {
      final response = await http.post(
        Uri.parse('$_baseUrl/services/aigc/text-generation/generation'),
        headers: {
          'Authorization': 'Bearer $_apiKey',
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
                'content': query
              }
            ]
          },
          'parameters': {
            'temperature': 0.7,
            'top_p': 0.8,
            'max_tokens': 500,
          }
        }),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        return data['output']['choices'][0]['message']['content'];
      } else {
        throw Exception('API request failed with status ${response.statusCode}');
      }
    } catch (e) {
      throw Exception('Failed to process query: $e');
    }
  }

  // 文本生成（对话）
  Future<String> generateText({
    required String prompt,
    String model = 'qwen-max',
    int maxTokens = 500,
    double temperature = 0.7,
  }) async {
    final url = '$_baseUrl/services/aigc/text-generation/generation';
    final headers = {
      'Authorization': 'Bearer $_apiKey',
      'Content-Type': 'application/json',
    };

    final body = {
      'model': model,
      'input': {
        'messages': [
          {'role': 'user', 'content': prompt}
        ]
      },
      'parameters': {
        'max_tokens': maxTokens,
        'temperature': temperature,
        'top_p': 0.8,
      }
    };

    try {
      final response = await http.post(
        Uri.parse(url),
        headers: headers,
        body: jsonEncode(body),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        return data['output']['choices'][0]['message']['content'];
      } else {
        throw Exception('API request failed with status ${response.statusCode}: ${response.body}');
      }
    } catch (e) {
      throw Exception('Failed to generate text: $e');
    }
  }

  // 语音转文本（ASR）
  Future<String> speechToText(String audioUrl) async {
    final url = '$_baseUrl/services/audio/transcription';
    final headers = {
      'Authorization': 'Bearer $_apiKey',
      'Content-Type': 'application/json',
    };

    final body = {
      'model': 'paraformer-realtime-v1',
      'input': {
        'url': audioUrl,
      },
      'parameters': {
        'format': 'wav',
        'sample_rate': 16000,
        'language': 'zh-CN',
      }
    };

    try {
      final response = await http.post(
        Uri.parse(url),
        headers: headers,
        body: jsonEncode(body),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        return data['output']['results'][0]['text'];
      } else {
        throw Exception('ASR request failed with status ${response.statusCode}: ${response.body}');
      }
    } catch (e) {
      throw Exception('Failed to transcribe speech: $e');
    }
  }

  // 文本转语音（TTS）
  Future<String> textToSpeech(String text) async {
    final url = '$_baseUrl/services/audio/tts';
    final headers = {
      'Authorization': 'Bearer $_apiKey',
      'Content-Type': 'application/json',
    };

    final body = {
      'model': 'sambert-zhichu-v1',
      'input': {
        'text': text,
      },
      'parameters': {
        'voice': 'zhiyu',
        'volume': 50,
        'rate': 1.0,
        'pitch': 1.0,
      }
    };

    try {
      final response = await http.post(
        Uri.parse(url),
        headers: headers,
        body: jsonEncode(body),
      );

      if (response.statusCode == 200) {
        final data = jsonDecode(response.body);
        return data['output']['results'][0]['url'];
      } else {
        throw Exception('TTS request failed with status ${response.statusCode}: ${response.body}');
      }
    } catch (e) {
      throw Exception('Failed to generate speech: $e');
    }
  }
}