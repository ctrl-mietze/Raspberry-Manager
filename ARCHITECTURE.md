# Raspberry Manager architecture

Raspberry Manager is a two-platform application sharing a design language and planning model. This document separates actual device interactions from review-only project planning.

## 1. Key components

| Component | Android | Windows | Trust boundary |
| :-- | :-- | :-- | :-- |
| Main app UI | Jetpack Compose | React in Electron renderer | UI never requires unconditional native privilege |
| Workbench document | Kotlin data model | TypeScript data model | Bounded `raspberry-workbench` JSON schema v1 |
| Workbench validation | Kotlin rule engine | TypeScript rule engine | Pure planning; does not execute downloaded binaries |
| Storage inventory | Android storage/USB APIs | Windows CIM / PowerShell inventory | Read-only listing |
| Image writing | Native Android Imager | Official Raspberry Pi Imager | Explicit user-selected media and erase confirmation |
| SSH | Android SSH engine | ssh2 / xterm.js | Explicit host fingerprint verification |
| Raspberry Connect | Isolated Android WebView process | Electron WebContentsView with isolated partition | Official Pi ID origin + official WebRTC provider |
| Website | — | Static HTML/CSS/JS | No credential or device APIs |

## 2. Windows process and data flow

```text
┌────────────────────────────────────────────────────────────┐
│  React renderer: Home / Builder / Workbench / Devices       │
│  - design editing, state visualization, file previews       │
│  - no unrestricted Node.js integration                      │
└──────────────────────┬─────────────────────────────────────┘
                       │ Typed, allowlisted IPC
┌──────────────────────▼─────────────────────────────────────┐
│  Electron main process                                     │
│  - workspace persistence with atomic file replacement       │
│  - SSH host verification and ssh2 client                     │
│  - read-only Windows disk inventory                          │
│  - launch installed or optional official Raspberry Imager     │
│  - manage embedded Connect WebContentsView                   │
└────────────┬─────────────────────┬──────────────────────────┘
             │                     │
             ▼                     ▼
      Authorized SSH host      Official Raspberry Pi ID +
                               Connect provider WebRTC
```

**Sensitive data:** SSH passwords are provided per session; only profile metadata and optional key path persist. Trusted SSH fingerprints persist to prevent silent host changes. Raspberry Pi ID sessions remain provider-controlled in the dedicated embedded browser profile.

## 3. What Workbench does not do

A Workbench graph is a **declaration** of desired systems and dependencies. Validation/simulation can identify obvious constraints and illustrate dependency execution order, but cannot ensure that an arbitrary image boots on real Pi hardware.

A JSON/SVG export is not a raw disk image, partition table or ELF executable. Adding an OS, kernel or service block does not download or execute that component.

## 4. Cross-platform schema

The portable interchange format uses:

```json
{
  "format": "raspberry-workbench",
  "schema": 1,
  "id": "example-project",
  "name": "Demo",
  "board": "Pi 5",
  "architecture": "arm64",
  "cardMiB": 65536,
  "memoryMiB": 8192,
  "bootSource": "sd",
  "network": "online",
  "targetOs": "linux",
  "activeOsId": "",
  "grid": true,
  "snap": true,
  "minimap": true,
  "nodes": [],
  "links": []
}
```

Importing a project should never execute arbitrary code, perform disk writes or make a network request merely because an untrusted JSON field requests it.

## 5. Electron boundary

`desktop/main.cjs` owns OS access. `desktop/preload.cjs` exposes a narrow API. The renderer receives structured data and requests actions; it never gets raw filesystem access or privileged shell execution.

The official Connect provider is **not** a native password form. Authentication and session UI are supplied by the isolated browser profile and remote WebRTC client.

## 6. Package and release

The Android APK and Windows installer are distributed as **GitHub Release assets** to avoid bloating Git history. SHA-256 checksums are published with the exact asset names.

The optional upstream Imager Windows installer is not part of the checked-in source. It remains a separate, unchanged upstream package with its own licensing obligations.
