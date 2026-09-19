# ChunkMap

> 客户端侧 Minecraft Fabric 模组：实时把周围区块渲染成一张俯视地图。

[![GitHub](https://img.shields.io/badge/GitHub-HGfds29%2FChunkMap-181717?logo=github)](https://github.com/HGfds29/ChunkMap)
[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11-62B47A)](https://www.minecraft.net/)
[![Fabric](https://img.shields.io/badge/Fabric-0.18.0%2B-DBB69C)](https://fabricmc.net/)

## ✨ 特性

- 🗺️ 实时俯视地图：`M` 键随时开图，`Alt + M` 打开配置
- 🎨 双颜色模式：原版地图色 / 纹理平均色
- ⛰️ 方向性光照：山脊亮、坡谷暗，可选绝对高度明暗
- 💾 自动导出：每个区块实时导出 PNG 瓦片，可一键拼接大图
- 📋 内置反馈与日志面板：地图右上角 `反馈` / `日志` 两个按钮
- ⭐ 一键 Star：地图右上角金色 `★ Star` 按钮直达仓库
- ⚙️ 全部配置热重载：改完按 `R` 立刻生效
- 🔧 分辨率三档可选：`16×16` / `32×32` / `64×64`

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
| `Z`（地图内） | 缩放重置 1:1 |
| `R`（地图内） | 热重载配置 + 重渲染 |
| `C`（日志内） | 复制全部日志 |
| `R` / `F5`（日志内） | 刷新日志 |

## ⚙️ 配置

配置文件：`config/chunkmap.json`

| 字段 | 说明 |
|---|---|
| `tileResolution` | `16` / `32` / `64`，像素边长 |
| `outputDir` | 瓦片输出根目录 |
| `colorMode` | `MAP_COLOR` / `TEXTURE_AVERAGE` |
| `shadeByHeight` | 是否施加高度明暗 |
| `uiAnimation` | UI 平滑动画开关 |
| `deleteOnUnload` | 卸载时删除瓦片 |
| `renderThreads` | 渲染线程数（1~8） |
| `logRetentionDays` | 日志保留天数（0 = 永久） |
| `githubToken` | 反馈提交用 PAT（可选，覆盖内置 DevToken） |

## 📝 反馈

- 地图内点 **反馈** → 输入内容 → 提交到 GitHub Issues
- 每日一次限制：24 小时内不能重复提交
- 无 token 时点 **打开 Issues** 会复制内容到剪贴板并打开网页，手动粘贴即可
- **日志** 按钮打开只读日志查看器，可按级别着色、复制全部

### 关于 githubToken

模组在 `DevToken.java` 里内置了一个默认 token（用于开箱即用）。如果要换成你自己的：

1. 去 [github.com/settings/tokens](https://github.com/settings/tokens) 生成 fine-grained token，权限只需 **Issues: Read and Write**
2. 打开 `Alt + M` → **反馈** 分类，粘贴到 `GitHub Token` 字段
3. 或直接改 `config/chunkmap.json` 的 `githubToken`

⚠ **内置 token 是可被反编译 jar 提取的**。它权限很低（仅能发 issue），但请定期轮换；如果发现滥用，立即到 GitHub 撤销。

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

## 📄 License

MIT