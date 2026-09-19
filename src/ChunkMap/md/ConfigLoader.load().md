- 读取 `config/chunkmap.json`。
    
- 存在则解析 JSON，转成 `TileMapConfig`，再 `validated()` 修正非法值。
    
- 不存在则生成默认配置并保存。
    
- 读取失败则使用默认值。