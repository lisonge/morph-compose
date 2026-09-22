# Playground and desktop debugging

## Playground

`morph-playground` shares its UI and playback state from `commonMain`. It has two runtime entry points:

- Desktop/JVM is the primary development and visual-verification target.
- Browser/Wasm is packaged as the `morph-playground` pnpm workspace package and mounted by
  `morph-website` through an exported DOM render function.

The playground provides:

- top-level tabs for Icons, Shapes, Image clips, Interaction, Loading, and Typography;
- component background/border morphing, a clipped landscape bitmap, a moving tab indicator,
  looping decorative shapes, and numeral outlines with a searchable font selector;
- independent target selection, cycle controls, and a manual pair scrubber in every new module;
- per-module controls retained when switching tabs (state is not persisted across app restarts
  or browser reloads);

- a fixed catalog of 2000 Material icons;
- ordered multi-selection supporting zero, one, or many icons;
- cyclic morph playback through the selected sequence;
- Last, play/pause, and Next controls;
- a progress scrubber;
- optional polar/linear comparison;
- Auto, Clockwise, and Counterclockwise rotation preferences (changing preference pauses and resets the current pair);
- a live Morph Doctor summary for Polar, hybrid, and unsupported pairs;
- a light theme by default and a theme icon button fixed to the left of the scrollable top-level tabs.

