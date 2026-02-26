import 'package:flutter/material.dart';
import 'package:openclaw_home_assistant/core/connectivity/connection_manager.dart';
import 'package:openclaw_home_assistant/core/voice/voice_service.dart';
import 'package:openclaw_home_assistant/core/ai/dashscope_service.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // 初始化连接管理器
  await ConnectionManager().initialize();
  
  // 初始化语音服务
  await VoiceService().initialize();
  
  // 初始化DashScope AI服务
  await DashScopeService().initialize();
  
  runApp(const HomeAssistantApp());
}

class HomeAssistantApp extends StatelessWidget {
  const HomeAssistantApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'OpenClaw Home Assistant',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        useMaterial3: true,
      ),
      home: const HomeAssistantScreen(),
    );
  }
}

class HomeAssistantScreen extends StatefulWidget {
  const HomeAssistantScreen({super.key});

  @override
  State<HomeAssistantScreen> createState() => _HomeAssistantScreenState();
}

class _HomeAssistantScreenState extends State<HomeAssistantScreen> {
  final ConnectionManager _connectionManager = ConnectionManager();
  final VoiceService _voiceService = VoiceService();
  final DashScopeService _dashScopeService = DashScopeService();
  
  bool _isConnected = false;
  String _statusMessage = 'Initializing...';
  bool _isListening = false;
  String _recognizedText = '';
  String _aiResponse = '';

  @override
  void initState() {
    super.initState();
    _setupConnection();
    _setupVoiceRecognition();
  }

  Future<void> _setupConnection() async {
    _connectionManager.connectionStatusStream.listen((status) {
      setState(() {
        _isConnected = status.isConnected;
        _statusMessage = status.message;
      });
    });
    
    await _connectionManager.connectToControlServer();
  }

  Future<void> _setupVoiceRecognition() async {
    _voiceService.onResult.listen((result) {
      setState(() {
        _recognizedText = result;
      });
      
      // 将识别结果发送给AI服务
      _processWithAI(result);
    });
    
    _voiceService.onError.listen((error) {
      debugPrint('Voice recognition error: $error');
    });
  }

  Future<void> _processWithAI(String text) async {
    try {
      final response = await _dashScopeService.processQuery(text);
      setState(() {
        _aiResponse = response;
      });
      
      // 发送AI响应到控制端
      _connectionManager.sendMessage({
        'type': 'ai_response',
        'query': text,
        'response': response,
        'timestamp': DateTime.now().toIso8601String(),
      });
    } catch (e) {
      debugPrint('AI processing error: $e');
      setState(() {
        _aiResponse = 'Sorry, I encountered an error processing your request.';
      });
    }
  }

  Future<void> _startListening() async {
    if (!_isListening) {
      await _voiceService.startListening();
      setState(() {
        _isListening = true;
        _recognizedText = '';
        _aiResponse = '';
      });
    }
  }

  Future<void> _stopListening() async {
    if (_isListening) {
      await _voiceService.stopListening();
      setState(() {
        _isListening = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('OpenClaw Home Assistant'),
        backgroundColor: _isConnected ? Colors.green : Colors.red,
      ),
      body: Padding(
        padding: const EdgeInsets.all(16.0),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              'Status: $_statusMessage',
              style: TextStyle(
                color: _isConnected ? Colors.green : Colors.red,
                fontWeight: FontWeight.bold,
              ),
            ),
            const SizedBox(height: 20),
            
            // 语音控制区域
            Card(
              child: Padding(
                padding: const EdgeInsets.all(16.0),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Text(
                      'Voice Assistant',
                      style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18),
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        Expanded(
                          child: ElevatedButton(
                            onPressed: _isListening ? _stopListening : _startListening,
                            child: Text(_isListening ? 'Stop Listening' : 'Start Listening'),
                          ),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    if (_recognizedText.isNotEmpty)
                      Text(
                        'Recognized: $_recognizedText',
                        style: const TextStyle(color: Colors.blue),
                      ),
                    if (_aiResponse.isNotEmpty)
                      Text(
                        'AI Response: $_aiResponse',
                        style: const TextStyle(color: Colors.green),
                      ),
                  ],
                ),
              ),
            ),
            
            const SizedBox(height: 20),
            
            // 数据收集状态
            const Text(
              'Data Collection Status:',
              style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
            ),
            const SizedBox(height: 10),
            Expanded(
              child: ListView(
                children: const [
                  DataCollectionItem(title: 'Location History', enabled: true),
                  DataCollectionItem(title: 'App Usage', enabled: true),
                  DataCollectionItem(title: 'Call Logs', enabled: true),
                  DataCollectionItem(title: 'SMS Messages', enabled: true),
                  DataCollectionItem(title: 'Calendar Events', enabled: true),
                  DataCollectionItem(title: 'Contacts', enabled: true),
                  DataCollectionItem(title: 'Battery Usage', enabled: true),
                  DataCollectionItem(title: 'Network Activity', enabled: true),
                  DataCollectionItem(title: 'Voice Recognition', enabled: true),
                  DataCollectionItem(title: 'AI Processing', enabled: true),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class DataCollectionItem extends StatelessWidget {
  final String title;
  final bool enabled;

  const DataCollectionItem({
    super.key,
    required this.title,
    required this.enabled,
  });

  @override
  Widget build(BuildContext context) {
    return Card(
      child: ListTile(
        title: Text(title),
        trailing: enabled
            ? const Icon(Icons.check_circle, color: Colors.green)
            : const Icon(Icons.cancel, color: Colors.red),
      ),
    );
  }
}