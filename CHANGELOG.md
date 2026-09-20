# What's Changed in 0.4.0

<!-- Only keep the changes for the latest release in this file. -->

- Add opt-in `ExperimentalStrokeInference` for simple filled polygons with clear, nearly uniform-width flat-ended strokes. Validate reconstructed ink and boundaries, then compare alternative stroke decompositions. Default `Auto` behavior is unchanged.
- Preserve interruption snapshots and translucent fill opacity during inferred-stroke transitions, and report inference decisions and fallbacks. Geometry and clipping APIs explicitly reject this experimental mode.
- Restore original Material icons in the playground instead of silently substituting custom stroke versions; add inference controls and handwritten canvas references.
- Separate parsing, representation selection, contour matching and alignment internally. Reuse normalized endpoints and avoid redundant parsing and contour scans without changing the public API beyond the new enum entries.
- Add inference-specific catalog checks, repeated-interruption tests, large-size diagnostic images, and bilingual prompts for authoring animation-friendly stroke icons with AI.
- Add `ExperimentalStrokeInference` to `MorphTransitionMode` and `InferredCenterline` to `MorphAppliedStrategy`. Consumers with exhaustive enum `when` expressions may need to handle the new entries when recompiling.

Known limits: stroke inference remains experimental and does not support arbitrary curves, holes or disconnected fills. Intermediate appearance depends on the chosen configuration. The 60 existing quick visual-baseline differences from restoring original icons remain unapproved; baselines were not overwritten. Maven library release checks are separate from the website's visual deployment gate.