The icon catalog preserves the original 504 entries and their indices. The additional 1496 names
come from [Google Fonts icon metadata](https://fonts.google.com/metadata/icons?key=material_symbols&incomplete=true),
selected on 2026-09-22 by descending popularity among icons available in Compose Material Icons
1.7.3 (using the highest popularity when multiple families share a name). Added entries retain
the metadata's searchable snake_case names. All added vectors reference AndroidX `Icons.Filled`
or `Icons.AutoMirrored.Filled` through the existing Compose Multiplatform dependency; no custom
path data or runtime metadata download is involved.

The shared UI uses Compose `BoxWithConstraints` as a container-query equivalent. Below 460 dp,
the Polar/Linear previews stack vertically; compact control containers use icon-only transport
buttons; and windows at least 980 dp wide switch the playground to a two-column workspace.

Run the desktop application:

```powershell
.\gradlew.bat :morph-playground:run
```

The Desktop/JVM application restores its last window position and size from
`.local/morph-playground/window-state.json`. The `.local` directory is ignored by Git. Missing,
invalid, or off-screen positions fall back to the platform default; a valid saved size is still
restored when the saved display is no longer connected.

### Typography fonts

Typography opens in **Number N → M** mode. Enter two signed integers, then choose **Play N → M**
or **Swap & play**. Presets include `99 → 100` and `1024 → 7`; the slider scrubs the applied pair.
Enable **Step · count by ±1** to visit every integer in order: `1 → 100` plays `1 → 2`,
`2 → 3`, through `99 → 100`; descending ranges subtract one and signed ranges can cross zero.
Each step takes 350 ms with a 70 ms endpoint hold between steps. The label shows the current pair
and the requested range. Scrubbing pauses on the current step; Play restarts from the requested
source. Changing Step stops playback and resets the preview for the chosen mode. Unchecked,
the demo keeps its direct one-second transition. Step computes one successor at a time, so large
ranges do not allocate a list of intermediate values; reaching a distant target still takes one
animation per integer. Leading-zero padding follows the source until the exact target is reached.
Input stays as strings, preserving leading zeros and values beyond `Long` without rounding.
Decimals and scientific notation are not accepted. Very long numbers scroll horizontally, starting
at the right edge so the least significant places remain visible when the number becomes shorter.

Digits morph simultaneously by place value, aligned from the right. Higher places fade and scale
in or out when the digit count changes; the minus sign is a separate geometric mark that fades
and moves beside the number. It does not morph into a digit. **Single digit** retains the original
0–9 cycle demo. Both modes share the selected font and retain their own controls across tab changes.

The Typography tab starts with bundled Space Grotesk outlines. Desktop automatically lists system
font families; the searchable dropdown loads the selected family's regular face. The digits use
the font's actual vector outlines, with a shared baseline and scale across 0–9. Font and digit
selections survive tab changes, and switching fonts retains playback and scrub settings.

In the browser, **Load system fonts** requests permission through
[Local Font Access](https://developer.mozilla.org/en-US/docs/Web/API/Window/queryLocalFonts).
This requires a supporting browser and a secure context (HTTPS or localhost). No font request is
made before a click. Unsupported browsers and denied permission leave the bundled font available;
permission failures can be retried. Font data stays local and is never uploaded.

Fonts without usable vector outlines for all ten digits show an error and retain the previous
selection. The eight most recently selected system-font outline sets are cached for reuse.

Install the workspace, build the Wasm package, and start the Vite website:

```powershell
pnpm install
pnpm dev
```

The website is served at `http://127.0.0.1:8421`. Run `pnpm --filter morph-playground build` when only the
embeddable ESM output under `morph-playground/dist` is needed. `pnpm build` type-checks and builds both
workspace packages in dependency order.

## Browser package

The `morph-playground` workspace package exports a render function that accepts the target DOM element.
The host application owns element lookup and lifecycle:

```ts
import { renderMorphPlayground } from 'morph-playground';

const container = document.querySelector<HTMLElement>('#playground');
if (container) renderMorphPlayground(container);

// Remove the Compose child when the host is unmounted.
container?.replaceChildren();
```

The build writes the self-contained ESM package to `morph-playground/dist`. See
[Architecture and morph pipeline](architecture.md) for the Kotlin/Wasm preprocessing and packaging
details.

## Ktor desktop debugging API

The desktop application starts an embedded Ktor CIO server at `http://127.0.0.1:17321`. It binds
only to the loopback interface.

Read the current state:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/health'
```

Replace the selected sequence, seek to a fixed frame, and enable the linear comparison:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/control?selected=0,4,8,12,16&progress=0.41&linear=true'
```

Clear the selection and animation state:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/control?selected='
```

Compare both directions of Arrow back / Close with a counterclockwise preference:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/control?selected=15,1&from=15&to=1&rotation=ccw&progress=0.5'
Invoke-RestMethod 'http://127.0.0.1:17321/control?from=1&to=15&rotation=ccw&progress=0.5'
```

`rotation` accepts `auto`, `cw`, or `ccw`. The health/control snapshots include `rotationPreference`.
The preview, Linear comparison, and Morph Doctor all use the selected options.

Choose **Infer strokes (experimental)** in the transition mode controls to try geometric stroke
recovery on the original filled icons. For Close → Arrow back, use:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/control?selected=1,15&from=1&to=15&transitionMode=infer&progress=0.5&details=true'
```

The original vectors stay in the catalog. Planning details distinguish accepted stroke recovery
from outline fallback. This is an opt-in approximation for simple flat-ended polygonal strokes;
it is not general curved-icon skeleton extraction. Auto remains the default.

Choose configurations by the effect you want; not every pair needs to look good under every mode.
If none fits, an explicitly authored stroke version is another option. See the
[AI-assisted rebuilding prompt](../README.md#choosing-an-effect-and-rebuilding-strokes-with-ai)
or [中文提示语](../README.zh.md#选择效果与-ai-辅助描边重建).
Keep rebuilt icons separately named and labeled; do not silently replace the original catalog entries.

Control playback:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/control?action=play'
Invoke-RestMethod 'http://127.0.0.1:17321/control?action=pause'
Invoke-RestMethod 'http://127.0.0.1:17321/control?action=last'
Invoke-RestMethod 'http://127.0.0.1:17321/control?action=next'
```

Switch the theme:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/control?dark=false'
Invoke-RestMethod 'http://127.0.0.1:17321/control?dark=true'
```

Open a feature module and inspect a reproducible intermediate frame:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/control?module=clipping&demoTarget=2&demoProgress=0.5'
Invoke-RestMethod 'http://127.0.0.1:17321/screenshot?area=window'
```

Module IDs are `icons`, `shapes`, `clipping`, `interaction`, `loading`, and `typography`.
Use `demoPlaying=true` to cycle a module automatically. Switching tabs disposes the previous module's animation
effect; revisiting preserves the chosen controls. Shape demos enter at the selected target without
replaying the previous transition; manual scrubbing restores its saved frame, and an enabled cycle
advances on its next timer tick. The preview-only screenshot remains the icon stage; use `area=window`
for the new modules. Tabs render through `when` branches without a
navigation dependency or back stack. No browser runtime tests are required by the project.

Read the system font list, select a font by its returned ID, or open the font dropdown:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/fonts'
Invoke-RestMethod 'http://127.0.0.1:17321/fonts?id=Times%20New%20Roman'
Invoke-RestMethod 'http://127.0.0.1:17321/fonts?menu=true'
```

`/fonts` returns `selected`, `error`, and a `fonts` array of `id`/`label` entries. Use
`id=builtin%3Aspace-grotesk` to restore the bundled font and `menu=false` to close the dropdown.

Inspect and control the whole-number demo through `/numbers`:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/numbers?from=99&to=100&progress=0.5'
Invoke-RestMethod 'http://127.0.0.1:17321/numbers?play=true'
Invoke-RestMethod 'http://127.0.0.1:17321/numbers?from=1&to=100&step=true'
Invoke-RestMethod 'http://127.0.0.1:17321/numbers'
```

Setting `from` or `to` applies both input fields and replays the transition. `progress` pauses at
the requested `0..1` frame. `/numbers` returns the applied pair, progress, playback state, and input
error, plus `step`, `rangeFrom`, and `rangeTo`. Use `step=false` to return to a direct transition.
The existing `demoTarget`/`demoProgress`/`demoPlaying` controls select Single digit mode.

Resize the Desktop/JVM window for responsive-layout checks (logical pixels):

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/control?width=420&height=760'
Invoke-RestMethod 'http://127.0.0.1:17321/control?width=1180&height=820'
```

Capture only the top morph-preview stage from inside the application process:

```powershell
Invoke-RestMethod 'http://127.0.0.1:17321/screenshot'
```

The default output is `morph-playground/build/reports/morph-playground-debug.png`. An absolute, URL-encoded
path can be supplied through the `path` query parameter. The compact output contains the large
Polar preview and, when enabled, the Linear comparison. The renderer does not require the window
to be in the foreground and cannot capture overlapping windows.

For responsive-layout diagnostics, `area=window` captures the full content area. The default
remains `area=preview` so ordinary morph checks do not produce oversized images.

## Control parameters

| Parameter | Description |
| --- | --- |
| `selected` | Comma-separated, ordered, distinct zero-based indices; empty and single-item selections are supported |
| `module` | Feature tab ID listed above |
| `demoTarget` | Zero-based target index within the active feature module |
| `demoProgress` | Manual feature progress in `0..1`; disables cycling |
| `demoPlaying` | Resume automatic mode and enable or disable cycling |
| `action` | `last`, `previous`, `prev`, `next`, `play`, `pause`, or `toggle` |
| `progress` | Finite morph progress; overshoot values are accepted |
| `playing` | Boolean playback state |
| `linear` | Show or hide the linear comparison |
| `dark` | Enable or disable the dark theme |
| `width`, `height` | Resize the Desktop/JVM window in logical pixels for responsive-layout checks |
| `from`, `to` | Select a specific pair while retaining sequence compatibility |
| `pair` | Compatibility shorthand where `pair=n` selects indices `2n` and `2n+1` |
| `target` | Compatibility alias for `playing` |

Boolean values accept `true`/`false`, `1`/`0`, and `yes`/`no`.
