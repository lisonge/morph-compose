# morph-compose

`morph-compose` is a filled-shape morphing library for Compose Multiplatform. It transforms
icons and custom paths while preserving rotations, scaling, and multi-contour shapes.
Use it for animated icons, component outlines, image clips, selection feedback, decorative
motion, and glyph outlines. The playground's Typography tab includes a searchable system-font
selector; see [font support and browser requirements](docs/playground.md#typography-fonts).

The `morph-compose` artifact supports Android, JVM, and Kotlin/Wasm. It provides controlled and
animated Compose APIs without requiring a specific icon pack.

## Features

- Polar interpolation with similarity-transform decomposition.
- Arc-length resampling with weighted corner preservation.
- Fold-aware contour alignment that penalizes self-intersecting intermediate boundaries when a
  cleaner correspondence is available.
- Winding-safe one-to-one contour matching for filled shapes and holes, including topology changes.
- Per-contour linear fallback for degenerate Polar fits.
- Non-throwing compatibility reports with per-contour fallback reasons.
- Interruption-safe state for morphing through arbitrary icon sequences.
- `Always`, `System`, and `Never` motion policies; system accessibility settings are honored by default.
- Exact rendering of the source and destination `ImageVector` at progress `0` and `1`.
- Spring overshoot support; progress values are not restricted to `0..1`.
- Reusable morph plans and frame buffers for animation-heavy interfaces.
- Automatic RTL handling through the current Compose layout direction.
- Optional linear interpolation for comparison and diagnostics.

## Dependency

`morph-compose` is published to Maven Central. Add it to a Kotlin Multiplatform project:

```kotlin
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation("li.songe.morph:morph-compose:0.1.0")
        }
    }
}
```

`morph-compose` includes its geometry engine and required Compose APIs. The library accepts solid,
fill-only `ImageVector` paths and does not require a Material icon-pack dependency. The Material
icons below are used only to keep the examples familiar.

## Controlled morph

Use `MorphIcon` when animation progress is owned by the caller:

```kotlin
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.morph.compose.MorphIcon

@Composable
fun MenuCloseIcon(progress: Float) {
    MorphIcon(
        from = Icons.Filled.Menu,
        to = Icons.Filled.Close,
        progress = progress,
        modifier = Modifier.size(32.dp),
        tint = MaterialTheme.colors.primary,
        contentDescription = "Menu",
    )
}
```

## Component shapes and image clips

Create immutable inputs once from Compose `Path` values or `List<PathNode>` commands, with an
explicit viewport. The same animated shape works with `background`, `border`, and `clip`:

```kotlin
val circle = remember {
    morphGeometryOf(Path().apply { addOval(Rect(0f, 0f, 100f, 100f)) }, Size(100f, 100f))
}
val square = remember {
    morphGeometryOf(Path().apply { addRect(Rect(0f, 0f, 100f, 100f)) }, Size(100f, 100f))
}
val plan = rememberMorphPlan(circle, square)
val shape = rememberMorphShape(plan, progress)
Box(Modifier.size(160.dp).background(Color.Blue, shape))
// Image(..., modifier = Modifier.size(160.dp).clip(shape))
```

These APIs are in `li.songe.morph.compose`; the geometry types come from
`androidx.compose.ui.geometry` and `androidx.compose.ui.graphics`.

For multiple destinations, `rememberMorphGeometryState(initialGeometry)` provides `animateTo`,
`snapTo`, and `seekTo(from, to, progress)`. Pass the controller to `rememberMorphShape(state)`.
Rapid target changes continue from the currently visible contour, including a scrubbed frame.
The default `System` motion policy honors the host animation duration scale.

`MorphContentScale.Fit` centers the normalized viewport without distortion. `FillBounds` stretches
its normalized square along both axes, useful for component backgrounds authored in a square
viewport. Shape morphing does not change layout, pointer hit regions, or the pixels inside a clip.

For custom Canvas rendering, remember `plan.createPathWriter()` and one destination `Path`, then
call `writer.writePath(path, progress, size)` during drawing. Each consumer owns its writer and
path; the plan can be shared. Geometry plans default to 128 samples per contour. Use more samples
for large or intricate shapes; intermediate frames are polylines, while endpoint curves are retained.

Path input represents filled geometry, with nested contours interpreted as alternating filled
shapes and holes. Open subpaths are implicitly closed. It does not carry stroke, gradient, text
layout, or font shaping information. Brushes can be applied independently when drawing the shape.

## Animated morph

Pass the current `ImageVector` to `AnimatedMorphIcon`. Whenever it changes, the component morphs
from the currently displayed geometry to the new icon:

```kotlin
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import li.songe.morph.compose.AnimatedMorphIcon

@Composable
fun NavigationIcon(icon: ImageVector) {
    AnimatedMorphIcon(
        imageVector = icon,
        animationSpec = tween(durationMillis = 300),
    )
}
```

Use the Boolean overload when switching between a fixed pair of icons:

```kotlin
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import li.songe.morph.compose.AnimatedMorphIcon

@Composable
fun PlayPauseIcon(
    playing: Boolean,
    onToggle: () -> Unit,
) {
    AnimatedMorphIcon(
        from = Icons.Filled.PlayArrow,
        to = Icons.Filled.Pause,
        targetState = playing,
        modifier = Modifier.size(32.dp).clickable(onClick = onToggle),
        contentDescription = if (playing) "Pause" else "Play",
    )
}
```

## Reusing a morph plan

Precompute and remember a plan when the same icon pair is rendered by multiple consumers or is
recomposed frequently:

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import li.songe.morph.compose.MorphIcon
import li.songe.morph.compose.rememberMorphPlan
import li.songe.morph.compose.MorphInterpolation

@Composable
fun CachedMenuCloseIcon(progress: Float) {
    val plan = rememberMorphPlan(Icons.Filled.Menu, Icons.Filled.Close)

    MorphIcon(
        plan = plan,
        progress = progress,
        interpolation = MorphInterpolation.Polar,
    )
}
```

## Arbitrary icon targets

Use `MorphIconState` when the caller also needs imperative `animateTo`, `snapTo`, `seekTo`, or
animation status. If the destination changes while an animation is running, the state freezes the
current geometry and continues from that frame without jumping back to an endpoint.

```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.vector.ImageVector
import li.songe.morph.compose.MorphIcon
import li.songe.morph.compose.rememberMorphIconState

@Composable
fun NavigationIcon(target: ImageVector) {
    val state = rememberMorphIconState(Icons.Filled.Menu)

    LaunchedEffect(target) {
        state.animateTo(target)
    }

    MorphIcon(state = state, contentDescription = "Navigation action")
}
```

Pass `motionPolicy = MorphMotionPolicy.Never` to `animateTo` or `AnimatedMorphIcon` to snap,
or `Always` to animate independently of the host duration scale. The default `System` policy
honors the platform preference.

## Compatibility diagnostics

Inspect user-provided icons before presenting an animation editor or importing an icon set:

```kotlin
val report = inspectMorphCompatibility(fromIcon, toIcon)

if (!report.isSupported) {
    println(report.issues.joinToString())
}
```

`FullPolar` means every contour uses Polar interpolation. `Hybrid` means safe Linear fallback is
used only for incompatible contours, such as a hole that exists at one endpoint. Unsupported
features such as stroke-only paths are reported as `Unsupported` instead of being rendered
incorrectly.

## Supported input

`morph-compose` supports solid fill paths, nested group transforms, compound contours, holes, and
different source and destination viewport sizes. It rejects clip paths, trim paths, gradient fills,
and stroke-only paths instead of rendering them incorrectly.
