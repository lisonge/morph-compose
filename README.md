# morph-compose

[![Maven Central](https://img.shields.io/maven-central/v/li.songe.morph/morph-compose.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/li.songe.morph/morph-compose)

English | [简体中文](README.zh.md)

Live demo: [morph-compose.pages.dev](https://morph-compose.pages.dev)

A Compose Multiplatform library for morphing icons and custom paths. Supports Android, JVM, and Kotlin/Wasm.

## Features

- Morph icons and custom paths, including shapes with multiple contours and holes.
- Animate when the target changes, or control progress yourself.
- Continue from the current shape when an animation is interrupted.
- Use animated shapes for backgrounds, borders, and image clipping.
- Follow system animation settings and handle RTL layouts automatically.

## Installation

Add the dependency to `commonMain` and replace `<latest>` with the version shown in the badge above:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("li.songe.morph:morph-compose:<latest>")
        }
    }
}
```

## Icons

Pass an `ImageVector` to `AnimatedMorphIcon`. When the icon changes, it animates from the current shape to the new one:

```kotlin
@Composable
fun NavigationIcon(icon: ImageVector) {
    AnimatedMorphIcon(
        imageVector = icon,
        animationSpec = tween(durationMillis = 300),
    )
}
```

To switch between two icons, pass a Boolean:

```kotlin
AnimatedMorphIcon(
    from = Icons.Filled.PlayArrow,
    to = Icons.Filled.Pause,
    targetState = playing,
    contentDescription = if (playing) "Pause" else "Play",
)
```

To control progress yourself, use `MorphIcon`. `0` shows the source icon and `1` shows the target:

```kotlin
MorphIcon(
    from = Icons.Filled.Menu,
    to = Icons.Filled.Close,
    progress = progress,
    contentDescription = "Menu",
)
```

The icon APIs are in `li.songe.morph.compose`. These examples use Material icons, but you can use your own `ImageVector`.
Animations follow the system animation duration scale by default.

To prefer the same rotation direction on both legs of a transition, supply a rotation preference:

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

`Auto` preserves the existing behavior. `PreferClockwise` and `PreferCounterClockwise` choose among
similarly clean correspondences in displayed coordinates, including RTL mirroring. If no suitable
candidate exists, the automatic result is retained without adding a full turn. Build each direction
separately: running one plan from progress `1` to `0` still reverses that plan. `AnimatedMorphIcon`
already handles this. The preference targets Polar interpolation; Linear shares the correspondence
but does not guarantee a rotation direction. Filled outlines are not automatically decomposed into
strokes, so this does not reproduce a hand-authored three-line animation exactly. As with the existing
algorithm, sampled quality checks do not guarantee intersection-free geometry at every intermediate frame.

## Custom paths

Use `morphGeometryOf` with a filled Compose `Path` or `List<PathNode>`. Paths can contain multiple contours and holes.
This example morphs a ring into a solid circle, closing the hole as progress goes from `0` to `1`:

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

Nested contours are treated as alternating filled regions and holes. The resulting shape works with `background`, `border`, and `clip`.

## Digits

Convert digit outlines to `MorphGeometry` with `morphGeometryOf`. Pass outlines for `0` through `9` in order, using the same viewport and baseline.
The example below animates when `digit` changes:

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

You supply the outlines; font loading is not part of the library. The playground includes [sample digit paths](morph-playground/src/commonMain/kotlin/li/songe/morph/playground/DigitOutlines.kt).
For transitions such as `99 → 100`, see the [whole-number example](morph-playground/src/commonMain/kotlin/li/songe/morph/playground/NumberMorph.kt), which aligns digits by place value and fades added or removed digits.

## Supported input

Supports solid fill paths, nested transforms, multiple contours, and holes.
Clip paths, trim paths, gradient fills, and stroke-only paths are not supported.
Use `inspectMorphCompatibility(from, to)` to check icons before animating them.

See the [API and algorithm documentation](docs/architecture.md#api-layers) for state controllers, plan reuse, and interpolation details.
The [project docs](docs/README.md) cover the playground and development setup.
