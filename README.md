# Loading More Files

> 让 Minecraft 读懂更多文件格式 —— 资源包、数据包和附属模组不再被迫转码。

**Loading More Files** 是一个 Forge 模组，目标是扩展 Minecraft 的资源加载管线，使游戏能直接读取原版不支持的文件格式。原版只接受极少数几种编码（音频仅 Ogg Vorbis、纹理仅 PNG、模型与数据一律 JSON），任何其他来源的素材都必须先手动转换；本模组希望把这一步省掉。

- 模组 ID：`loadingmorefiles`
- 依赖：Minecraft 1.20.1 · Forge 47.x · Java 17
- 许可证：MIT

---

## 目录

- [它解决什么问题](#它解决什么问题)
- [文件格式兼容指南](docs/format-compatibility.md)
- [当前状态](#当前状态)
- [环境要求](#环境要求)
- [安装](#安装)
- [从源码构建](#从源码构建)
- [项目结构](#项目结构)
- [设计思路](#设计思路)
- [兼容性](#兼容性)
- [许可证](#许可证)

---

## 它解决什么问题

Minecraft 的资源系统对文件格式极其挑剔：

| 场景 | 原版的限制 | 实际后果 |
| --- | --- | --- |
| 音效 | `sounds.json` 只解析 Ogg Vorbis | FFmpeg 导出的 `.wav` / `.mp3` / `.flac` 必须逐个转码 |
| 纹理 | 只加载 `.png` | `.jpg`、`.webp`、`.tga` 素材要先用图像工具另存 |
| 模型 / 方块状态 | 仅 `.json` | 导出器产出的其他描述格式无法直接使用 |
| 数据包内容 | 仅 `.json` | 部分生成工具的额外编码需要手工转换 |

对玩家和整合包作者来说，这意味着「下载 → 转换 → 重命名 → 再打包」的重复劳动，而且转码过程很容易引入画质、音量或元数据损失。

Loading More Files 的做法是在**资源加载的入口**做一次格式适配：检测到不认识的扩展名时先解码，再交给原版后续流程处理，因此上层逻辑（音效系统、纹理管理器、模型烘焙）无需感知格式差异。

## 当前状态

目前已支持 WAV、MP3 和 FLAC 音效与流式音乐，以及 JPEG、WebP、GIF 和 TGA 纹理。动态图集精灵可直接使用 GIF 的帧顺序和时长。各格式的支持进度、资源包示例与配置见[文件格式兼容指南](docs/format-compatibility.md)。

- Forge 模组注册（`LoadingMoreFiles` 入口类）与公共配置项；
- 格式别名注册表（`format/`），后续音频、图像格式都从这里接入；
- Mixin 配置与 refmap 接入；
- Gradle 构建、数据生成与运行配置。

## 环境要求

| 项目 | 版本 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 及以上（47.x 系列） |
| Java（运行） | 17 |
| Gradle | 9.8.2（随 `gradlew` 提供） |

> 编译期由 `build.gradle` 指定的 JDK 25 工具链完成，`options.release = 17` 保证产物字节码兼容 Java 17。若本机没有 JDK 25，Gradle 会通过 foojay 工具链解析器自动下载。

## 安装

1. 安装 Forge 1.20.1（47.x）。
2. 把 `loadingmorefiles-<version>.jar` 放进 `.minecraft/mods`。
3. 仅客户端需要的格式支持（纹理、字体等）也包含在同一 jar 内，无需拆分安装。

## 从源码构建

```bash
# 构建
./gradlew build

# 产物
build/libs/loadingmorefiles-0.1.jar

# 启动开发客户端 / 服务端
./gradlew runClient
./gradlew runServer
```

Windows 下把 `./gradlew` 换成 `gradlew.bat` 即可。

## 项目结构

```
Loading-more-files/
├── build.gradle                     # 模组元数据、运行配置、Mixin 与 processResources 展开
├── settings.gradle                  # 根工程名
├── gradle.properties                # mod_id / mod_name / 版本等被构建脚本引用
├── src/main/java/com/mpp/loadingmorefiles/
│   ├── LoadingMoreFiles.java        # @Mod 入口：注册配置与内置格式
│   ├── config/LmfConfig.java        # 公共配置（格式别名开关、日志开关）
│   ├── format/FormatAlias.java      # 一个「别名扩展名 → 原版扩展名」的声明
│   ├── format/ResourceFormats.java  # 别名注册表：请求 → 候选文件，文件 → 原版名字
│   └── mixin/
│       ├── FallbackResourceManagerMixin.java          # 查找 / 列举时的别名处理
│       ├── FallbackResourceManagerAccessor.java       # 读取包类型与包层级顺序
│       └── FallbackResourceManagerPackEntryAccessor.java # 读取单个包层级的内容
├── src/main/resources/
│   ├── META-INF/mods.toml           # 模组声明（占位符由 gradle.properties 填充）
│   ├── loadingmorefiles.mixins.json # Mixin 配置（refmap: loadingmorefiles.refmap.json）
│   └── pack.mcmeta                  # 资源包元数据
└── run/ , run-data/                 # 开发环境生成目录（已忽略）
```

新增格式支持时，预期改动集中在这几处：

- 在 `LoadingMoreFiles` 构造函数里调用新的注册方法（沿用 `format/ResourceFormats`）；
- 需要字节级转码的格式（音频）新增 `audio/` 等包，并在 `ResourceFormats` 里挂接解码；
- 若介入点不同，在 `loadingmorefiles.mixins.json` 注册新的 Mixin 类。

## 设计思路

在三个位置介入文件读取：

1. **资源定位阶段（已实现）** —— 资源管理器查找或列举 `assets/<ns>/…` 时解析扩展名别名，保证图集和单张纹理行为一致。
2. **字节读取阶段** —— 打开资源时，按需将内容解码为原版加载器可读取的数据流。
3. **缓存阶段（计划中）** —— 解码结果按资源哈希缓存，避免反复转码带来的启动开销。

格式将实现为可注册的 `FormatAlias` 体系（`format/ResourceFormats`），方便其他模组按同样机制扩展，而不必各自再写一套 Mixin。

## 兼容性

- 本模组在 `FallbackResourceManager` 的查找与列举阶段解析别名，在客户端 `SpriteLoader` 读取 GIF 精灵时提供动画帧条和元数据。
- 与优化类模组（如 Sodium、ModernFix 等）同时安装时，纹理与音效路径上可能出现行为差异，建议先只装本模组定位问题；若出现异常，可先把 `enable_format_aliases` 设为 `false` 确认是否与本模组有关。
- 服务端与客户端行为可能不同：纹理别名只在客户端真正生效；服务端即使加载了同样的资源包也不会读取 `assets/`，因此没有额外开销。

## 许可证

本项目基于 [MIT License](LICENSE) 发布，版权归 Magic power plant 所有。

---

## English

**Loading More Files** is a Forge mod for Minecraft 1.20.1 that teaches the game to read file formats vanilla refuses.

The mod supports WAV, MP3 and FLAC sounds and streaming music, plus JPEG, WebP, GIF and TGA textures, including animated GIF atlas sprites. For usage and planned formats, see the [format compatibility guide](docs/format-compatibility.md#english).

See [LICENSE](LICENSE) (MIT).
