# morph-compose

[![Maven Central](https://img.shields.io/maven-central/v/li.songe.morph/morph-compose.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/li.songe.morph/morph-compose)

[English](README.md) | 简体中文

在线演示：[morph-compose.pages.dev](https://morph-compose.pages.dev)

一个用于图标和自定义路径变形动画的 Compose Multiplatform 库，支持 Android、JVM 和 Kotlin/Wasm。

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

支持纯色填充路径、嵌套变换、多轮廓和孔洞，不支持裁剪路径、路径修剪、渐变填充或仅有描边的路径。
可以用 `inspectMorphCompatibility(from, to)` 在播放动画前检查图标。

状态控制、变形计划复用和插值算法见 [API 与算法文档](docs/architecture.md#api-layers)。
演示应用和开发配置见[项目文档](docs/README.md)。
