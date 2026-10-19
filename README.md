# Dhikr — website branch

This branch holds only the static website for the Dhikr Android app. It has no app code.
The app source lives on `main` (and its feature branches).

## What is here

| File | Purpose |
|------|---------|
| `index.html` | Landing page: app description and download link |
| `privacy.html` | Privacy policy |
| `terms.html` | Terms of use |
| `style.css` | Shared styles, light and dark mode |
| `assets/` | Icon and social preview image |
| `.nojekyll` | Tells GitHub Pages to serve files as-is |

## Deployment

Pushing to `website` runs `.github/workflows/pages.yml`, which publishes the repo root to GitHub Pages.
In the repo settings, set Pages source to **GitHub Actions**.

## Working on it

- Edit the HTML/CSS directly; there is no build step. Open `index.html` in a browser to preview.
- Keep this branch site-only. Do not merge it into `main`, or the app code there will be deleted.
- If the privacy policy changes in the app repo, update `privacy.html` here to match.
