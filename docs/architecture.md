# Architecture and morph pipeline

## Gradle modules and workspace packages

The repository contains two Gradle modules and two pnpm workspace packages. `morph-playground` participates
in both build systems:

| Directory | Package | Purpose |
| --- | --- | --- |
| `morph-compose` | `li.songe.morph.compose` | Morph engine, Compose `ImageVector` conversion, plan caching, and drawing APIs |
| `morph-playground` | `li.songe.morph.playground` / `morph-playground` | Shared playground UI, Desktop/JVM application, and Browser/Wasm ESM artifact |
| `morph-website` | `morph-website` | TypeScript Vite/Vue/Tailwind CSS shell that owns the browser DOM lifecycle |

All published artifacts use `li.songe.morph` as their group ID and are distributed through Maven
Central.

## Browser packaging pipeline

`morph-playground` exports `renderMorphPlayground(container)` from its Wasm target. JavaScript passes the real
DOM `Element`; Kotlin does not perform an ID lookup. The generated Wasm ABI represents the value as
`JsAny` because Kotlin 2.4.20 cannot export the complete DOM `Element` model, while the preprocessor
narrows the generated public TypeScript declaration back to `Element`.

The Node.js preprocessing script runs the production Wasm link, recreates `morph-playground/dist`, copies
the Kotlin/Wasm output plus `skiko.mjs` and `skiko.wasm`, normalizes source-map paths, adds
`/* @vite-ignore */` to compiler-generated Node-only dynamic imports, and validates all relative imports
and `new URL` assets. It also emits a small metadata module containing the original byte length of each
Wasm asset.

`morph-website` imports that workspace package dynamically after Vue has created the host element.
During that import it temporarily tracks the two Wasm response streams and presents their aggregate,
byte-weighted download progress. The original streams continue into `WebAssembly.instantiateStreaming`,
so progress reporting does not add another download or disable streaming compilation. Once both streams
finish, the loading state reports startup until Compose mounts its first DOM child.
Removing the host's Compose child triggers Compose's disconnect cleanup. The website contains no
Kotlin code and all of its source and configuration files are type-checked TypeScript.

## Processing pipeline

The library remains one Gradle module. Internal file boundaries follow the existing pipeline:

- `ImageVectorParsing.kt` parses commands, group transforms and topology. `ImageVectorAdapter.kt`
  retains public vector-plan entry points and compatibility reports, preserving their JVM facade.
- `VectorPlanning.kt` owns representation selection for both original inputs and interruption
  snapshots, including inference fallback diagnostics. `StrokeOutline.kt` only reconstructs
  snapshot curves and expands stroke ink.
- `ContourMatching.kt` owns role-compatible assignment; `ContourAlignment.kt` owns geometric
  alignment and candidate scoring. `internal/MorphPlan.kt` assembles plans and global transport.
- Filled geometry retains its validated cubic paths. Vector plans retain normalized original
  endpoints for path writers, so validation and endpoint rendering do not repeat parsing.
  This is per-object reuse, not a global cache. Interrupted sources retain sampled snapshots.
- Icons and compound-path writers share the single-contour append routine. Drawing one stroke
  does not scan every other contour. Curve offsets and exact endpoint paths remain distinct.

These boundaries do not change matching thresholds, interpolation formulas, strategy defaults,
or the public API. Geometry/clip wrappers continue to reuse the icon controller rather than
introducing a second animation state machine.

1. Flatten nested `ImageVector` group transforms and normalize both vectors into a shared
   `xMidYMid meet` coordinate space.
2. Convert lines, quadratic curves, cubic curves, and elliptical arcs into cubic Bézier curves.
3. Measure arc length with eight-point Gauss–Legendre integration and preserve sharp corners as
   weighted sampling anchors. These feature weights survive interrupted-animation snapshots.
4. Classify compound-path contours as filled shapes or holes, then match only compatible roles by
   centroid and length. Matching is a minimum-cost partial one-to-one assignment: an existing
   contour is never duplicated because duplicate filled contours change `NonZero` winding. Any
   unmatched source or target contour collapses into or grows from a zero-area contour.
5. Evaluate cyclic contour correspondences by Procrustes residual and preflight their 25%, 50%,
   and 75% Polar/Linear frames for boundary folds. Among similarly clean fits, retained corners,
   local edge direction, and rotation amount break ties. This keeps semantic corners aligned
   without allowing a feature heuristic to override a materially better geometric fit.
