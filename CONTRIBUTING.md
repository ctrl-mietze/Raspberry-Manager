# Contributing to Raspberry Manager

Thanks for making Raspberry Manager better. Feature proposals, debugging notes, UI improvements, translations and tested patches are welcome. 🫐

## Before opening a PR

1. Check existing issues and releases to avoid duplicating work.
2. State whether the change affects **Android**, **Windows**, **the shared Workbench schema**, or **the website**.
3. For Android, preserve the existing native image-writing flow and do not alter disk access semantics without tests.
4. For Windows, preserve Electron context isolation and the preload allowlist. Do **not** turn on renderer Node integration, change Connect's origin allowlist casually, or store credentials.
5. Do not silently auto-flash, auto-install an OS, run Workbench configurations or infer live boot success from a dry-run.
6. Include a before/after screenshot for any visual change and a test or manual verification note for functional changes.
7. Document any platform feature that is currently not equivalent.

## Suggested development workflow

### Windows

```powershell
cd windows
npm ci
npm run build:web
npx tsc --noEmit
npm test
npm run dev
```

To package an installer:

```powershell
npm run build
```

The public source build may open Raspberry Pi's official download page if the optional Imager installer isn't present in `windows/assets`.

### Android

Install Android Studio/JDK 17, open `android/source`, then run:

```bash
cd android/source
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Debug builds use local debug signing. Release builds require appropriate signing material kept out of Git.

## Code style and testing

- Prefer small, reviewable patches, meaningful component naming, accessible controls and responsive layouts.
- Keep app behavior truthful: distinguish a design plan, validation simulation, generated configuration, and real device operations.
- Avoid dependency upgrades without reviewing compatibility and license implications.
- Never commit `node_modules/`, build caches, signing keys, tokens, credential files or personally identifying device logs.
- Run `npm run build:web` and `npx tsc --noEmit` for Windows changes; run Gradle compile/tests for Android changes.

## Translations

English is the main technical documentation language. The repo includes a German README and the website offers German/English copy. Please keep instructions technically equivalent between languages.

## Pull request checklist

- [ ] Scope and affected platform are clear
- [ ] No secret, credential or local filesystem path was committed
- [ ] Manual interaction/screenshot review completed where applicable
- [ ] Relevant tests executed; any skipped tests explained
- [ ] Documentation and screenshots updated
- [ ] No unsupported production claims introduced
