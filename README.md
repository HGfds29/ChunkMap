# ChunkMap

> 为 Minecraft Fabric 客户端打造的俯视区块地图渲染器。实时快照、多线程渲染、支持 PNG 导出。

[![License](https://img.shields.io/github/license/HGfds29/ChunkMap?style=flat-square)](https://github.com/HGfds29/ChunkMap/blob/main/LICENSE)
[![Release](https://img.shields.io/github/v/release/HGfds29/ChunkMap?style=flat-square&include_prereleases)](https://github.com/HGfds29/ChunkMap/releases)
[![Issues](https://img.shields.io/github/issues/HGfds29/ChunkMap?style=flat-square)](https://github.com/HGfds29/ChunkMap/issues)
[![Stars](https://img.shields.io/github/stars/HGfds29/ChunkMap?style=flat-square)](https://github.com/HGfds29/ChunkMap/stargazers)
[![Last Commit](https://img.shields.io/github/last-commit/HGfds29/ChunkMap?style=flat-square)](https://github.com/HGfds29/ChunkMap/commits/main)
[![Code Size](https://img.shields.io/github/languages/code-size/HGfds29/ChunkMap?style=flat-square)](https://github.com/HGfds29/ChunkMap)

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A?style=flat-square)](https://www.minecraft.net/)
[![Fabric Loader](https://img.shields.io/badge/Fabric_Loader-%3E%3D0.18.0-DBB69B?style=flat-square)](https://fabricmc.net/)
[![Java](https://img.shields.io/badge/Java-%3E%3D21-ED8B00?style=flat-square&logo=openjdk&logoColor=white)](https://adoptium.net/)

---

## 简介

ChunkMap 是一个纯客户端 Fabric 模组，用于在游戏内实时查看并导出你探索过的区块俯视图。它通过 Mixin 监听区块变化，将每个区块顶部方块的颜色渲染为瓦片，并保存在内存缓存中，最终拼合成一张可缩放、可平移的地图。

地图数据同时写入磁盘，支持一键导出为单张 PNG 大图，方便分享或制作宣传素材。

---

## 特性

- **实时渲染**：区块加载或方块变化时自动重新渲染，无需手动刷新。
- **多线程工作池**：渲染任务在工作线程执行，主线程仅负责快照读取，最大程度减少卡顿。
- **双颜色模式**：
  - `MAP_COLOR`：基于原版 `MapColor`，配合饱和度与明度微调，观感接近原版地图。
  - `TEXTURE_AVERAGE`：基于方块粒子图标的纹理平均色，色彩更接近真实材质。
- **高度明暗**：根据地形坡度与海拔高度施加方向性光照，突出山脊与谷地。
- **可调瓦片分辨率**：16 / 32 / 64 三档，兼顾清晰度与性能。
- **PNG 导出**：将已渲染瓦片按区块坐标拼合成单张大图，附带网格底纹。
- **游戏内反馈**：内置反馈界面，可自动附上日志、系统信息、模组列表与配置文件，一键提交 Issue。
- **日志查看器**：内置日志浏览界面，支持滚动、复制与打开目录。
- **ModMenu 集成**：通过 ModMenu 或 `Alt + M` 打开图形化配置界面。

---

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.21.11 |
| Fabric Loader | >= 0.18.0 |
| Fabric API | 任意 |
| Java | >= 21 |
| 可选：Cloth Config | >= 21.11.0 |
| 可选：ModMenu | >= 17.0.0 |

> 模组仅需在客户端安装。服务器无需安装，也不会向服务器发送任何额外数据。

---

## 安装

1. 安装 [Fabric Loader](https://fabricmc.net/use/)。
2. 将 [Fabric API](https://modrinth.com/mod/fabric-api) 放入 `mods` 文件夹。
3. 从 [Releases](https://github.com/HGfds29/ChunkMap/releases) 下载最新的 `chunkmap-x.x.x.jar`，放入 `mods` 文件夹。
4. （推荐）安装 [ModMenu](https://modrinth.com/mod/modmenu) 与 [Cloth Config](https://modrinth.com/mod/cloth-config) 以使用图形化配置界面。

---

## 使用

| 操作 | 说明 |
| --- | --- |
| `M` | 打开区块地图 |
| `Alt + M` | 打开配置界面 |
| 滚轮 | 缩放地图 |
| 拖拽 | 平移地图 |
| `E` | 导出当前维度瓦片为单张 PNG |
| `C` | 地图视图回到玩家位置 |
| `Z` | 缩放复位至 1:1 |
| `R` | 重载配置并刷新缓存 |
| `ESC` | 关闭界面 |

---

## 配置

配置文件位于 `.minecraft/config/chunkmap.json`，首次启动时自动生成。

| 字段 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `tileResolution` | int | `32` | 瓦片边长（像素），可选 16 / 32 / 64 |
| `outputDir` | string | `chunkmap-output` | 瓦片与导出图的相对输出目录 |
| `colorMode` | enum | `MAP_COLOR` | 颜色模式：`MAP_COLOR` / `TEXTURE_AVERAGE` |
| `shadeByHeight` | bool | `true` | 是否根据高度施加明暗 |
| `uiAnimation` | bool | `true` | 是否启用 UI 过渡动画 |
| `deleteOnUnload` | bool | `false` | 区块卸载时是否删除对应瓦片文件 |
| `renderThreads` | int | `2` | 渲染工作线程数，范围 1 - 8 |
| `logRetentionDays` | int | `7` | 日志保留天数，`0` 表示永久保留 |
| `githubToken` | string | `""` | 用于游戏内提交 Issue，可留空手动提交 |

---

## 输出结构

```
chunkmap-output/
├── minecraft/
│   ├── overworld/
│   │   ├── 0_0.png
│   │   ├── 1_0.png
│   │   └── ...
│   ├── the_nether/
│   └── the_end/
└── stitched/
    └── overworld_20260101_120000.png
```

- 瓦片文件命名格式为 `<chunkX>_<chunkZ>.png`，坐标与 Minecraft 区块坐标一致。
- `stitched` 目录存放导出的拼接大图，文件名附带时间戳。

---

## 从源码构建

```bash
git clone https://github.com/HGfds29/ChunkMap.git
cd ChunkMap
./gradlew build
```

构建产物位于 `build/libs/` 目录下。

开发环境运行：

```bash
./gradlew runClient
```

---

## 项目结构

```
src/main/java/com/geek/chunkmap/
├── ChunkMapMod.java            模组入口
├── config/                     配置加载与数据模型
├── event/                      客户端事件与脏区块追踪
├── mixin/                      Mixin 注入
├── render/                     快照、调色板与俯视渲染器
├── tile/                       瓦片缓存、调度、存储与拼接
├── ui/                         地图、配置、反馈与日志界面
└── util/                       日志工具
```

---

## 反馈与贡献

- Bug 报告或功能建议请通过 [Issues](https://github.com/HGfds29/ChunkMap/issues) 提交。
- 游戏内可在反馈界面一键提交，自动附带版本、日志、系统信息与模组列表。
- 欢迎提交 Pull Request，请保持代码风格一致并附带必要的说明。

---

## 许可证

本项目使用 [MIT License](https://github.com/HGfds29/ChunkMap/blob/main/LICENSE) 授权。

---

## 致谢

- [Fabric](https://fabricmc.net/) 提供模组加载框架
- [Cloth Config](https://github.com/shedaniel/cloth-config) 与 [ModMenu](https://github.com/TerraformersMC/ModMenu) 提供配置界面支持