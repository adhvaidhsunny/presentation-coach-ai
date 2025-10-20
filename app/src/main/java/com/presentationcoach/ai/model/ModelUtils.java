/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.presentationcoach.ai.model;

public class ModelUtils {
  // XNNPACK or QNN or Vulkan
  static final int TEXT_MODEL = 1;

  // XNNPACK or Vulkan
  static final int VISION_MODEL = 2;
  public static final int VISION_MODEL_IMAGE_CHANNELS = 3;
  public static final int VISION_MODEL_SEQ_LEN = 2048;
  static final int TEXT_MODEL_SEQ_LEN = 768;

  // MediaTek
  static final int MEDIATEK_TEXT_MODEL = 3;

  // QNN static llama
  static final int QNN_TEXT_MODEL = 4;

  // Whisper models
  static final int WHISPER_ENCODER_MODEL = 5;
  static final int WHISPER_DECODER_MODEL = 6;

  public static int getModelCategory(ModelType modelType, BackendType backendType) {
    if (backendType.equals(BackendType.XNNPACK) || backendType.equals(BackendType.VULKAN)) {
      switch (modelType) {
        case LLAVA_1_5:
          return VISION_MODEL;
        case WHISPER:
        default:
          return TEXT_MODEL;
      }
    } else if (backendType.equals(BackendType.MEDIATEK)) {
      return MEDIATEK_TEXT_MODEL;
    } else if (backendType.equals(BackendType.QUALCOMM)) {
      return QNN_TEXT_MODEL;
    }

    return TEXT_MODEL; // default
  }

  /**
   * Get the specific model category for Whisper encoder/decoder
   */
  public static int getWhisperModelCategory(boolean isEncoder) {
    return isEncoder ? WHISPER_ENCODER_MODEL : WHISPER_DECODER_MODEL;
  }
}

