# morph-compose

[![Maven Central](https://img.shields.io/maven-central/v/li.songe.morph/morph-compose.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/li.songe.morph/morph-compose)

[English](README.md) | 简体中文

在线演示：[morph-compose.pages.dev](https://morph-compose.pages.dev)

一个用于图标和自定义路径变形动画的 Compose Multiplatform 库，支持 Android、JVM 和 Kotlin/Wasm。

<img width="1900" height="480" alt="Image" src="https://camo.githubusercontent.com/a80bb59affd89d0bdd7cb8d9572fdb44eaeba1582a9ecb08a1698e21be10a812/68747470733a2f2f652e676b642e6c692f30613266353963372d653664652d343831632d396434332d646239396433643664323234" />

## 功能

- 支持图标和自定义路径变形，包括多轮廓和带孔洞的形状。
- 目标变化时自动播放动画，也可以手动控制进度。
- 动画中途切换目标时，从当前形状继续过渡。
- 动画形状可用于背景、边框和图片裁剪。
- 遵循系统动画设置，自动适配 RTL 布局。

## 安装

在 `commonMain` 中添加依赖，将 `<latest>` 替换为上方徽章显示的版本：

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("li.songe.morph:morph-compose:<latest>")
        }
    }
}
```

## 图标

将 `ImageVector` 传给 `AnimatedMorphIcon`，图标变化时会从当前形状过渡到新图标：

```kotlin
@Composable
fun NavigationIcon(icon: ImageVector) {
    AnimatedMorphIcon(
        imageVector = icon,
        animationSpec = tween(durationMillis = 300),
    )
}
```

在两个图标之间切换时，用布尔值控制：

```kotlin
AnimatedMorphIcon(
    from = Icons.Filled.PlayArrow,
    to = Icons.Filled.Pause,
    targetState = playing,
    contentDescription = if (playing) "Pause" else "Play",
)
```

需要自己控制进度时，用 `MorphIcon`。`0` 为起始图标，`1` 为目标图标：

```kotlin
MorphIcon(
    from = Icons.Filled.Menu,
    to = Icons.Filled.Close,
    progress = progress,
    contentDescription = "Menu",
)
```

图标 API 位于 `li.songe.morph.compose` 包中。示例使用 Material 图标，也可以换成自己的 `ImageVector`。
动画默认遵循系统的动画时长缩放设置。

需要让往返切换优先沿同一方向旋转时，传入方向偏好：

```kotlin
AnimatedMorphIcon(
    from = Icons.AutoMirrored.Filled.ArrowBack,
    to = Icons.Filled.Close,
    targetState = closed,
    options = MorphOptions(
        rotationPreference = MorphRotationPreference.PreferCounterClockwise,
    ),
)
```

`Auto` 保持原有行为；`PreferClockwise` 和 `PreferCounterClockwise` 在几何质量接近的对应关系中选择旋转方向。
方向按最终显示坐标定义，包括 RTL 镜像后的坐标。没有合适候选时保留自动结果，不强制绕圈。
两个方向需要分别生成计划；同一个计划的进度从 `1` 回到 `0` 仍是倒放，`AnimatedMorphIcon` 会自动处理双向规划。
此配置适合 Polar 插值；Linear 会使用所选对应关系，但不保证旋转方向。它不会把填充轮廓自动拆成笔画，
因此不保证精确复现手写的三线动画。与已有算法一样，离散帧检查不保证所有中间帧都没有边界自交。

## 自定义路径

用 `morphGeometryOf` 接入填充的 Compose `Path` 或 `List<PathNode>`，支持多轮廓和孔洞。
下面把圆环变成实心圆，随着进度从 `0` 变到 `1`，中间的孔洞逐渐闭合：

```kotlin
val ring = remember {
    morphGeometryOf(
        Path().apply {
            addOval(Rect(0f, 0f, 100f, 100f))
            addOval(Rect(30f, 30f, 70f, 70f))
        },
        viewportSize = Size(100f, 100f),
    )
}
val disc = remember {
    morphGeometryOf(Path().apply { addOval(Rect(0f, 0f, 100f, 100f)) }, Size(100f, 100f))
}
val plan = rememberMorphPlan(ring, disc)
val shape = rememberMorphShape(plan, progress)
Box(Modifier.size(160.dp).background(Color.Blue, shape))
```

嵌套轮廓按层级交替作为填充区域和孔洞。生成的形状可用于 `background`、`border` 和 `clip`。

## 数字

用 `morphGeometryOf` 将数字轮廓转为 `MorphGeometry`，按 `0` 到 `9` 排列，并使用相同的视口和基线。
下面的示例会在 `digit` 变化时播放变形动画：

```kotlin
@Composable
fun MorphingDigit(digit: Int, digits: List<MorphGeometry>) {
    require(digit in 0..9 && digits.size == 10)
    val target = digits[digit]
    val state = rememberMorphGeometryState(target)

    LaunchedEffect(state, target) {
        state.animateTo(target, animationSpec = tween(300))
    }

    Box(Modifier.size(64.dp).background(Color.Black, rememberMorphShape(state)))
}
```

轮廓数据需要自行准备，库不负责加载字体，可以参考演示应用的[数字路径数据](morph-playground/src/commonMain/kotlin/li/songe/morph/playground/DigitOutlines.kt)。
`99 → 100` 这类变化见[多位数示例](morph-playground/src/commonMain/kotlin/li/songe/morph/playground/NumberMorph.kt)：按数位对齐，新增或消失的数字使用淡入淡出。

## 支持的输入

支持纯色填充路径和纯色描边路径、嵌套变换、多轮廓和孔洞。不支持裁剪路径、路径修剪、渐变、同一路径同时填充和描边，以及描边路径的非等比变换。

线条图标建议使用 `fill = null`、`stroke = SolidColor(...)` 的 `ImageVector`，用 `moveTo` 分隔独立笔画。
动画对中心线进行匹配和变换，保持描边宽度；笔画数量不同时通过分裂／合并过渡。
Playground 使用原版 Material 图标，不将填充图标替换成自定义描边版本。

可选 `MorphTransitionMode.ExperimentalStrokeInference`（示例中的 **Infer strokes (experimental)**）
会从具有 2–6 个明确平头端部的单个填充多边形推断直线笔画，支持交叉线及带横杆的折线结构。
输入仍是原版图标，不按图标名称选择算法。候选重建必须仍为单轮廓，双向多边形边界误差不超过线宽的 0.6%，
新增与缺失区域的面积总和不超过原面积的 0.3%，不能用面积相互抵消；边界检查覆盖整条边。
每端最多保留 4 种分解，结合目标比较交汇处分离、拉伸、额外自交及对应残差；旋转偏好只打破质量接近的候选平局。
规划详情显示具体误差、候选选择和运动评分。静止端点绘制原图，中间帧仍使用近似重建，保留平头和尖角连接。
曲线、孔洞、多块填充、不明确的宽度及通用骨架推断不在首版范围；不支持或输入相同时回退到 Auto 行为。
当前仅支持 `ImageVector` / `MorphIcon`，不支持 `MorphGeometry` / 裁剪。默认 Auto 不变，规划详情会说明识别或回退。

默认 `Auto` 在两端均为描边时使用中心线；只要存在填充图形，就把描边展开并合并重叠部分，统一按填充轮廓变形。因此描边与填充的跨类型组合也能连续过渡。不会从 Material 填充图标反推笔画。`MorphGeometry` 和形状裁剪仍使用填充几何。

```kotlin
val options = MorphOptions(
    transitionMode = MorphTransitionMode.Auto, // Outline 强制填充轮廓；Centerline 要求两端均为描边
    strokeCountStrategy = MorphStrokeCountStrategy.SplitMerge, // Collapse 改为收缩／展开
    contourStrategy = MorphContourStrategy.SharedBoundary, // Standard 为普通基线；ExperimentalHoleOpening 显式启用实验孔洞连接
    outlineTolerance = 0.0001, // 描边展开误差，单位是归一化视口；越小越精细
    sampleCount = 64,
    rotationPreference = MorphRotationPreference.Auto,
)
```

描边展开保留线宽、端帽和连接方式，并通过路径并集合并交叠区域。动画中断后也按同一策略重新规划；从中心线切换为轮廓时，会在指定误差内转换当前画面。`Centerline` 对填充输入明确报错。笔画数量策略只作用于中心线模式，填充轮廓仍使用保持孔洞和绕向的匹配方式。专门编排的裁剪时序不属于自动策略。
可以用 `inspectMorphCompatibility(from, to)` 在播放动画前检查图标。
报告包含实际策略、输入轮廓对应、公共边界约束和回退原因。整体算法改动通过
`./gradlew.bat verifyMorph` 验证；策略边界、视觉基线与审阅流程见[质量控制](docs/quality.md)。

状态控制、变形计划复用和插值算法见 [API 与算法文档](docs/architecture.md#api-layers)。
演示应用和开发配置见[项目文档](docs/README.md)。

## 选择效果与 AI 辅助描边重建

不同图标组合适合不同的配置，由使用者选择满意的效果；某个组合在某种配置下不好看，
不必然代表算法缺陷。可以先比较轮廓策略、旋转偏好和插值方式；对于简单线条图标，
也可以尝试 Infer strokes，例如原版 Close ↔ Arrow back。

如果现有配置都无法得到满意的动画，可以让 AI 将填充图标重建为显式描边图标。
明确的笔画结构有助于控制对应与运动，但不能保证任意填充图形都能用描边完全等价地表达。
需要人工核对外观，以及正向、反向动画。下面的提示语可以直接复制：

```text
请将下面的填充图标重新实现为基于 stroke 的 Compose ImageVector，用于图标变形动画。

