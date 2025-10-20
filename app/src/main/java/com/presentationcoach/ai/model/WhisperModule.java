/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.presentationcoach.ai.model;

import android.util.Log;
import org.pytorch.executorch.extension.llm.LlmCallback;
import org.pytorch.executorch.extension.llm.LlmModule;

/**
 * A wrapper class for Whisper audio transcription using ExecutorTorch.
 * This class handles both encoder and decoder models for Whisper.
 */
public class WhisperModule implements LlmCallback {
  private static final String TAG = "WhisperModule";
  
  private LlmModule mEncoderModule = null;
  private LlmModule mDecoderModule = null;
  private String mEncoderModelPath = "";
  private String mDecoderModelPath = "";
  private String mTokenizerPath = "";
  private WhisperCallback mCallback = null;
  private boolean mIsLoaded = false;

  /**
   * Constructor for WhisperModule
   * @param encoderModelPath Path to the encoder .pte file
   * @param decoderModelPath Path to the decoder .pte file  
   * @param tokenizerPath Path to the tokenizer file
   * @param callback Callback for transcription results
   */
  public WhisperModule(String encoderModelPath, String decoderModelPath, 
                      String tokenizerPath, WhisperCallback callback) {
    mEncoderModelPath = encoderModelPath;
    mDecoderModelPath = decoderModelPath;
    mTokenizerPath = tokenizerPath;
    mCallback = callback;
  }

  /**
   * Load the Whisper models (encoder and decoder)
   * @return 0 if successful, error code otherwise
   */
  public int load() {
    try {
      // Load encoder model
      mEncoderModule = new LlmModule(
          ModelUtils.getWhisperModelCategory(true), // true for encoder
          mEncoderModelPath,
          mTokenizerPath,
          0.0f);
      
      int encoderResult = mEncoderModule.load();
      if (encoderResult != 0) {
        Log.e(TAG, "Failed to load encoder model: " + encoderResult);
        return encoderResult;
      }

      // Load decoder model  
      mDecoderModule = new LlmModule(
          ModelUtils.getWhisperModelCategory(false), // false for decoder
          mDecoderModelPath,
          mTokenizerPath,
          0.0f);
      
      int decoderResult = mDecoderModule.load();
      if (decoderResult != 0) {
        Log.e(TAG, "Failed to load decoder model: " + decoderResult);
        return decoderResult;
      }

      mIsLoaded = true;
      Log.i(TAG, "Whisper models loaded successfully");
      return 0;
      
    } catch (Exception e) {
      Log.e(TAG, "Error loading Whisper models: " + e.getMessage());
      return -1;
    }
  }

  /**
   * Transcribe audio data
   * @param audioData Audio data as float array
   * @param sampleRate Audio sample rate
   * @param maxLength Maximum sequence length for transcription
   */
  public void transcribe(float[] audioData, int sampleRate, int maxLength) {
    if (!mIsLoaded) {
      Log.e(TAG, "Models not loaded");
      if (mCallback != null) {
        mCallback.onTranscriptionError("Models not loaded");
      }
      return;
    }

    try {
      Log.i(TAG, "Starting transcription...");
      
      // Run encoder
      mEncoderModule.generate("", 0, this, false);
      
      // Run decoder with the encoder output
      mDecoderModule.generate("", maxLength, this, false);
      
    } catch (Exception e) {
      Log.e(TAG, "Error during transcription: " + e.getMessage());
      if (mCallback != null) {
        mCallback.onTranscriptionError(e.getMessage());
      }
    }
  }

  /**
   * Stop the transcription process
   */
  public void stop() {
    if (mEncoderModule != null) {
      mEncoderModule.stop();
    }
    if (mDecoderModule != null) {
      mDecoderModule.stop();
    }
  }

  /**
   * Unload the Whisper models and free resources
   */
  public void unload() {
    try {
      if (mEncoderModule != null) {
        mEncoderModule.stop();
        mEncoderModule = null;
      }
      if (mDecoderModule != null) {
        mDecoderModule.stop();
        mDecoderModule = null;
      }
      mIsLoaded = false;
      Log.i(TAG, "Whisper models stopped and cleared");
    } catch (Exception e) {
      Log.e(TAG, "Error stopping Whisper modules: " + e.getMessage());
    }
  }

  /**
   * Check if models are loaded
   */
  public boolean isLoaded() {
    return mIsLoaded;
  }

  @Override
  public void onResult(String result) {
    if (mCallback != null) {
      mCallback.onTranscriptionResult(result);
    }
  }

  @Override
  public void onStats(String stats) {
    if (mCallback != null) {
      mCallback.onTranscriptionStats(stats);
    }
  }

  /**
   * Callback interface for Whisper transcription results
   */
  public interface WhisperCallback {
    void onTranscriptionResult(String result);
    void onTranscriptionError(String error);
    void onTranscriptionStats(String stats);
  }
}

