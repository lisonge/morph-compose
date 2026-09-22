# morph-compose

[![Maven Central](https://img.shields.io/maven-central/v/li.songe.morph/morph-compose.svg?label=Maven%20Central)](https://central.sonatype.com/artifact/li.songe.morph/morph-compose)

English | [简体中文](README.zh.md)

Live demo: [morph-compose.pages.dev](https://morph-compose.pages.dev)

A Compose Multiplatform library for morphing icons and custom paths. Supports Android, JVM, and Kotlin/Wasm.

<img width="1900" height="480" alt="Image" src="https://camo.githubusercontent.com/a80bb59affd89d0bdd7cb8d9572fdb44eaeba1582a9ecb08a1698e21be10a812/68747470733a2f2f652e676b642e6c692f30613266353963372d653664652d343831632d396434332d646239396433643664323234" />

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

Supports solid fill-only and solid stroke-only paths, nested transforms, multiple contours, and holes.
Clip paths, trim paths, gradients, combined fill and stroke on one path, and non-uniform stroke transforms are not supported.

For line icons, use an `ImageVector` with `fill = null`, `stroke = SolidColor(...)`, and separate
`moveTo` commands for independent strokes. Morphing matches and transforms centerlines while
retaining stroke width; unequal stroke counts split or merge. The playground uses original Material icons.
Material filled icons
still morph their boundaries; stroke structure is not inferred. Auto uses centerlines only when both
inputs are stroke-only. Otherwise strokes expand into filled outlines and overlapping ink is merged,
so mixed stroke/fill pairs morph together. `MorphGeometry` and clipping remain
filled geometry APIs.

`MorphTransitionMode.ExperimentalStrokeInference` is an opt-in `ImageVector`/`MorphIcon` mode.
It can recover flat-capped straight strokes from a single filled polygon with 2–6 clear terminals,
including crossings and a bent branch with a stem. Original Material vectors remain the inputs;
there are no icon-name rules. Candidate ink must retain one contour, have bidirectional polygon
boundary error at most 0.6% of stroke width and symmetric ink difference at most 0.3%. Added and
missing regions are measured separately; whole-edge capsule coverage checks the boundary.
Up to four decompositions per input are compared jointly, using sampled junction gaps, stretch,
extra self-intersections and alignment residual. Rotation preference breaks quality ties.
Diagnostics include reconstruction errors, candidate choice and motion scores. Resting endpoints render
the original vector; intermediate frames use the verified approximate reconstruction with butt
caps and miter joins. Unsupported or identical input pairs retain Auto behavior. Curves, holes,
disconnected shapes, ambiguous widths and general skeleton inference are outside this first version.
This mode does not support `MorphGeometry`/clipping. The playground exposes **Infer strokes (experimental)**;
Auto remains unchanged. Diagnostics report `InferredCenterline` or an inference fallback.

`MorphOptions.transitionMode` selects `Auto`, forced `Outline`, or strict `Centerline` (rejects filled
input). `strokeCountStrategy` chooses `SplitMerge` or `Collapse` for unequal centerline counts.
`outlineTolerance` defaults to `0.0001` normalized viewport units and controls stroke expansion
precision; smaller values cost more planning work. Existing sampling and rotation options apply after
conversion. Interruptions use the same representation policy, converting the currently rendered ink
within that tolerance when leaving centerline mode. Filled contour matching continues to preserve
winding and holes. Authored trim timelines remain outside the automatic solver.
Use `inspectMorphCompatibility(from, to)` to check icons before animating them.
`contourStrategy` selects the ordinary `Standard` baseline, default `SharedBoundary`, or explicit
`ExperimentalHoleOpening`. Reports include actual strategies, contour correspondence and fallback
decisions. Run `./gradlew verifyMorph` for the quality gate; see the [quality workflow](docs/quality.md).

See the [API and algorithm documentation](docs/architecture.md#api-layers) for state controllers, plan reuse, and interpolation details.
The [project docs](docs/README.md) cover the playground and development setup.

## Choosing an effect and rebuilding strokes with AI

Different icon pairs suit different configurations; choose the effect you prefer. An unattractive
result for one configuration is not necessarily an algorithm defect. First compare contour policies,
rotation preferences and interpolation modes. For simple line icons, also try Infer strokes,
for example with the original Close ↔ Arrow back pair.

If none of the configurations produces a satisfactory animation, you can ask AI to rebuild the
filled icons as explicit strokes. Authored centerlines can help control correspondence and motion,
but arbitrary filled shapes cannot always be expressed identically with strokes. Review the appearance
and animations in both directions. Copy and adapt this prompt:

```text
Reimplement the filled icon below as a stroke-based Compose ImageVector for icon morphing.

Requirements:
- Preserve the original viewport, dimensions, position, visual weight, end caps and corners as closely as possible.
- Express each stroke through its centerline; do not simply add an outline stroke to the original filled boundary.
- Use fill = null and stroke = SolidColor(...), with moveTo separating independent strokes.
- Prefer a small number of clear paths; avoid unnecessary bends and duplicate paths.
- Choose strokeLineWidth, strokeLineCap, strokeLineJoin and strokeLineMiter to match the original.
- If a target icon is supplied, check that it is stroke-only too; rebuild it if needed so both endpoints support centerline morphing.
- Design sensible stroke correspondence across the pair, without visibly changing the icons just to equalize stroke counts.
- Do not use clipping, path trimming, gradients, combined fill and stroke on one path, or non-uniform stroke transforms.
- Output usable Kotlin code and an animation example using MorphIcon and MorphTransitionMode.Centerline.
- Provide static original/rebuilt comparisons and animations in both directions. Explain approximations; do not claim unverified pixel equivalence.
- Give rebuilt icons separate names (such as CloseStroke), preserving the originals for comparison and rollback.

Source icon:
[Paste ImageVector code or SVG]

Target icon (optional):
[Paste ImageVector code or SVG]
```

When both inputs are stroke-only, `Auto` uses centerlines; you can also select `Centerline` explicitly.
If the other input remains filled, `Auto` expands strokes and morphs outlines, while `Centerline`
rejects the pair. For unequal stroke counts, compare `SplitMerge` and `Collapse`.
This is an intentional redesign by the user, not an automatic replacement by the library.
Rebuilt examples should identify their origin and purpose separately from the originals.

## Generate a standalone transition with AI (no library required)

For a fixed icon pair, you can ask AI to author a dedicated animation when you want precise control
over how each part moves. This does not require morph-compose or trying every library configuration
first. It suits a small number of important interactions; automatic transitions between arbitrary
runtime icons remain the library's main use case. This approach can use path trimming, local timelines
and filled geometry freely; the stroke-rebuilding restrictions above do not apply.

Copy this prompt, supply both original icons, and describe the motion you want:

```text
Write a standalone Kotlin/Compose transition between the two icons below, without morph-compose
or another morphing library. Use Compose animation and drawing APIs. Provide complete code and
a clickable toggle example.

Requirements:
- Treat the supplied original code as the endpoint reference. Preserve viewports, dimensions,
  position, stroke widths, caps, corners, fills and holes. Do not add rounding, change weight or
  redesign icons to simplify animation. Explain limitations and alternatives if exact preservation is infeasible.
- Briefly describe which parts stay, move, rotate, shrink or grow before implementing them.
  Prefer preserving shared structure; avoid unnecessary whole-icon rotation, collapse and extra bends.
  Do not default to crossfading.
- Use Canvas, Path, ImageVector, path trimming and local progress intervals as appropriate.
  You do not need to convert all fills to strokes or equalize path counts.
- Describe geometry with one continuous progress p: 0 is the source and 1 is the target.
  Separate progress-driven drawing from animation state so intermediate frames can be inspected manually.
- Support repeated clicks and reversal from the current progress without resetting to an endpoint.
  Do not abruptly switch geometry formulas based on the target boolean. State whether velocity is continuous.
- Support modifier, tint and contentDescription; preserve aspect ratio and center within the available area.
- Keep exact endpoints and nearby frames continuous. Do not hide mismatched intermediate geometry
  by drawing the originals only at p=0/1. Check short-stroke caps, path birth/death and arc joins
  for flashes, jumps and seams.
- Avoid unnecessary per-frame allocations without changing appearance or adding unrelated infrastructure.
- Provide original-versus-animated endpoint comparisons, frames at 0/25/50/75/100%, and examples of
  both directions and interrupted reversal. Verify in a runnable environment when available;
  otherwise identify unverified items instead of claiming pixel equivalence or passing tests.

Source icon:
[Paste complete ImageVector code or SVG]

Target icon:
[Paste complete ImageVector code or SVG]

Desired motion:
[For example: retain the shared slash, retract the other stroke toward the center, and grow the ring
from specified locations; or ask AI to propose the motion]

Duration and easing:
[For example: 300ms with Compose's default tween easing]

Project environment:
[Compose platform, version, and existing Material/Material3 dependencies]
```

Review AI-generated results, especially endpoint fidelity and rapid reversal. An authored transition
for a fixed pair does not automatically support interruption toward an arbitrary third icon.

## Inspiration

This project was inspired by [morphicons](https://github.com/guillermolg00/morphicons).
