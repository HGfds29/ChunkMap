# ChunkMap

> 客户端侧 Minecraft Fabric 模组：实时把周围区块渲染成一张俯视地图。

[![GitHub](https://img.shields.io/badge/GitHub-HGfds29%2FChunkMap-181717?logo=github)](https://github.com/HGfds29/ChunkMap)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.18.0%2B-DBB69C)](https://fabricmc.net/)

## ✨ 特性

- 🗺️ **实时俯视地图**：`M` 键随时开图，`Alt + M` 打开配置
- 🎨 **双颜色模式**：原版地图色 `MAP_COLOR` / 纹理平均色 `TEXTURE_AVERAGE`
- ⛰️ **方向性光照**：山脊亮、坡谷暗，可选绝对高度明暗
- 💾 **自动导出**：每个区块实时导出 PNG 瓦片，可一键拼接整张大地图
- 📋 **内置反馈面板**：地图里点“反馈”即可按级别查看日志、一键复制、跳转 Issues
- ⭐ **一键 Star**：地图右上角金色 `★ Star` 按钮直达仓库
- ⚙️ **全部配置热重载**：改完配置按 `R` 立刻生效，无需重启游戏
- 🔧 **分辨率三档可选**：`16×16` / `32×32` / `64×64`

## 📦 安装

1. 安装 [Fabric Loader](https://fabricmc.net/) `0.18.0+`
2. 安装 [Fabric API](https://modrinth.com/mod/fabric-api)
3. 将 `chunkmap-x.x.x.jar` 放入 `.minecraft/mods/`
4. 可选：[Cloth Config](https://modrinth.com/mod/cloth-config)、[Mod Menu](https://modrinth.com/mod/modmenu)

## ⌨️ 快捷键

| 快捷键 | 功能 |
|---|---|
| `M` | 打开 / 关闭地图界面 |
| `Alt + M` | 打开配置界面 |
| `E`（地图内） | 导出拼接大图 |
| `C`（地图内） | 视角回到玩家 |
| `R`（地图内） | 热重载配置 + 重渲染 |
| `C`（反馈内） | 复制全部日志到剪贴板 |
| `R` / `F5`（反馈内） | 重新读取日志 |

## ⚙️ 配置

配置文件：`config/chunkmap.json`

| 字段 | 类型 | 说明 |
|---|---|---|
| `tileResolution` | `16` / `32` / `64` | 每个区块瓦片的像素边长，越大越清晰也越吃内存 |
| `outputDir` | 字符串 | 瓦片输出根目录（相对游戏目录） |
| `colorMode` | `MAP_COLOR` / `TEXTURE_AVERAGE` | 颜色来源 |
| `shadeByHeight` | 布尔 | 是否根据地势高度施加方向性光照 |
| `uiAnimation` | 布尔 | UI 平滑动画开关 |
| `deleteOnUnload` | 布尔 | 区块卸载时是否删除瓦片文件 |
| `renderThreads` | `1` ~ `8` | 渲染工作线程数（修改后自动重建线程池） |
| `logRetentionDays` | `0` ~ `30` | 日志保留天数，`0` 为永久保留 |

## 📂 输出目录结构

```
chunkmap-output/
├── minecraft/
│   ├── overworld/
│   │   ├── 0_0.png
│   │   └── 1_-2.png
│   └── the_nether/
│       └── 0_0.png
└── stitched/
    └── overworld_20240101_120000.png
```

## 🔨 构建

```bash
./gradlew build
# 产物：build/libs/chunkmap-<version>.jar
```

## 📝 反馈

地图 UI 内点击 **反馈** 按钮，可以：

- 按级别查看日志（TRACE / DEBUG / INFO / WARN / ERROR 分级着色）
- 点击 **复制全部** 把日志复制到剪贴板
- 点击 **前往 Issues** 直接跳转 [Issues 页](https://github.com/HGfds29/ChunkMap/issues/new)，日志已自动复制到剪贴板
- 点击 **打开目录** 在系统文件管理器里查看日志文件

也可以在游戏外直接查看：`logs/chunkmap/chunkmap_<时间戳>.log`

## 📄 License

MIT