6. Interpolate residual geometry in a local coordinate frame. Role births/deaths and numerically
   degenerate fits fall back to linear interpolation per contour, while compatible contours in the
   same icon keep Polar interpolation.
7. Use block transport when the complete icon is close to a rigid similarity transform.
8. Reuse `MorphFrame` and Compose `Path` instances while drawing animation frames.

Filled contour pairs with ordered, same-position cubic vertices are also checked for shared
boundary spans when `contourStrategy` enables them. `Standard` bypasses both boundary constraints
and hole opening; default `SharedBoundary` enables only the former. `ExperimentalHoleOpening`
must be explicitly selected to enable seam construction. `ContourStrategies.kt` owns this policy
stage, and each contour report retains its input indices and actual selection/fallback reason.

When a meaningful portion of an eligible pair's boundary agrees, samples are allocated jointly
between those vertices instead of independently over each entire perimeter. These contours are
excluded from global rotation/scale transport. Shared spans interpolate their original curve
details directly, preserving real differences between icon packs rather than snapping them away.
Changing spans retain local Polar motion with endpoint corrections and bounds from both endpoint
curve chains, so they cannot drag the shared region along. Linear mode uses the same anchored
correspondence without the local rotation. Ambiguous/reordered vertices, unsupported segmentation,
and sampled interruption sources without an original curve use the existing solver; this is not
a general semantic decomposition of arbitrary icons. Interrupted frames still retain the exact
rendered curve offsets for continuity.

Before topology classification, collinear filled retraces and points are discarded. This check
uses cubic control points rather than signed area, so zero-signed-area self-intersecting shapes
and visible stroked lines are not accidentally removed. Shared anchors must border a verified
common span; isolated coordinate coincidences in the moving region do not constrain motion.
When an unmatched hole can join the changing boundary of an unambiguous parent with a shared
region, a doubled, zero-width seam joins the hole to that parent's cubic chain. Equally short
valid seams are compared by the resulting local fit rather than path enumeration order. The seam leaves
the endpoint fill unchanged and lets the opening morph with the outer boundary rather than shrink
independently. Unchanged parents and ambiguous/nonlocal holes retain the collapse fallback.

After matching and global alignment, source and target cubic details are represented as offsets
from the sampled edges. Their segment boundaries are merged per aligned edge and cubics are
subdivided exactly, giving both endpoints the same control-point layout without changing the
sampled correspondence, rotation, or block transport. Source offsets fade out and target offsets
fade in, each transported in its own Polar/Linear coordinate frame. The limiting geometry is the
original endpoint curve, so switching to an exact icon at rest does not flash its rounded edges.
Clamped detail weights also preserve continuity through spring overshoot. Exactly stationary
contours keep their existing curves throughout. Interrupted snapshots retain the rendered offsets
and can be refined again for a new target without losing their current outline. Icons, shapes,
and masks share the compound-path renderer, including hole winding.

### Rotation preferences

`MorphOptions.rotationPreference` defaults to `Auto`. The two directional preferences retain the
existing minimum quality score (Procrustes residual plus weighted fold count) and its `1e-3` tie
window. Within that window, direction ranks before the existing corner/edge/rotation tie-break.
Candidates cannot increase the baseline fold count at the 25%, 50%, and 75% checks. These are sampled
checks, not a guarantee against short-lived intersections between checkpoints. The algorithm does not
infer stroke structure from filled outlines, and their intermediate silhouettes may still bulge.

### Stroke centerlines

The opt-in `ExperimentalStrokeInference` vector mode runs a bounded geometric recognizer before
representation selection. `StrokeInference.kt` accepts a single straight-edged filled contour with
2–6 flat terminals and near-uniform width. It enumerates terminal pairings (straight continuations
before bends), permits one odd terminal to attach at a verified junction, expands each candidate,
and checks one-contour topology and reconstruction against the input. `InkDifference.kt` measures
the added and missing regions with PathOps Difference and cubic area integration; their sum must
be at most 0.3% of input area. Equal-area changes cannot cancel. Bidirectional boundary checks cover
each entire polygon edge by the union of radius-0.6%-of-width capsules around the opposite edges.
The capsule intersections are parameter intervals; a covered interval is checked for gaps rather
than relying on isolated samples. Binary search gives a certified upper bound subject to the
floating-point PathOps/polygon representation, not a guarantee for arbitrary curves. Small source
coordinate rounding is still tolerated: this remains approximate recovery, not lossless skeleton
extraction. Enumeration is limited to six terminals and 32 simplified vertices.

