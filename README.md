# Speech Coach AI

An AI-powered Android application designed to help users improve their speaking skills with real-time feedback and analysis.

## 📱 Device Compatibility

This application is optimized for modern Android devices including:
- Samsung Galaxy S25 Ultra
- Samsung Galaxy S24/S23 series
- Google Pixel devices
- Other devices running Android 8.0 (API 26) and above

## 🚀 Features

- **Modern UI**: Built with Jetpack Compose and Material Design 3
- **Edge-to-Edge Display**: Full support for modern Android displays
- **Dynamic Theming**: Adapts to system theme (Android 12+)
- **Speech Recording**: Ready for audio recording integration
- **AI-Powered Analysis**: Framework ready for speech analysis integration

## 🛠️ Technology Stack

- **Language**: Kotlin 2.0.20
- **UI Framework**: Jetpack Compose
- **Material Design**: Material 3
- **Build System**: Gradle 8.9 with Kotlin DSL
- **Minimum SDK**: Android 8.0 (API 26)
- **Target SDK**: Android 15 (API 35)
- **Compile SDK**: Android 15 (API 35)

## 📋 Prerequisites

Before building the project, ensure you have the following installed:

1. **Android Studio** (Latest version recommended)
   - Download from: https://developer.android.com/studio

2. **Java Development Kit (JDK) 17**
   - Included with Android Studio or download from: https://adoptium.net/

3. **Android SDK**
   - Install via Android Studio SDK Manager
   - Required SDK version: API 35

## 🔧 Setup Instructions

### 1. Clone the Repository

```bash
git clone <repository-url>
cd speech-coach-ai
```

### 2. Configure Android SDK Path

Edit `local.properties` file in the project root and set your Android SDK path:

```properties
sdk.dir=/Users/YOUR_USERNAME/Library/Android/sdk
```

**Note for macOS/Linux users:**
- The SDK is typically located at: `~/Library/Android/sdk` (macOS) or `~/Android/Sdk` (Linux)

**Note for Windows users:**
- The SDK is typically located at: `C:\\Users\\YOUR_USERNAME\\AppData\\Local\\Android\\Sdk`

### 3. Open in Android Studio

1. Launch Android Studio
2. Select **"Open an Existing Project"**
3. Navigate to the `speech-coach-ai` directory
4. Click **"Open"**

Android Studio will automatically:
- Download required Gradle dependencies
- Sync the project
- Index files

### 4. Build the Project

#### Option A: Using Android Studio
1. Wait for Gradle sync to complete
2. Click **Build > Make Project** (or press `Cmd+F9` on macOS / `Ctrl+F9` on Windows/Linux)

#### Option B: Using Command Line

**macOS/Linux:**
```bash
./gradlew build
```

**Windows:**
```cmd
gradlew.bat build
```

## 📲 Running the Application

### On a Physical Device (Samsung S25 Ultra, etc.)

1. **Enable Developer Options** on your device:
   - Go to **Settings > About Phone**
   - Tap **Build Number** 7 times
   - Go back to **Settings > Developer Options**
   - Enable **USB Debugging**

2. **Connect your device** via USB

3. **Run the app**:
   - In Android Studio, select your device from the device dropdown
   - Click the **Run** button (green play icon) or press `Shift+F10`

### On an Emulator

1. **Create an emulator**:
   - Click **Tools > Device Manager**
   - Click **Create Device**
   - Select a device definition (e.g., "Pixel 8 Pro" or custom with S25 Ultra specs)
   - Select a system image (API 35 recommended)
   - Click **Finish**

2. **Run the app**:
   - Select your emulator from the device dropdown
   - Click the **Run** button

## 📁 Project Structure

```
speech-coach-ai/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/speechcoach/ai/
│   │   │   │   ├── MainActivity.kt          # Main entry point
│   │   │   │   └── ui/theme/                # Theme configuration
│   │   │   │       ├── Color.kt             # Color definitions
│   │   │   │       ├── Theme.kt             # App theme
│   │   │   │       └── Type.kt              # Typography
│   │   │   ├── res/
│   │   │   │   ├── values/                  # Resources (strings, colors, themes)
│   │   │   │   ├── xml/                     # XML configurations
│   │   │   │   └── mipmap-*/                # App icons
│   │   │   └── AndroidManifest.xml          # App manifest
│   │   └── test/                            # Unit tests
│   ├── build.gradle.kts                     # App-level build configuration
│   └── proguard-rules.pro                   # ProGuard rules
├── gradle/
│   └── wrapper/                             # Gradle wrapper
├── build.gradle.kts                         # Project-level build configuration
├── settings.gradle.kts                      # Project settings
├── gradle.properties                        # Gradle properties
├── local.properties                         # Local SDK configuration (not in VCS)
└── README.md                                # This file
```

## 🎨 Customization

### Changing App Name
Edit `app/src/main/res/values/strings.xml`:
```xml
<string name="app_name">Your App Name</string>
```

### Changing Package Name
1. Refactor package in Android Studio: Right-click package > Refactor > Rename
2. Update `namespace` in `app/build.gradle.kts`
3. Update `applicationId` in `app/build.gradle.kts`

### Modifying Theme Colors
Edit `app/src/main/java/com/speechcoach/ai/ui/theme/Color.kt`

## 🔐 Permissions

The app currently requests the following permissions (in `AndroidManifest.xml`):

- `RECORD_AUDIO`: For speech recording functionality
- `INTERNET`: For potential cloud-based AI analysis

**Note**: Runtime permissions must be requested in the code before use (Android 6.0+)

## 🧪 Testing

### Run Unit Tests
```bash
./gradlew test
```

### Run Instrumented Tests
```bash
./gradlew connectedAndroidTest
```

## 📦 Building APK

### Debug APK
```bash
./gradlew assembleDebug
```
Output: `app/build/outputs/apk/debug/app-debug.apk`

### Release APK
```bash
./gradlew assembleRelease
```
Output: `app/build/outputs/apk/release/app-release.apk`

**Note**: For release builds, you need to configure signing in `app/build.gradle.kts`

## 🚧 Next Steps / TODOs

- [ ] Implement speech recording functionality
- [ ] Integrate speech-to-text API
- [ ] Add AI-powered speech analysis
- [ ] Implement feedback and coaching features
- [ ] Add user profile and progress tracking
- [ ] Implement data persistence (Room database)
- [ ] Add comprehensive unit and UI tests
- [ ] Configure proper app signing for release builds

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📄 License

This project is created for hackathon purposes. License TBD.

## 💡 Tips for Development

1. **Use Android Studio's built-in tools**:
   - Layout Inspector for UI debugging
   - Profiler for performance analysis
   - Logcat for viewing logs

2. **Keep dependencies updated**:
   - Regularly check for library updates
   - Use `./gradlew dependencyUpdates` (with plugin)

3. **Test on real devices**:
   - Emulators are useful, but real device testing is essential
   - Test on various screen sizes and Android versions

4. **Follow Material Design guidelines**:
   - https://m3.material.io/

## 🆘 Troubleshooting

### Gradle Sync Failed
- Check your internet connection
- Click **File > Invalidate Caches / Restart**
- Delete `.gradle` folder and sync again

### Device Not Detected
- Ensure USB debugging is enabled
- Try different USB cable/port
- Install device-specific USB drivers (Windows)

### Build Errors
- Clean project: **Build > Clean Project**
- Rebuild: **Build > Rebuild Project**
- Update Gradle: Check `gradle-wrapper.properties`

## 📞 Support

For issues and questions, please open an issue in the repository.

---

**Happy Coding! 🎉**

