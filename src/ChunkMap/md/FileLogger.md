- 创建 `logs/chunkmap/chunkmap_yyyy-MM-dd_HH-mm-ss.log`。
    
- 设置日志级别为 `INFO`。
    
- 调用 `cleanOldLogs(config.logRetentionDays())` 清理旧日志。
    
- 输出启动信息、版本、配置、日志路径。