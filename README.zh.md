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
Playground 的 Search、Close、Menu、Add、Remove、Check 和四个方向箭头已使用这种数据。
默认 `Auto` 在两端均为描边时使用中心线；只要存在填充图形，就把描边展开并合并重叠部分，统一按填充轮廓变形。因此 Close ↔ Play 等跨类型组合也能连续过渡。不会从 Material 填充图标反推笔画。`MorphGeometry` 和形状裁剪仍使用填充几何。

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

## 灵感来源

本项目的灵感来自 [morphicons](https://github.com/guillermolg00/morphicons)。
