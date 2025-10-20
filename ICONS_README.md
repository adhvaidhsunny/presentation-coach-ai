# App Icons Guide

## Important Note

This project currently uses the **vector drawable** and **adaptive icon** approach for app icons, which is the modern Android standard. The icons are defined in XML files rather than bitmap images.

## Current Icon Setup

The app icon is configured using:

1. **Adaptive Icons** (Android 8.0+):
   - `/app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
   - `/app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml`

2. **Vector Foreground**:
   - `/app/src/main/res/drawable/ic_launcher_foreground.xml`

3. **Background Color**:
   - Uses `@color/primary_blue` from `colors.xml`

## How to Add Custom Icons

If you want to replace the default icons with custom designs, you have two options:

### Option 1: Use Android Studio's Asset Studio (Recommended)

1. Right-click on `app` in the Project view
2. Select **New > Image Asset**
3. Choose your icon type (Launcher Icons)
4. Upload your icon image (PNG or SVG)
5. Customize foreground and background layers
6. Click **Next** and **Finish**

Android Studio will automatically generate all necessary icon sizes and densities.

### Option 2: Manually Add Icon Files

If you prefer to manually add icon images, you need to create icon files for different densities:

```
app/src/main/res/
├── mipmap-mdpi/          (48x48 px)
│   ├── ic_launcher.webp
│   └── ic_launcher_round.webp
├── mipmap-hdpi/          (72x72 px)
│   ├── ic_launcher.webp
│   └── ic_launcher_round.webp
├── mipmap-xhdpi/         (96x96 px)
│   ├── ic_launcher.webp
│   └── ic_launcher_round.webp
├── mipmap-xxhdpi/        (144x144 px)
│   ├── ic_launcher.webp
│   └── ic_launcher_round.webp
└── mipmap-xxxhdpi/       (192x192 px)
    ├── ic_launcher.webp
    └── ic_launcher_round.webp
```

**Note**: When adding bitmap icons manually, you should also keep or remove the adaptive icon XML files in `mipmap-anydpi-v26/` to avoid conflicts.

## Recommended Icon Design

- **Format**: PNG or WebP (WebP is preferred for smaller file size)
- **Background**: Transparent or solid color
- **Design**: Follow Material Design icon guidelines
- **Safe Zone**: Keep important content within the safe zone (avoid edges)

## Useful Tools

- **Android Asset Studio**: https://romannurik.github.io/AndroidAssetStudio/
- **Figma**: For designing custom icons
- **GIMP/Photoshop**: For image editing
- **Icon Kitchen**: https://icon.kitchen/

## Testing Your Icons

After adding new icons:

1. Clean and rebuild the project
2. Uninstall the app from your device/emulator
3. Reinstall and check the app icon on the home screen
4. Test on different Android versions and launchers

## Current Icon Appearance

The current setup shows a simple circular icon with a microphone-like design on a blue background. This is a placeholder and should be replaced with your custom branding before release.

