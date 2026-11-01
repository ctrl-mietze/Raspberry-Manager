# Changelog

All significant public changes are described here. Versions across platforms identify the shared release train; implementation details may differ by operating system.

## [10.3.0] — 2026-11-01

### Shared repository
- Introduced a monorepo with Android and Windows source, GitHub Pages website, security policy, contributing guidelines, build instructions and packaged release assets.
- Added genuine Windows screenshots, custom artwork, SHA-256 checksums, platform guidance and explicit feature-parity notes.

### Android (based on v10.2.0)
- Removed the automatic “What would you like to do?” startup dialog.
- Introduced **Ready · Continue** with separate EmulateLoad diagnostic and native Imager routes.
- Reworked Pi Magic Workbench layout for portrait and landscape, including more compact toolbars, adaptive side rail and inspector.
- Added block search, focus selection, connected selection, additional alignment/distribution tools and report copying.
- Improved native Raspberry Pi Connect device list with search, filtering and responsive cards.
- Retained the main header USB icon and native Imager launch.
- Earlier validation: 279 JVM tests, 0 failed, 1 skipped; 33 Connect DOM tests and 18 clipboard tests passed. Live hardware flashing and Connect account sign-in were not part of that automated test run.

### Windows
- Added native Windows application with Electron/React.
- Added image stack designer and dependency checks with **Ready · Continue** flow.
- Added visual Workbench built with React Flow and 68 catalog component types.
- Added project templates, JSON import/export, SVG diagrams, validation and simulated dependency scenarios.
- Added local SSH profile manager, fingerprint approval and interactive xterm.js terminal.
- Added isolated embedded Raspberry Pi Connect official provider integration, native device presentation and official WebRTC session viewport.
- Added read-only disk inventory, native Imager launch, optional official Imager installer handoff and app settings.
- Created Windows x64 NSIS installer. Unsigned personal build; hardware and account integration need real-device validation.

## [10.2.0] — 2026-10-15 (historical Android baseline)

- Refined portrait/landscape Workbench, catalog and Minecraft selection tree.
- Preserved original Pi Magic Image Designer while adding responsive editor behavior and regression tests.

## [10.1.0] — 2026-10-15 (historical Android baseline)

- Expanded Pi Magic Workbench, project management, validation and export behavior.
