const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('electronAPI', {
  getApiKey: () => ipcRenderer.invoke('get-api-key'),
  saveApiKey: (key) => ipcRenderer.invoke('save-api-key', key)
});
