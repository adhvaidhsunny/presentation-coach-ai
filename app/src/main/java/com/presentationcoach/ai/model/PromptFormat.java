/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 * All rights reserved.
 *
 * This source code is licensed under the BSD-style license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.presentationcoach.ai.model;

public class PromptFormat {

  public static final String SYSTEM_PLACEHOLDER = "{{ system_prompt }}";
  public static final String USER_PLACEHOLDER = "{{ user_prompt }}";

  public static String getSystemPromptTemplate(ModelType modelType) {
    switch (modelType) {
      case LLAVA_1_5:
        return "USER: ";
      default:
        return SYSTEM_PLACEHOLDER;
    }
  }

  public static String getUserPromptTemplate(ModelType modelType) {
    switch (modelType) {
      case LLAVA_1_5:
      default:
        return USER_PLACEHOLDER;
    }
  }

  public static String getStopToken(ModelType modelType) {
    switch (modelType) {
      case LLAVA_1_5:
        return "</s>";
      default:
        return "";
    }
  }

  public static String getLlavaPresetPrompt() {
    return "A chat between a curious human and an artificial intelligence assistant. The assistant"
        + " gives helpful, detailed, and polite answers to the human's questions. USER: ";
  }
  
  public static String getPresentationCoachSystemPrompt() {
    return "You are a professional presentation coach AI. Analyze the presenter's performance and provide a grade (A, B, C, D, or F) for each of the following aspects: " +
           "1. Eye Contact - Are they looking at the camera/audience? " +
           "2. Framing - Is the person properly centered and visible? " +
           "3. Posture - Do they have good posture and body language? " +
           "4. Lighting - Is the lighting adequate and flattering? " +
           "Provide your response in this exact format:\n" +
           "Eye Contact: [Grade]\n" +
           "Framing: [Grade]\n" +
           "Posture: [Grade]\n" +
           "Lighting: [Grade]\n" +
           "Explanation: [Brief explanation of the grades]";
  }
}

