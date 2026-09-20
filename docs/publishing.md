# Publishing

`morph-compose` is the only published module. Its Maven coordinates are:

```text
li.songe.morph:morph-compose:0.4.0
```

The project uses the Vanniktech Maven Publish plugin. The root build configures coordinates,
Apache-2.0 license metadata, developer information, and SCM links for every module that applies the
plugin. `morph-playground` does not apply it and cannot be released accidentally.

The `morph-playground` and `morph-website` pnpm packages are private workspace packages. Their build
artifacts support the browser demonstration and are not part of the Maven Central publication.
The website workflow builds and deploys these browser artifacts independently from Maven releases.

## Release workflow

Pushing a tag matching `v*` starts `.github/workflows/release.yml`. The workflow:

1. Verifies that the tag matches the project version, then runs the JVM library tests and Desktop demo tests.
2. Checks the committed Kotlin ABI baseline for JVM, Android, and KLib targets, and compiles Android and Wasm targets.
3. Decodes the signing key into a temporary `secring.gpg` file.
4. Runs `publishAndReleaseToMavenCentral` with Maven Central and signing properties.
5. Creates the corresponding GitHub release.

The Maven release workflow does not run visual-baseline comparison. Website deployment does, and
fails when its references differ. A passing Maven release check does not imply `verifyMorph` or the
website deployment gate passed. Review and update visual references separately; never overwrite
them automatically to unblock publication.

Configure these GitHub Actions secrets before publishing:

- `OSSRH_USERNAME`
- `OSSRH_PASSWORD`
- `OSSRH_GPG_SECRET_FILE_BASE64`
- `OSSRH_GPG_SECRET_KEY_ID`
- `OSSRH_GPG_SECRET_KEY_PASSWORD`

The Maven Central repository and publication signing are configured only when `signing.keyId` is
present. Normal local builds therefore do not require publishing credentials. The release workflow
publishes directly to Maven Central and does not call `publishToMavenLocal`.

Before creating a release tag, update the project version in the root `build.gradle.kts` and keep
the tag aligned with it, for example version `0.4.0` with tag `v0.4.0`.

Public API changes are checked by Kotlin's built-in ABI validator. Review any intended API change,
then update the committed baseline with:

```powershell
.\gradlew.bat :morph-compose:updateKotlinAbi
```