要求：
- 尽量保持原图的视口、尺寸、位置、视觉重量、端部和转角特征。
- 使用明确的中心线与描边表达笔画，不要只是给原填充轮廓加描边。
- 使用 fill = null、stroke = SolidColor(...)；用 moveTo 分隔独立笔画。
- 优先使用少量、结构清晰的路径，避免不必要的折点和重复路径。
- 根据原图选择 strokeLineWidth、strokeLineCap、strokeLineJoin 和 strokeLineMiter。
- 如果提供了目标图标，也请检查目标是否为纯描边；需要时一并重建，使两端都能使用中心线变形。
- 结合两端结构设计合理的笔画对应，但不要为了凑相同笔画数量而明显改变外观。
- 不使用裁剪、路径修剪、渐变、同一路径同时填充和描边，或描边路径的非等比变换。
- 输出可用的 Kotlin 代码，以及使用 MorphIcon 和 MorphTransitionMode.Centerline 的动画示例。
- 提供原版与重建版的静态对照和双向动画，说明近似之处，不宣称未经验证的像素级等价。
- 将重建图标单独命名（例如 CloseStroke），保留原版，便于比较和撤回。

源图标：
[粘贴 ImageVector 代码或 SVG]

目标图标（可选）：
[粘贴 ImageVector 代码或 SVG]
```

两端均为纯描边时，`Auto` 会使用中心线，也可以显式选择 `Centerline`。
若另一端仍是填充图标，`Auto` 会展开描边后按轮廓变形，`Centerline` 则会报错。
笔画数量不同时可比较 `SplitMerge` 与 `Collapse`，选择适合的表现。
这是使用者主动选择的图标重设计，库不会自动替换原版；示例中的重建版本也应单独标注来源和用途。

## 用 AI 生成独立过渡动画（无需本库）

对于固定的图标对，如果希望精确控制笔画如何运动，可以直接让 AI 编写专用动画，
不需要依赖 morph-compose，也不必先尝试所有库配置。适合少量关键交互；
运行时任意图标之间的自动过渡仍是本库的主要用途。
这条路线可以自由使用路径裁剪、分段时序和填充绘制，不受上一节描边重建提示词的限制。

复制下面的提示词，附上两个原始图标的代码，并填写期望的运动效果：

```text
请为下面两个图标编写独立的 Kotlin/Compose 过渡动画，不依赖 morph-compose 或其他变形库。
使用 Compose 自带的动画与绘图 API，输出完整代码和可点击切换的使用示例。

