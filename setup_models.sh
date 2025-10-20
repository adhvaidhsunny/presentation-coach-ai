#!/bin/bash

# Script to set up Presentation Coach model files on Android device
# This script copies the model files to the device

echo "Setting up Presentation Coach model files on Android device..."

# Check if ADB is available
if ! command -v adb &> /dev/null; then
    echo "Error: ADB (Android Debug Bridge) is not installed or not in PATH"
    echo "Please install Android SDK and add ADB to your PATH"
    exit 1
fi

# Check if device is connected
if ! adb devices | grep -q "device$"; then
    echo "Error: No Android device connected or device not authorized"
    echo "Please connect your device and enable USB debugging"
    exit 1
fi

# Create the target directory on the device
echo "Creating directory /data/local/tmp/llama/ on device..."
adb shell "mkdir -p /data/local/tmp/llama/"

# Copy model files from models to device
echo "Copying model files to device..."

# Copy the main model files (REQUIRED)
if [ -f "models/llava.pte" ]; then
    echo "Copying llava.pte..."
    adb push models/llava.pte /data/local/tmp/llama/
    echo "✓ llava.pte copied successfully"
else
    echo "WARNING: llava.pte not found in models directory"
fi

# Copy Whisper model files (OPTIONAL)
if [ -f "models/whisper_tiny_en_encoder_xnnpack.pte" ]; then
    echo "Copying whisper encoder..."
    adb push models/whisper_tiny_en_encoder_xnnpack.pte /data/local/tmp/llama/
    echo "✓ whisper encoder copied successfully"
else
    echo "WARNING: whisper encoder not found in models directory"
fi

if [ -f "models/whisper_tiny_en_decoder_xnnpack.pte" ]; then
    echo "Copying whisper decoder..."
    adb push models/whisper_tiny_en_decoder_xnnpack.pte /data/local/tmp/llama/
    echo "✓ whisper decoder copied successfully"
else
    echo "WARNING: whisper decoder not found in models directory"
fi

# Copy LLaVA tokenizer files
if [ -f "models/llava_tokenizer.json" ]; then
    echo "Copying LLaVA tokenizer.json..."
    adb push models/llava_tokenizer.json /data/local/tmp/llama/llava_tokenizer.json
    echo "✓ LLaVA tokenizer.json copied successfully"
else
    echo "WARNING: llava_tokenizer.json not found in models directory"
fi

if [ -f "models/llava_tokenizer.model" ]; then
    echo "Copying LLaVA tokenizer.model..."
    adb push models/llava_tokenizer.model /data/local/tmp/llama/llava_tokenizer.model
    echo "✓ LLaVA tokenizer.model copied successfully"
else
    echo "WARNING: llava_tokenizer.model not found in models directory"
fi

# Copy Whisper tokenizer files
if [ -f "models/whisper_tokenizer.json" ]; then
    echo "Copying Whisper tokenizer.json..."
    adb push models/whisper_tokenizer.json /data/local/tmp/llama/whisper_tokenizer.json
    echo "✓ Whisper tokenizer.json copied successfully"
else
    echo "WARNING: whisper_tokenizer.json not found in models directory"
fi

if [ -f "models/whisper_tokenizer.model" ]; then
    echo "Copying Whisper tokenizer.model..."
    adb push models/whisper_tokenizer.model /data/local/tmp/llama/whisper_tokenizer.model
    echo "✓ Whisper tokenizer.model copied successfully"
else
    echo "WARNING: whisper_tokenizer.model not found in models directory"
fi

if [ -f "models/whisper_tokenizer_config.json" ]; then
    echo "Copying Whisper tokenizer_config.json..."
    adb push models/whisper_tokenizer_config.json /data/local/tmp/llama/whisper_tokenizer_config.json
    echo "✓ Whisper tokenizer_config.json copied successfully"
else
    echo "WARNING: whisper_tokenizer_config.json not found in models directory"
fi

# Verify files were copied
echo "Verifying files on device..."
adb shell "ls -la /data/local/tmp/llama/"

echo ""
echo "✅ Setup complete! All model files have been copied to your device."
echo ""
echo "📱 Next steps:"
echo "1. Build and install the app: ./gradlew installDebug"
echo "2. Open the Presentation Coach app on your phone"
echo "3. Grant camera and microphone permissions"
echo "4. Tap 'Start Presentation Analysis'"
echo "5. The app will automatically load models and start analyzing"
echo ""
