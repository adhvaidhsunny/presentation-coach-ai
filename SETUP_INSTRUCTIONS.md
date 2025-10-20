# Presentation Coach - Setup Instructions

## Prerequisites

1. **Android Studio** installed
2. **Android SDK** at `/Users/kosisochukwuasuzu/Library/Android/sdk`
3. **ADB** (Android Debug Bridge) in your PATH
4. **Android device** with USB debugging enabled (Android 9.0+ / API 28+)

## Quick Setup

### 1. Push Models to Device

The models are stored in the `models/` directory and need to be pushed to your Android device:

```bash
# Make script executable (first time only)
chmod +x setup_models.sh

# Push models to device
./setup_models.sh
```

This will copy the following files to `/data/local/tmp/llama/` on your device:
- `llava.pte` (3.6GB) - Vision model
- `llava_tokenizer.model` - Tokenizer
- `whisper_tiny_en_encoder_xnnpack.pte` (31MB) - Audio encoder
- `whisper_tiny_en_decoder_xnnpack.pte` (113MB) - Audio decoder
- `whisper_tokenizer.model` - Whisper tokenizer

### 2. Build and Install App

```bash
# Build the app
./gradlew assembleDebug

# Install on device
./gradlew installDebug

# Or build and install in one step
./gradlew installDebug
```

### 3. Run the App

1. Open "Presentation Coach" on your device
2. Grant camera and microphone permissions
3. Tap "Start Presentation Analysis"
4. Wait for models to load (3-5 seconds first time)
5. Watch real-time feedback appear!

## Model Storage

### Development Setup
- **Models Location (local)**: `models/` directory
- **Device Location**: `/data/local/tmp/llama/`
- **Models NOT in assets**: Keeps APK size small (~5MB instead of ~3.7GB)

### Why This Approach?
- ✅ Small APK size (models stored separately)
- ✅ Easy model updates (just run setup script)
- ✅ Matches LlamaDemo architecture
- ✅ Fast development iteration

## Troubleshooting

### "SDK location not found" Error
**Solution**: Ensure `local.properties` exists with:
```
sdk.dir=/Users/kosisochukwuasuzu/Library/Android/sdk
```

### "Model files not found" Error in App
**Solution**: Run `./setup_models.sh` to push models to device

### ADB Connection Issues
```bash
# Check device is connected
adb devices

# Should show:
# List of devices attached
# <device-id>    device

# If not authorized, check phone for authorization prompt
```

### Build Fails
```bash
# Clean build
./gradlew clean

# Rebuild
./gradlew assembleDebug
```

## Model Files

### LLaVA (Vision Analysis)
- **llava.pte**: 3.6GB - Main vision-language model
- **llava_tokenizer.model**: 488KB - Text tokenizer
- **llava_tokenizer.json**: 3.5MB - Tokenizer config

### Whisper (Audio Transcription)
- **whisper_tiny_en_encoder_xnnpack.pte**: 31MB - Audio encoder
- **whisper_tiny_en_decoder_xnnpack.pte**: 113MB - Audio decoder
- **whisper_tokenizer.model**: Empty placeholder
- **whisper_tokenizer.json**: 2.4MB - Tokenizer
- **whisper_tokenizer_config.json**: 276KB - Config

## Development Workflow

### After Code Changes
```bash
./gradlew installDebug
```

### After Model Updates
```bash
./setup_models.sh
```

### Full Clean Build
```bash
./gradlew clean
./setup_models.sh
./gradlew installDebug
```

## Architecture

```
speech-coach-ai/
├── models/                          # Model files (not in git, not in APK)
│   ├── llava.pte
│   ├── llava_tokenizer.model
│   └── whisper_*.pte
├── setup_models.sh                  # Push models to device
├── local.properties                 # Android SDK path (not in git)
└── app/
    └── src/main/
        └── java/com/presentationcoach/ai/
            ├── model/                # Model integration layer
            └── CameraPreviewScreen.kt  # Loads from /data/local/tmp/llama/
```

## App Features

- 🎥 **Real-time visual analysis** via LLaVA model
- 📊 **Grading** on 4 aspects: Eye Contact, Framing, Posture, Lighting
- 🎤 **Audio transcription** via Whisper model (optional)
- 💚 **On-device processing** - No internet required
- ⚡ **Fast inference** - ~500ms per analysis after warmup

## Notes

- First analysis takes 3-5 seconds (model warmup)
- Analysis runs every 3 seconds during presentation
- Models run entirely on-device (privacy-friendly)
- Requires 4GB+ RAM for best performance

