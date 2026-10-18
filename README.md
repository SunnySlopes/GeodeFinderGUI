# Amethyst Geode Finder

A GUI for searching amethyst geode clusters in Minecraft Java Edition.

用于在 Minecraft Java 版中搜索紫水晶晶洞簇的图形工具。

**Java 17 or higher is required.**

**此程序需要 Java 17 或更高版本。**

Right-click the `GeodeFinder` jar, choose **Open with**, and select **Java 17+**.

右键 `GeodeFinder` 的 jar，选择**打开方式**，并使用 **Java 17 及以上版本**启动。

## How to Use / 如何使用

There are two tabs: **Single Seed Search** and **Search from Seed List**. Both can pause, resume, and stop. Results can be sorted and exported. The seed-list tab can also export only the seeds that produced hits.

程序有两个标签页：**单种子搜索**和**从种子列表搜索**。两者都可以暂停、继续和停止，结果可以排序和导出。种子列表页还可以只导出有结果的种子。

The search rectangle is a closed block range. Each edge is converted with `chunk = floor(block / 16)`. The single-seed default is X/Z `-16384~16383` (2048×2048 chunks). The seed-list default stays `-4096~4095`. With **Square Area Side Length** checked, the box is centered on 0: `min = -side / 2`, `max = min + side - 1`.

搜索范围是闭区间方块坐标，每一侧按 `chunk = floor(block / 16)` 换成区块。单种子默认 X/Z 为 `-16384~16383`（2048×2048 个区块）。种子列表默认仍是 `-4096~4095`。勾选**正方形区域边长**时，范围以 0 为中心：`min = -side / 2`，`max = min + side - 1`。

### Parameters / 参数

- Version: `1.17.x` / `1.18.0~1.18.1` / `1.18.2~1.19.3` / `1.19.4~26.2` / `26.3+` (default `26.3+`)
- Geode threshold, default 20
- Budding amethyst threshold, default 800
- Random tick load radius in chunks, default 6, range 0–64

- 版本：`1.17.x` / `1.18.0~1.18.1` / `1.18.2~1.19.3` / `1.19.4~26.2` / `26.3+`（默认 `26.3+`）
- 晶洞阈值，默认 20
- 紫水晶芽阈值，默认 800
- 随机刻加载半径（区块），默认 6，范围 0–64

### Search Modes / 三种搜索

The two checkboxes are off by default and cannot be on together.

两个勾选框默认都不勾选，不能同时勾选。

- Neither checked: every cluster whose geode count meets the threshold gets an exact budding count.
- **Count structures only**: stop after counting geodes in each loaded window. The budding field is disabled. A result looks like `/tp x 64 z geodes=23`.
- **Fast search**: keep the budding field editable. After the full geode scan, only clusters where `geode count × 36` reaches the budding threshold are simulated. The 36 is the empirical average budding per geode. With the default 800, that means only clusters with at least 23 geodes. This can miss a cluster that has fewer geodes but unusually rich budding. A result looks like `/tp x 64 z budding=911 geodes=23`.

- 都不勾选：每个达到晶洞阈值的簇都会做精确芽计算。
- **仅计算结构数**：只统计每个加载窗口里的晶洞个数，不计算紫水晶芽。芽阈值输入框会灰掉。结果形如 `/tp x 64 z geodes=23`。
- **快速搜索**：芽阈值保持可编辑。全图统计晶洞后，只对 `晶洞数 × 36` 达到芽阈值的簇做精确计算。36 是单洞芽数的经验平均数。默认芽阈值 800 时，相当于只精算至少 23 个晶洞的簇，可能漏掉晶洞更少但单洞芽很多的结果。结果形如 `/tp x 64 z budding=911 geodes=23`。

`Y=64` is only a teleport height. Full and fast results sort by budding, then distance. Structure-only results sort by geode count, then distance.

`Y=64` 只是传送高度。完整搜索和快速搜索按芽数排序，其次按距离。仅计算结构数按晶洞数排序，其次按距离。

## Build / 编译

On Windows, `jni/build-jni.bat` builds `libGeodeFinderLibJ.dll` and runs the C++ golden tests. `gradlew buildMainJar` then packs `GeodeFinder.jar`.

在 Windows 上，`jni/build-jni.bat` 会编译 `libGeodeFinderLibJ.dll` 并运行 C++ 黄金测试。随后 `gradlew buildMainJar` 打包出 `GeodeFinder.jar`。
