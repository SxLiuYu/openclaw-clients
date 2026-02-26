import 'package:flutter/material.dart';
import 'package:openclaw_home_assistant/core/connectivity/connection_manager.dart';
import 'package:openclaw_home_assistant/core/voice/voice_service.dart';
import 'package:openclaw_home_assistant/core/ai/dashscope_service.dart';
import 'package:openclaw_home_assistant/ui/home_assistant_screen.dart';

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