要求：
- 以提供的原始代码为端点依据，保留视口、尺寸、位置、线宽、端帽、转角、填充和孔洞。
  不得为了方便动画擅自加圆角、改粗细或重设计图标；不能精确保留时，先说明限制和替代方案。
- 先简要说明哪些部分保留、移动、旋转、收缩或生长，再实现。优先保留公共结构，
  避免无意义的整体旋转、收缩和额外折角。不要把交叉淡入淡出作为默认方案。
- 可以按效果需要使用 Canvas、Path、ImageVector、路径裁剪及局部时间区间；
  不要求将所有填充图形转换为描边，也不要求两端路径数量相同。
- 用一个连续进度 p 描述几何：p=0 为源图标，p=1 为目标图标。
  将进度驱动的绘制与状态动画分离，便于手动查看任意中间帧。
- 支持动画中途连续点击并反向，从当前进度继续，不重置到端点；
  不要根据目标布尔值突然切换整套几何公式。说明是否保证速度连续。
- 支持 modifier、tint 和 contentDescription；缩放时保持纵横比，并在可用区域居中。
- 精确端点与邻近帧都应连续，不能仅在 p=0/1 绘制原图来掩盖中间几何不匹配。
  特别检查短笔画的端帽、路径出生/消失和圆弧拼接处，避免闪点、跳变与接缝。
- 避免不必要的逐帧对象分配，但不要以性能优化为由改变外观或增加无关框架。
- 提供原图与动画端点的对照、0/25/50/75/100% 帧，以及双向切换和中途反转示例。
  有运行环境时实际验证；无法运行时明确列出未验证项，不宣称像素一致或测试通过。

源图标：
[粘贴完整 ImageVector 代码或 SVG]

目标图标：
[粘贴完整 ImageVector 代码或 SVG]

期望运动：
[例如：公共斜线保留，另一笔向中心收缩，圆环从指定位置逐渐长出；或让 AI 提出方案]

动画时长与缓动：
[例如：300ms，使用 Compose 默认 tween 缓动]

项目环境：
[Compose 平台、版本，以及现有 Material/Material3 依赖]
```

AI 生成的结果仍需要检查，尤其是端点外观与快速反向切换。
固定图标对的手写动画不自动具备途中切换到任意第三个图标的能力。

## 灵感来源

本项目的灵感来自 [morphicons](https://github.com/guillermolg00/morphicons)。
