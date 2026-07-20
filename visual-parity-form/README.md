# All Widgets Visual Check

A single ODK form that renders **every question type + appearance** ODK Collect supports, for a
side-by-side **visual** comparison between Android Collect and the iOS (KotlinCollect) port. It is
NOT a logic/journey test — there are no `relevant`/`required`/`constraint` rules, so you can swipe
freely through all ~78 widgets and eyeball each one on both platforms.

## Files
- `all_widgets_visual.xlsx` — the XLSForm source (edit this).
- `all_widgets_visual.xml` — the compiled + ODK-validated XForm (ready to side-load).
- `all_widgets_visual-media/` — the attachments (images, CSV, XML, GeoJSON, SVG) the form needs.

## How to load

**Side-load into Collect (fastest for a device visual check):**
1. Copy `all_widgets_visual.xml` into the device `…/odk/forms/` (or `…/Android/data/org.odk.collect.android/files/projects/<id>/forms/`).
2. Copy the whole `all_widgets_visual-media/` folder next to it (same name = `<formname>-media`).
3. In Collect: **Fill Blank Form → All Widgets Visual Check**. Do the same on iOS.

**Or via Central / your server:** upload `all_widgets_visual.xlsx`, then attach every file inside
`all_widgets_visual-media/` as form-media attachments, publish, and download to both apps.

## What's covered (11 groups)

1. **Text & number** — text (default, multiline, numbers, url, thousands-sep), integer (default,
   thousands-sep), decimal (default, bearing).
2. **Range** — integer, decimal, vertical, picker, no-ticks, rating.
3. **Date & time** — date (default, month-year, year, no-calendar), time, dateTime, and the
   non-Gregorian calendars: ethiopian, coptic, islamic, bikram-sambat, myanmar, persian.
4. **Geospatial capture** — geopoint (default, placement-map, maps), geotrace (default,
   placement-map), geoshape (default, placement-map).
5. **Media capture** — image (default, new, selfie/new-front, annotate, draw, signature),
   audio (default, quality=voice-only), video (default, selfie), file upload, barcode/QR.
6. **Select one** — default, minimal, columns, columns-pack, columns-2, likert, quick (autoadvance),
   no-buttons, autocomplete, **image choices**, **image-map** (regions.svg).
7. **Select one from file** — **CSV**, **XML**, **GeoJSON**, GeoJSON **map**, CSV **map**.
8. **Select multiple** — default, minimal, columns, no-buttons, autocomplete, **image choices**,
   **from XML file**.
9. **Rank, note, trigger** — rank, plain note, **markdown/output note**, calculate + output note,
   trigger (acknowledge).
10. **Groups** — regular (presentation), **field-list** (one screen, incl. a likert), nested groups.
11. **Repeats** — basic add/remove, **fixed-count (3)**, **nested repeat**.

## Notes / caveats
- The images (`apple/banana/cherry.png`, `regions.svg`) are simple generated placeholders — distinct
  colours/labels so any rendering difference is obvious. Swap in your own if you prefer.
- Capture widgets (image/audio/video/geo) open the device camera/mic/GPS — for a pure visual check
  you only need to confirm the *widget UI* (buttons, layout, labels) matches; you don't have to
  actually capture.
- `select_*_from_file … map` and `image-map` need a configured basemap on both devices to render the
  map/interactive image.
- Generated + validated with pyxform 4.5.0 (ODK Validate: valid; only image `max-pixels` warnings).