`InferencePlanning.kt` compares up to four validated decompositions per side (at most 16 plans),
including the straight-first baseline. Each actual plan is evaluated at seven interior progress
values with at most 33 centerline points per stroke. Extra self-intersections rank first; the score
then combines alignment residual, relative edge stretch, gaps between strokes that touch at both
endpoints, and scale change, plus a 0.005 cost per inferred bend to discourage revealing hidden
elbows for negligible fit improvements. Direction preference breaks ties within 1e-3 of the best score without
allowing more sampled intersections. Reconstruction error, bend count, rotation and canonical
geometry order settle further ties. This is a bounded candidate comparison and sampled motion
heuristic, not globally optimal semantic matching or an all-times nonintersection proof. Decisions
record candidate counts/indices, gained/lost area ratios, boundary error and selected/baseline scores.
Interrupted strokes use their existing sampled snapshot while comparing target decompositions.

Both sides must be recoverable or explicit strokes. Identical inputs skip inference. Eligible
plans reuse stroke correspondence, rotation and width interpolation, retaining butt caps and miter
joins. Reports use `InferredCenterline`; recovered strokes refer back to their original single
filled contour (index 0). Ineligible pairs keep Auto's behavior and carry an explanatory decision.
An interrupted filled snapshot is never reinterpreted; interrupted strokes can target recovered
strokes or expand through the existing outline fallback. Geometry/clipping APIs reject this mode
until their fill-only renderer supports the inferred representation. Auto remains unchanged.

Public vector plans first resolve `MorphOptions.transitionMode`. Auto chooses centerlines only for
two stroke-only inputs; all other combinations expand stroke ink into filled outlines. Outline forces
that conversion and Centerline rejects non-stroke input. Common-code expansion flattens centerlines
using `outlineTolerance`, applies caps/joins/miter limits, and unions the ink with Compose PathOps
before classifying holes. This prevents overlapping Close strokes from becoming duplicate shapes.
The same policy handles interruption snapshots: keep existing outline samples, or reconstruct the
rendered stroked cubics and expand/resample them when switching representation. Expansion is an
approximation bounded by the configured flattening tolerance, rather than inferred stroke skeletons.

Solid stroke-only `VectorPath` inputs retain their open/closed centerlines, normalized width, cap,
join, and miter limit. Stroke contours are matched separately from filled shapes and holes. When
stroke counts differ, the default SplitMerge strategy uses a minimum-cost surjection to duplicate smaller-side centerlines so each stroke
splits or merges instead of shrinking to a point. Large assignments use an injective cover plus
nearest matches. Filled paths retain injection/collapse and their original winding behavior.
`MorphStrokeCountStrategy.Collapse` uses injection/collapse for centerlines too.

For equal stroke counts up to eight, distance/length assignments tied within `1e-9` are resolved
using the aligned direction preference, residual, and rotation cost. This lets interchangeable
strokes in symmetric icons switch partners before choosing endpoint traversal, avoiding a 135°
turn when a different equally close pairing permits 45°. Non-tied spatial assignments stay intact.

`MorphIcon` draws filled contours together and each stroke with its own `Stroke` style. Width
interpolates independently of the centerline's similarity scale (equal widths remain constant);
cap/join/miter use the nearer endpoint's style. Use matching cap/join styles for smooth transitions.
Interrupted snapshots retain the current centerline and width. Missing stroke roles collapse with
zero width, allowing filled/stroked icons to transition without a full-width dot at the endpoint.
Stroke group transforms must be similarities: translation, rotation, reflection, and uniform scale.
General filled geometry and clipping APIs remain fill-only.

Positive angles are clockwise in the final screen coordinate system; RTL normalization happens before
planning. Exact non-rotating similarities keep their original correspondence, so unchanged icons and
pure translations/scales do not acquire a gratuitous spin. Global block transport is skipped when it
would worsen a contour's direction preference. No angle is extended by a full turn. Linear comparison
uses the same correspondence but does not inherit a promise of rotational motion. Direction options
are part of the existing Compose plan/state remember keys, and interrupted plans reuse them with the
exact sampled source snapshot.

Adding the field changes the JVM/Android constructor and generated `copy` ABI of the `MorphOptions`
data class. Kotlin source callers using defaults remain compatible; precompiled consumers must be
rebuilt with this library version. JVM, Android, and KLib ABI baselines record the new signatures.

## API layers

