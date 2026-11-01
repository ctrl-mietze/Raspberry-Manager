# Third-party notices

Raspberry Manager is an **independent, community-made project** by ctrl-mietze. It is **not an official Raspberry Pi Ltd product**.

## Raspberry Pi ecosystem

“Raspberry Pi”, “Raspberry Pi Imager” and “Raspberry Pi Connect” are names and trademarks associated with Raspberry Pi Ltd. Raspberry Manager does not claim affiliation, endorsement or partnership.

The Android app is an independent implementation informed by the public Raspberry Pi Imager project and public operating-system catalogue. See [the Android upstream notice](android/UPSTREAM.md) and [Raspberry Pi Imager license terms](android/LICENSE-RPI-IMAGER.txt).

The Windows installer may include the **unaltered official Raspberry Pi Imager installer** as a convenience. The user chooses whether to launch it. It is not silently installed, and no storage medium is automatically erased. Raspberry Pi Imager remains subject to its upstream licensing and copyright.

Official site: https://www.raspberrypi.com/software/  
Upstream source: https://github.com/raspberrypi/rpi-imager

## Software dependencies

Windows uses Electron, React, React Flow, xterm.js, ssh2 and other open-source dependencies. Consult `windows/package-lock.json` for the resolved dependency versions and their individual license terms.

The Windows app's official Connect integration runs the provider site in a dedicated, isolated Chromium profile. The service's server implementation is not included in this repository.

## Screenshots and artwork

Product screenshots in `docs/assets/` were captured from the actual Windows application. The custom Raspberry Manager icon and Pi board illustration are project artwork, not official Raspberry Pi logos.

No passwords, OAuth tokens, private keys or actual SSH credentials are distributed with this repository.
