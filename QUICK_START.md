# Quick Start Guide - Speech Coach AI

## Prerequisites Checklist

- [ ] Android Studio installed (latest version)
- [ ] JDK 17 or higher installed
- [ ] Android SDK with API 35 installed
- [ ] Physical device or emulator ready

## Setup Steps (5 minutes)

### 1. Configure SDK Path

Edit `local.properties` and set your Android SDK location:

```properties
# macOS/Linux example:
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk

# Windows example:
sdk.dir=C:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk
```

### 2. Open in Android Studio

1. Launch Android Studio
2. Click **"Open"**
3. Navigate to and select the `speech-coach-ai` folder
4. Wait for Gradle sync to complete (may take a few minutes on first run)

### 3. Run the App

**Option A - Physical Device (Samsung S25 Ultra, etc.):**
1. Enable Developer Options and USB Debugging on your device
2. Connect via USB
3. Click the green **Run** button in Android Studio
4. Select your device from the list

**Option B - Emulator:**
1. Open **Device Manager** in Android Studio
2. Create a new device (Pixel 8 Pro or similar recommended)
3. Click the green **Run** button
4. Select your emulator

### 4. Grant Permissions

When the app launches:
- You'll need to grant microphone permission when you first try to record
- Tap "Allow" when prompted

## What You'll See

The app will display:
- "Speech Coach AI" title
- A description of the app
- A "Start Recording" button
- When recording, you'll see "🎤 Listening..."

## Next Steps for Development

1. **Add Speech Recognition:**
   - Integrate Android SpeechRecognizer API or Google Cloud Speech-to-Text

2. **Implement Audio Recording:**
   - Use MediaRecorder or AudioRecord API
   - Handle permission requests at runtime

3. **Add AI Analysis:**
   - Integrate OpenAI API, Google Cloud, or local ML models
   - Analyze speech patterns, pace, filler words, etc.

4. **Build Out UI:**
   - Add screens for: results, history, settings, onboarding
   - Use Jetpack Navigation for screen transitions

5. **Data Persistence:**
   - Add Room database for storing speech sessions
   - Use DataStore for user preferences

## Quick Commands

```bash
# Build the project
./gradlew build

# Run tests
./gradlew test

# Clean build
./gradlew clean

# Generate debug APK
./gradlew assembleDebug
```

## Common Issues

### "SDK not found"
- Make sure `local.properties` has the correct SDK path
- Verify Android SDK is actually installed at that location

### "Gradle sync failed"
- Check internet connection (Gradle needs to download dependencies)
- Try: File > Invalidate Caches > Restart

### "Device not detected"
- Enable USB Debugging in Developer Options
- Try a different USB cable
- Install device drivers (Windows only)

### "Build failed: API 35 not found"
- Open SDK Manager in Android Studio
- Install "Android API 35" under SDK Platforms

## Useful Resources

- [Android Developers](https://developer.android.com/)
- [Jetpack Compose Documentation](https://developer.android.com/jetpack/compose)
- [Kotlin Documentation](https://kotlinlang.org/docs/home.html)
- [Material Design 3](https://m3.material.io/)

## Need Help?

Check the main `README.md` for more detailed information, or open an issue in the repository.

---

**You're all set! Happy coding! 🚀**