Curve normalization, resampling, contour correspondence, and interpolation are implementation
details under `li.songe.morph.compose.internal`. They remain separate from the Compose adapter at
the package level without requiring another published artifact.

`buildMorphPlan` converts two `ImageVector` instances into an `ImageVectorMorphPlan`.
`rememberMorphPlan` adds Compose-aware caching and automatically accounts for RTL layout.

`MorphIcon` accepts either a precomputed plan or a source/destination vector pair. It is a
controlled API: the caller provides the progress value.

`MorphIconState` drives arbitrary icon sequences. On interruption it snapshots the current sampled
geometry, including any retained curve detail, and replans from that frame without another resampling pass. `AnimatedMorphIcon`
uses this controller for its Boolean convenience API. Both stateful entry points accept any
Compose `AnimationSpec<Float>` and default to the system motion-duration scale.

`inspectMorphCompatibility` is the non-throwing diagnostic layer. It reports `FullPolar`, `Hybrid`,
or `Unsupported`, plus the interpolation and fallback reason for every matched contour.

`MorphGeometry` adapts immutable snapshots of Compose paths or path commands through the same
normalization pipeline. Rational quadratic conics are subdivided in common code because the
Skia-backed iterator does not implement its requested conic-to-quadratic conversion.
`MorphGeometryPlan` shares the existing engine. `MorphGeometryState` delegates interruption,
motion policies, and seeking to the icon controller, avoiding a second animation implementation.
`MorphShape` produces independently owned `Outline.Generic` paths for background, border and clip;
`MorphPathWriter` reuses per-consumer frame storage for Canvas rendering. Endpoint frames retain
cubic curves. Intermediate frames retain the engine's sampled representation.

The playground renders its selected top-level tab through `when` branches. The six modules
share the theme and retain their controls in `PlaygroundState`. Animation effects live inside each
module's composition and are disposed when its tab is deselected. There is no navigation dependency,
back stack, or browser URL/history synchronization.

Typography uses platform-specific `SystemFontSource` implementations: Desktop enumerates Skia's
system families, and Wasm requests browser Local Font Access after explicit user activation.
Shared Skia glyph extraction converts digits into copied `PathNode` data before disposing native
font handles. `TypographyState` retains the chosen font across tabs, caches up to eight outline
sets, and ignores stale asynchronous loads. New outlines invalidate the active morph effect even
when the selected digit is unchanged. This font integration belongs to the playground; the library
continues to accept geometry independently of font loading and text shaping.

The whole-number demo keeps signed integer input as strings. It matches digits by place value
and reuses a glyph morph plan for each distinct digit pair. All places share one animation progress;
missing places fade and scale instead of being replaced with zero. A separate minus mark follows
the interpolated digit length. This keeps contour matching local to each numeral and preserves
leading zeros and integer precision without adding numeric parsing to the core engine.

## Supported `ImageVector` input

The adapter supports:

- solid fill paths;
- solid stroke-only paths with cap, join, and width;
- nested group transforms;
- compound contours inside a single `VectorPath`;
- automatic winding normalization for holes inside compound paths;
- different source and destination viewport sizes.

The adapter intentionally rejects inputs that cannot currently be represented faithfully:

- clip paths;
- trim paths;
- gradients;
- combined fill and stroke on a single path;
- non-uniform transforms of stroke paths.

Unsupported input fails explicitly instead of silently producing incorrect geometry. Separate
`VectorPath` values remain separate filled shapes.

## Toolchain baseline

- Kotlin `2.4.20`
- Gradle `9.7.1`
- Compose Multiplatform `1.12.0`
- Android Gradle Plugin `9.4.0`
- Java toolchain `21`, JVM/Desktop bytecode `21`, and Android bytecode `11`
- Android `compileSdk 37`, `minSdk 21`
- Ktor `3.5.2` for the Desktop/JVM debug server
- Node.js `26.8.1`, pnpm `12.3.4`, and TypeScript `6.0.3` for the browser workspace

Dependency and plugin versions are centralized in `gradle/libs.versions.toml`. Android SDK levels,
the Java toolchain, Kotlin JVM bytecode target, explicit API policy, and published-module selection are
configured once in the root `build.gradle.kts`.

Kotlin's built-in ABI validator records JVM, Android, and KLib public API baselines under
`morph-compose/api`. `checkKotlinAbi` runs as part of `check`; intentional public API changes must
be reviewed before updating the baseline with `:morph-compose:updateKotlinAbi`.
