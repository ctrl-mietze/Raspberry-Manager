# Raspberry Manager project website

This is the static, no-backend landing page for [Raspberry Manager](https://ctrl-mietze.github.io/Raspberry-Manager/). GitHub Pages serves the `docs/` directory from the `main` branch.

## Structure

- `index.html` – accessible landing-page content, language strings and download links.
- `style.css` – Berry dark-mode design and responsive layouts.
- `app.js` – language selector, mobile navigation, screenshot lightbox, reduced-motion aware reveals.
- `assets/windows-*.png` – screenshots captured from the **actual Windows application**.
- `assets/logo.png` – custom Raspberry Manager project icon.
- `assets/pi5-illustration.svg` – original **illustration** (not an official Raspberry Pi product photo).

## Local preview

From the project root:

```bash
cd docs
python -m http.server 8080
```

Open `http://localhost:8080` in a browser. No JavaScript build system is required for the website.

## Updates

Before changing a download link, upload the matching APK or EXE to GitHub Releases and verify the exact asset name. When creating a new release, update both platform links on this page, the README and release notes. Confirm the link through a real browser.

Keep screenshot alt text meaningful. Do not label a concept visualization or generated illustration as a real app screenshot. Real screenshots and mockups must be clearly differentiated.

The project is independent of Raspberry Pi Ltd and must retain the attribution and third-party licensing statements.
