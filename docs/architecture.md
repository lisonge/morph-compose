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

After matching and global alignment, exactly equal cubic contours retain their curves only when
the selected plan leaves them stationary. This avoids switching a shared curved outline to a
coarse polygon during animation. Matching, sample counts, and rotation choices remain unchanged.
Curve segments are subdivided at the existing sample locations and represented as offsets from
the sampled edges. Interrupted snapshots retain those offsets as well as the sample points;
if a subsequent transition moves the contour, the offsets follow its Polar/Linear transport and
fade to zero by the target. Icons, shapes, and masks share the same compound-path renderer so
preserved holes keep their winding. Curves that merely look similar or use different cubic
encodings conservatively follow the existing sampled rendering.

### Rotation preferences

`MorphOptions.rotationPreference` defaults to `Auto`. The two directional preferences retain the
existing minimum quality score (Procrustes residual plus weighted fold count) and its `1e-3` tie
window. Within that window, direction ranks before the existing corner/edge/rotation tie-break.
Candidates cannot increase the baseline fold count at the 25%, 50%, and 75% checks. These are sampled
checks, not a guarantee against short-lived intersections between checkpoints. The algorithm does not
infer stroke structure from filled outlines, and their intermediate silhouettes may still bulge.

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
- nested group transforms;
- compound contours inside a single `VectorPath`;
- automatic winding normalization for holes inside compound paths;
- different source and destination viewport sizes.

The adapter intentionally rejects inputs that cannot currently be represented faithfully:

- clip paths;
- trim paths;
- gradient fills;
- stroke-only paths.

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
