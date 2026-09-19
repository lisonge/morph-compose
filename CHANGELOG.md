# What's Changed

<!-- Only keep the changes for the latest release in this file. -->

- Add unified automatic, outline and centerline transitions with configurable stroke-count and contour strategies.
- Preserve shared boundaries, improve endpoint continuity and rotation matching, and expose per-contour diagnostics. Hole-opening transitions remain explicitly experimental.
- Add outlined lock examples, icon filtering and copyable pair names to the playground.
- Add a Chinese quality center with visual regression reports, APNG previews, public image links and local image caching.
- Verify JVM tests and visual references before website deployment, and API compatibility plus Android/Wasm compilation before library publication.
- Rebuild precompiled consumers when upgrading: additions to `MorphOptions` and contour reports change the binary API.
