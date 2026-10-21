# Play Store assets

- `icon-512.png`: the store icon, 512×512, full bleed (Google Play rounds the corners).
- `feature-graphic-1024x500.png`: the feature graphic shown at the top of the store listing.

Both are rendered from the launcher icon's vectors:

```
python3 tools/play/render_assets.py /tmp/play
magick -density 300 -background none /tmp/play/icon.svg -resize 512x512 -depth 8 -alpha off dashboard-app/art/play/icon-512.png
magick -background none /tmp/play/feature.svg dashboard-app/art/play/feature-graphic-1024x500.png
```

Phone screenshots (at least 2, 9:16) are taken from the app against the test server (`tools/test-ha/`), never a real home.
