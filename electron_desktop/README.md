# OpenClaw 桌面客户端 (Electron)

OpenClaw 家庭助手的跨平台桌面客户端，基于 Electron 构建。

## 功能特性

- 💻 **跨平台** - Windows / macOS / Linux
- 🎤 **语音输入** - Web Speech API 支持
- 💬 **AI 对话** - DashScope API 集成
- ⌨️ **快捷键** - F2 语音，Ctrl+Enter 发送
- 📱 **系统托盘** - 最小化到托盘
- 🎨 **现代化 UI** - 渐变设计

## 系统要求

- Node.js 18+
- npm 或 yarn

## 安装与运行

### 1. 安装依赖
```bash
cd electron_desktop
npm install
```

### 2. 开发模式运行
```bash
npm start
```

### 3. 构建可执行文件
```bash
npm run build
```

构建产物在 `dist/` 目录：
- Windows: `.exe` 安装程序
- macOS: `.dmg` 镜像
- Linux: `.AppImage`

## 使用说明

### 首次使用
1. 启动应用
2. 点击右上角 ⚙️ 设置
3. 输入 DashScope API Key
4. 保存设置

### 快捷键
- **F2**: 开始/停止语音输入
- **Ctrl+Enter**: 发送消息

### 系统托盘
- 关闭窗口会最小化到托盘
- 双击托盘图标恢复窗口
- 右键托盘菜单可退出应用

## 项目结构

```
electron_desktop/
├── src/
│   ├── main.js          # Electron 主进程
│   └── preload.js       # 预加载脚本
├── index.html           # 主界面
├── package.json         # 项目配置
└── README.md            # 说明文档
```

## 技术栈

- **框架**: Electron 28
- **UI**: 原生 HTML/CSS/JavaScript
- **语音**: Web Speech API
- **打包**: electron-builder

## 下一步计划

- [ ] 全局快捷键唤醒
- [ ] 自动启动
- [ ] 多账号支持
- [ ] 对话历史记录
- [ ] 主题切换

## 许可证

MIT License
