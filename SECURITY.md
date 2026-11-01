# Security policy

## Scope

Raspberry Manager includes an SSH client, an in-app Raspberry Pi Connect browser and access to disk imaging tools. These deserve extra care. Please **do not post credentials, recovery codes, SSH keys, cookie values, signed session URLs, device serial numbers or exploit payloads** in public issues.

## Supported versions

| Version | Branch | Support |
| :-- | :-- | :-- |
| Android 10.3.0 | `main/android` | Current public preview |
| Windows 10.3.0 | `main/windows` | Current public preview |
| Older Workbench / Manager builds | — | Historical, limited support |

## Security properties

- The Windows SSH terminal uses the `ssh2` library and requires confirmation for unknown server host fingerprints. Known fingerprint changes are rejected.
- SSH passwords are used for connecting, not persisted by Raspberry Manager's profile store. If a private key is selected, its path is stored; the key bytes are read only to establish the connection.
- Connect authentication is provided by the official Raspberry Pi ID page in an isolated app-controlled WebView/Chromium session. No separate Raspberry Manager password field, cross-browser cookie extraction or arbitrary authentication-token API is present.
- JavaScript-to-native actions are validated against a finite set of known provider actions rather than executing arbitrary page instructions.
- The Windows drive list is **read-only**. Actual writes occur in the official Raspberry Pi Imager, with explicit device selection and confirmation. A Workbench design is **not** a raw disk image.

## Reporting a vulnerability

If the issue is not sensitive, open a GitHub issue with a minimal reproduction, affected version and sanitized logs.

For security-sensitive problems, use GitHub's **Report a vulnerability** / private vulnerability reporting option when enabled for this repository. Do not publish zero-days, token leakage or unredacted personal records publicly.

Please include: affected build/version, precise reproduction conditions, expected result, actual result and a redacted proof-of-concept or trace.

## Build authenticity

Release APK and EXE assets are accompanied by SHA-256 checksums. Check that their hashes match the release's `SHA256SUMS.txt`. Windows binaries may be **unsigned** and trigger SmartScreen; an unsigned status is not itself proof of malware or safety.
