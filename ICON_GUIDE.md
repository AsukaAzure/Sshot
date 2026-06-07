# Android App Icon Guide: Adaptive Icons, Material You & Themed Icons

> **App:** ScreenshotJanitor (`ssJanitor`)  
> **Your Source PNG:** `/Users/sj/Downloads/ssJanitor.png` (1024×1024)  
> **Target:** Standard Android launcher icon + Material You expressive icon support

---

## Table of Contents

1. [What We Did — Quick Summary](#1-what-we-did--quick-summary)
2. [How Android App Icons Work](#2-how-android-app-icons-work)
3. [Adaptive Icons (Android 8+)](#3-adaptive-icons-android-8)
4. [Density Buckets Explained](#4-density-buckets-explained)
5. [Material You — Themed (Expressive) Icons](#5-material-you--themed-expressive-icons)
6. [Step-by-Step: How to Do This Yourself](#6-step-by-step-how-to-do-this-yourself)
7. [Troubleshooting](#7-troubleshooting)
8. [References](#8-references)

---

## 1. What We Did — Quick Summary

| Step | What | Why |
|------|------|-----|
| 1 | Resized `ssJanitor.png` to 5 density buckets (48–192px) | Android needs different pixel sizes for different screen densities |
| 2 | Converted PNGs to WebP (lossy, q=90) | Smaller APK size with negligible quality loss |
| 3 | Copied into `mipmap-mdpi` through `mipmap-xxxhdpi` | Each folder gets `ic_launcher.webp`, `ic_launcher_round.webp`, `ic_launcher_foreground.webp`, `ic_launcher_monochrome.webp` |
| 4 | Created monochrome (grayscale) versions | Required for Android 13+ themed icons |
| 5 | Wrote `adaptive-icon` XMLs in `mipmap-anydpi/` and `mipmap-anydpi-v26/` | Links the background color, foreground bitmap, and monochrome layer |
| 6 | Added `ic_launcher_background` color to `colors.xml` | Solid brand teal (`#006E6D`) — clean, simple, no gradient needed |
| 7 | Created `ic_notification.xml` vector drawable | Notifications need a simple white-on-transparent icon, not a full-color bitmap |

---

## 2. How Android App Icons Work

### The Manifest Connection

```xml
<application
    android:icon="@mipmap/ic_launcher"
    android:roundIcon="@mipmap/ic_launcher_round">
```

- `@mipmap/ic_launcher` → Android resolves the best density from the `mipmap-*dpi` folders
- `@mipmap/ic_launcher_round` → Same, but used on round launchers (Pixel, etc.)

### mipmap vs drawable

| Resource folder | Use case |
|----------------|---------|
| `res/mipmap-*dpi/` | **Launcher icons only.** Android launchers pick icons at the highest available density |
| `res/drawable/` | Everything else — button icons, notification icons, vector graphics |

**Never put launcher icons in `drawable/`.** They belong in `mipmap/` folders.

---

## 3. Adaptive Icons (Android 8+)

Since API 26 (Android 8.0), Android uses **adaptive icons**. They have three layers:

```
┌────────────────────┐
│   Monochrome       │  ← For themed icons (Android 13+)
│   (transparent bg) │
├────────────────────┤
│   Foreground       │  ← Your app logo (72dp visible within 108dp)
│   (transparent bg) │
├────────────────────┤
│   Background       │  ← Solid color or subtle pattern
│   (solid/gradient) │
└────────────────────┘
```

The launcher applies a **mask** (circle, squircle, rounded square) to all layers together, so shapes near the edges may be clipped. The safe zone is the inner **72dp** of the 108dp canvas.

### Our Adaptive Icon XML

File: `app/src/main/res/mipmap-anydpi/ic_launcher.xml`

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
```

- **Background**: A solid color (`@color/ic_launcher_background` = `#006E6D`), matching our app's primary color. Could also be a gradient drawable.
- **Foreground**: A density-specific bitmap (`@mipmap/ic_launcher_foreground` resolves to `mipmap-xxxhdpi/ic_launcher_foreground.webp` on a high-res screen).
- **Monochrome**: A grayscale version of the icon, used by the system when the user enables themed icons.

### Why `mipmap-anydpi-v26`?

The `-v26` qualifier means "API 26 and above." Adaptive icons only work on API 26+. We duplicate the same XML in two locations:

- `mipmap-anydpi/` — Works on all API levels (pre-26 will fall back to the plain `mipmap-*dpi/` image)
- `mipmap-anydpi-v26/` — Explicitly for API 26+, ensures no confusion

Both point to the same `@mipmap/ic_launcher_foreground` and `@mipmap/ic_launcher_monochrome` references.

---

## 4. Density Buckets Explained

Android classifies screens by **density** (dots per inch). Each density bucket needs a different pixel size.

| Qualifier | Density (dpi) | Scale factor | Icon size | Your file |
|-----------|---------------|--------------|-----------|-----------|
| `mdpi` | ~160 (baseline) | 1× | 48×48 | `mipmap-mdpi/ic_launcher.webp` |
| `hdpi` | ~240 | 1.5× | 72×72 | `mipmap-hdpi/ic_launcher.webp` |
| `xhdpi` | ~320 | 2× | 96×96 | `mipmap-xhdpi/ic_launcher.webp` |
| `xxhdpi` | ~480 | 3× | 144×144 | `mipmap-xxhdpi/ic_launcher.webp` |
| `xxxhdpi` | ~640 | 4× | 192×192 | `mipmap-xxxhdpi/ic_launcher.webp` |

**Why 5 copies?** If you only supply one size, Android will scale it. Scaling up = blurry, scaling down = wasteful. Providing all 5 ensures crisp rendering on every device.

**How we generated them** (using macOS `sips` and `cwebp`):

```bash
# Resize from source 1024×1024 PNG
sips -z 48 48 /path/to/source.png --out /tmp/ic_48.png
sips -z 72 72 /path/to/source.png --out /tmp/ic_72.png
# ... etc.

# Convert to WebP (lossy, quality 90)
cwebp -q 90 /tmp/ic_48.png -o ic_launcher.webp
```

---

## 5. Material You — Themed (Expressive) Icons

### What are Themed Icons?

Introduced in Android 13 (API 33), **themed app icons** (also called "Material You icons" or "expressive icons") allow the system to recolor your app icon to match the user's wallpaper color palette.

**How they work:**

1. User enables "Themed icons" in their launcher settings (Pixel Launcher or similar)
2. The system takes your `monochrome` layer and applies the user's color palette (the "color extractor" from wallpaper)
3. The app icon now matches the user's wallpaper-based theme — blue, green, peach, etc.

### Requirements for Themed Icon Support

1. **API 26+** (adaptive icon required)
2. **A `<monochrome>` layer** in your adaptive icon XML — a single-color (white) version of your logo
3. The icon must also be published to Google Play with the monochrome layer (not relevant for personal builds)

### Our Monochrome Layer

We generated it by converting the source PNG to grayscale:

```bash
convert source.png -colorspace Gray -resize 192x192\! monochrome.png
```

Then included it in all density buckets as `ic_launcher_monochrome.webp` and referenced it in the adaptive XML:

```xml
<monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
```

> **Note for future:** A proper monochrome icon should be a simplified silhouette of your logo, not just a grayscale photo. For complex logos, design a clean single-color version. For ScreenshotJanitor's scissors icon, the grayscale works fine since it's already simple.

### Supported Devices

| Android Version | Themed Icon Support |
|----------------|---------------------|
| 8–12 (API 26–32) | No themed icons, but adaptive icons work (background + foreground) |
| 13+ (API 33+) | Full themed icon support (all three layers) |
| Pixel Launcher (all) | Themed icons toggle available |
| Samsung One UI 5+ | Supports themed icons |
| Other launchers | Varies — some ignore the monochrome layer |

---

## 6. Step-by-Step: How to Do This Yourself

### Prerequisites

- Source icon in PNG format (1024×1024 recommended for best quality)
- macOS with `sips` and `cwebp` installed (or ImageMagick)
- Open terminal in your Android project root

### Step 1: Resize the source PNG

```bash
sips -z 48 48 source.png --out ic_48.png
sips -z 72 72 source.png --out ic_72.png
sips -z 96 96 source.png --out ic_96.png
sips -z 144 144 source.png --out ic_144.png
sips -z 192 192 source.png --out ic_192.png
```

### Step 2: Convert to WebP (smaller files)

```bash
cwebp -q 90 ic_48.png -o ic_launcher.webp
cwebp -q 90 ic_72.png -o ic_launcher.webp
# ...
```

### Step 3: Create monochrome versions

```bash
convert source.png -colorspace Gray -resize 48x48\! ic_mono_48.png
cwebp -q 90 ic_mono_48.png -o ic_launcher_monochrome.webp
# Repeat for all sizes
```

### Step 4: Copy files to project

```
app/src/main/res/
├── mipmap-mdpi/
│   ├── ic_launcher.webp          (48×48)
│   ├── ic_launcher_round.webp    (48×48)
│   ├── ic_launcher_foreground.webp (48×48)
│   └── ic_launcher_monochrome.webp (48×48)
├── mipmap-hdpi/                  (72×72)
├── mipmap-xhdpi/                 (96×96)
├── mipmap-xxhdpi/                (144×144)
└── mipmap-xxxhdpi/               (192×192)
```

### Step 5: Add background color to `res/values/colors.xml`

```xml
<color name="ic_launcher_background">#006E6D</color>
```

### Step 6: Write adaptive-icon XMLs

Create `res/mipmap-anydpi/ic_launcher.xml`:

```xml
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
```

Repeat for `ic_launcher_round.xml`, and copy both into `res/mipmap-anydpi-v26/`.

### Step 7: Clean up old icon files

Remove old vector or bitmap icons that are no longer referenced to avoid `R.drawable.xxx` build errors.

### Step 8: Build and test

```bash
./gradlew assembleDebug
```

Install the APK and check:
1. App icon shows on the home screen
2. Icon looks crisp (not blurry)
3. On Android 13+: enable "Themed icons" in launcher settings → icon should adopt the wallpaper color

---

## 7. Troubleshooting

### "Compilation error: Unresolved reference 'drawable'"

**Cause:** You had code referencing `R.drawable.ic_launcher_foreground` which was the old vector file.  
**Fix:** Either recreate the drawable, or update the code to reference the new notification icon (`R.drawable.ic_notification`).

### Icon appears as a plain green circle

**Cause:** Only the background color is showing; the foreground bitmap isn't found.  
**Fix:** Check that `ic_launcher_foreground.webp` exists in each `mipmap-*dpi/` folder and that the XML path is correct (`@mipmap/ic_launcher_foreground` without extension).

### Icon is blurry

**Cause:** Missing a density bucket, so Android scaled a smaller one up.  
**Fix:** Ensure all 5 density folders have the correct pixel sizes (48, 72, 96, 144, 192).

### Themed icon doesn't work

**Cause 1:** Missing `<monochrome>` layer in the adaptive-icon XML.  
**Cause 2:** Device/launcher doesn't support themed icons (requires Android 13+ and a launcher like Pixel Launcher).  
**Fix:** Add the monochrome layer and test on a Pixel running Android 13+.

### Notification icon is blank / shows a white square

**Cause:** Notifications need a **white-on-transparent** vector drawable, not a full-color bitmap. The system renders small icons in the status bar as a mask.  
**Fix:** Create a simple vector drawable in `res/drawable/ic_notification.xml` with white fill and transparent background.

### App icon in the status bar / recent apps is wrong

**Cause:** The manifest's `android:icon` only controls the **launcher** icon. Most status bar / notification code uses `setSmallIcon()` with a drawable from `res/drawable/`. These are separate.

---

## 8. References

| Topic | Link |
|-------|------|
| Adaptive Icons | https://developer.android.com/develop/ui/views/launch/icon_design_adaptive |
| Material You Themed Icons | https://developer.android.com/about/versions/13/features#themed-app-icons |
| Mipmap resource concept | https://developer.android.com/develop/ui/views/launch/icon_design_adaptive#design-an-adaptive-icon |
| Supported icon formats | https://developer.android.com/develop/ui/views/launch/icon_design_adaptive#format |
| Notification icons | https://developer.android.com/develop/ui/views/notifications/guides/notification-design |
| Android density buckets | https://developer.android.com/training/multiscreen/screendensities#TaskProvideAltBmp |

---

*Generated for ScreenshotJanitor project. Last updated: June 2026*