# OpenClaw Clients

Multi-platform client applications for OpenClaw家庭助手项目。

## 当前开发进度
✅ **Flutter移动端** - 语音识别 + DashScope API集成完成
- 语音唤醒和识别功能
- AI对话和指令处理
- 跨设备协同基础架构
- 数据收集和加密传输

🔄 **其他平台** - 待开发
- Android原生客户端
- iOS原生客户端  
- 智能音箱端
- 桌面端
- 手表端

## Platforms
- **Flutter Mobile**: 跨平台移动客户端（当前重点）
- **Android**: 原生移动客户端 (Java/Kotlin)
- **WearOS**: 智能手表客户端 (Kotlin)  
- **iOS**: iPhone/iPad客户端 (Swift)
- **Web**: 浏览器客户端 (React/Vue)
- **Desktop**: 跨平台桌面客户端 (Electron/C++)
- **Smart Speaker**: 智能音箱客户端 (Python)

## Flutter移动端使用说明

### 环境配置
1. 安装Flutter SDK (>=3.0.0)
2. 配置Android/iOS开发环境
3. 复制`.env.example`为`.env`并配置API密钥

### API密钥配置
在`.env`文件中配置：
```env
CONTROL_SERVER_URL=ws://your-control-server:8080/ws
DASHSCOPE_API_KEY=your-dashscope-api-key
```

### 功能特性
- 🎤 **语音识别**: 实时语音转文本
- 🤖 **AI对话**: 基于DashScope的智能对话
- 📱 **跨设备协同**: 与控制端实时同步
- 🔒 **数据安全**: 端到端加密传输
- 📊 **数据收集**: 用户行为分析和偏好学习

### 开发命令
```bash
# 运行应用
flutter run

# 构建APK
flutter build apk

# 构建iOS
flutter build ios
```

## Getting Started
```bash
git clone https://github.com/SxLiuYu/openclaw-clients.git
cd openclaw-clients/flutter_mobile
flutter pub get
cp .env.example .env
# 编辑.env文件配置API密钥
flutter run
```

## Project Structure
```
openclaw-clients/
├── flutter_mobile/   # Flutter跨平台移动客户端（当前重点）
├── android/         # Android原生客户端
├── ios/             # iOS原生客户端
├── wearos/          # Wear OS智能手表客户端  
├── web/             # Web客户端
├── desktop/         # 桌面客户端
├── smart_speaker/   # 智能音箱客户端
├── ARCHITECTURE.md  # 架构设计文档
├── CONCURRENCY_COMPARISON.md  # 并发模型对比
└── PROGRESS.md      # 项目进度记录
```

## 下一步计划
1. 完善Flutter移动端UI/UX
2. 实现完整的语音助手交互流程
3. 开发智能音箱端Python服务
4. 建立跨设备协同测试环境
