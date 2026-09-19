# Roadmap

The roadmap follows the useful direction of the original Morphicons project while keeping the
scope native to Compose `ImageVector` rather than copying SVG/browser-specific APIs.

## Available now

- Filled Compose Path / PathNode geometry input, reusable path writers, and animated Shape adapters.
- Background, border, clip, interaction, decoration, and prepared numeral outline demonstrations.
- Top-level feature tabs in the shared Desktop/Wasm playground.

- Winding-safe one-to-one shape and hole matching with per-contour Polar/Linear fallback.
- Corner- and edge-aware cyclic alignment with intermediate-frame boundary-fold checks.
- `inspectMorphCompatibility` reports whether a pair is full Polar, hybrid, or unsupported.
- `MorphIconState` supports arbitrary destinations and continuous mid-animation interruption.
- System-aware reduced-motion behavior with explicit always-animate and never-animate policies.
- The Desktop/JVM playground exposes live Morph Doctor feedback for selected pairs.
- JVM, Android, and KLib ABI baselines are enforced before release.

## Next

- Add richer input diagnostics with vector/path names and suggested fixes.
- Scan larger icon corpora and preserve visual regression fixtures for known topology cases.
- Let the Desktop/JVM playground export a compact compatibility report for a selected sequence.
- Add an optional adapter layer for generated/custom vector formats without coupling the core
  artifact to an icon pack.

## Exploring

- A timeline editor for multi-icon sequences and per-step animation specs.
- Shareable playground state encoded in a compact URL or file format.
- Extend stroke centerline support to non-uniform transforms and trim semantics; basic solid
  stroke paths, caps, joins, and interrupted stroke morphing are supported.
