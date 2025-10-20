package com.presentationcoach.ai.model;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Looper;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import org.pytorch.executorch.extension.llm.LlmCallback;
import org.pytorch.executorch.extension.llm.LlmModule;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Main model manager for Presentation Coach
 * Handles both LLaVA (visual analysis) and Whisper (audio transcription)
 */
public class PresentationCoachModel implements LlmCallback {
    private static final String TAG = "PresentationCoachModel";
    
    private LlmModule mLlavaModule = null;
    private WhisperModule mWhisperModule = null;
    private Context mContext;
    private PresentationCoachCallback mCallback = null;
    private Executor mExecutor;
    private Handler mMainHandler;
    private boolean mIsLlavaLoaded = false;
    private boolean mIsWhisperLoaded = false;
    private boolean mIsLlavaReady = false;  // Ready for inference after prefill
    private StringBuilder mCurrentResponse = new StringBuilder();
    private String mCurrentModelPath = "";
    private String mCurrentTokenizerPath = "";
    
    public interface PresentationCoachCallback {
        void onModelLoadingStarted(String modelName);
        void onModelLoaded(String modelName, boolean success, String error);
        void onAnalysisResult(String eyeContact, String framing, String posture, String lighting, String explanation);
        void onTranscriptionResult(String transcription);
        void onError(String error);
    }
    
    public PresentationCoachModel(Context context, PresentationCoachCallback callback) {
        mContext = context.getApplicationContext();
        mCallback = callback;
        mExecutor = Executors.newSingleThreadExecutor();
        mMainHandler = new Handler(Looper.getMainLooper());
        
        // Set up native library paths for ExecutorTorch
        try {
            Os.setenv("ADSP_LIBRARY_PATH", context.getApplicationInfo().nativeLibraryDir, true);
            Os.setenv("LD_LIBRARY_PATH", context.getApplicationInfo().nativeLibraryDir, true);
        } catch (ErrnoException e) {
            Log.e(TAG, "Error setting native library path: " + e.getMessage());
        }
    }
    
    /**
     * Load the LLaVA model for visual analysis
     */
    public void loadLlavaModel(String modelPath, String tokenizerPath) {
        mExecutor.execute(() -> {
            try {
                // Validate model paths first
                if (modelPath == null || modelPath.isEmpty()) {
                    String error = "Invalid model path provided";
                    Log.e(TAG, error);
                    mMainHandler.post(() -> {
                        if (mCallback != null) {
                            mCallback.onModelLoaded("LLaVA", false, error);
                        }
                    });
                    return;
                }
                
                if (tokenizerPath == null || tokenizerPath.isEmpty()) {
                    String error = "Invalid tokenizer path provided";
                    Log.e(TAG, error);
                    mMainHandler.post(() -> {
                        if (mCallback != null) {
                            mCallback.onModelLoaded("LLaVA", false, error);
                        }
                    });
                    return;
                }
                
                mMainHandler.post(() -> {
                    if (mCallback != null) {
                        mCallback.onModelLoadingStarted("LLaVA");
                    }
                });
                
                Log.i(TAG, "Loading LLaVA model: " + modelPath);
                Log.i(TAG, "Using tokenizer: " + tokenizerPath);
                
                // Reset state
                mIsLlavaLoaded = false;
                mIsLlavaReady = false;
                
                // Stop existing module if any
                if (mLlavaModule != null) {
                    Log.d(TAG, "Stopping existing LLaVA module...");
                    mLlavaModule.stop();
                    mLlavaModule = null;
                }
                
                // Create and load LLaVA module with proper configuration
                Log.d(TAG, "Creating LLaVA module with VISION_MODEL type");
                mLlavaModule = new LlmModule(
                    ModelUtils.VISION_MODEL,  // Use VISION_MODEL type for LLaVA
                    modelPath,
                    tokenizerPath,
                    0.8f  // Temperature for balanced responses
                );
                
                Log.d(TAG, "Loading LLaVA model into memory...");
                int result = mLlavaModule.load();
                
                if (result == 0) {
                    Log.i(TAG, "LLaVA model loaded successfully, starting prefill...");
                    
                    // Prefill the preset prompt - CRITICAL for LLaVA to work properly
                    String presetPrompt = PromptFormat.getLlavaPresetPrompt();
                    Log.d(TAG, "Prefilling with preset prompt: " + presetPrompt);
                    mLlavaModule.prefillPrompt(presetPrompt);
                    
                    // Mark as loaded and ready
                    mIsLlavaLoaded = true;
                    mIsLlavaReady = true;
                    mCurrentModelPath = modelPath;
                    mCurrentTokenizerPath = tokenizerPath;
                    
                    Log.i(TAG, "LLaVA model fully initialized and ready for inference");
                    
                    mMainHandler.post(() -> {
                        if (mCallback != null) {
                            mCallback.onModelLoaded("LLaVA", true, null);
                        }
                    });
                } else {
                    String error = "Failed to load LLaVA model. Error code: " + result;
                    Log.e(TAG, error);
                    mIsLlavaLoaded = false;
                    mIsLlavaReady = false;
                    
                    mMainHandler.post(() -> {
                        if (mCallback != null) {
                            mCallback.onModelLoaded("LLaVA", false, error);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception loading LLaVA model: " + e.getMessage());
                e.printStackTrace();
                mIsLlavaLoaded = false;
                mIsLlavaReady = false;
                
                mMainHandler.post(() -> {
                    if (mCallback != null) {
                        mCallback.onModelLoaded("LLaVA", false, e.getMessage());
                    }
                });
            }
        });
    }
    
    /**
     * Load the Whisper model for audio transcription
     */
    public void loadWhisperModel(String encoderPath, String decoderPath, String tokenizerPath) {
        mExecutor.execute(() -> {
            try {
                mMainHandler.post(() -> {
                    if (mCallback != null) {
                        mCallback.onModelLoadingStarted("Whisper");
                    }
                });
                
                Log.i(TAG, "Loading Whisper model...");
                
                // Stop existing module if any
                if (mWhisperModule != null) {
                    mWhisperModule.unload();
                    mWhisperModule = null;
                }
                
                // Create Whisper module
                mWhisperModule = new WhisperModule(
                    encoderPath,
                    decoderPath,
                    tokenizerPath,
                    new WhisperModule.WhisperCallback() {
                        @Override
                        public void onTranscriptionResult(String result) {
                            mMainHandler.post(() -> {
                                if (mCallback != null) {
                                    mCallback.onTranscriptionResult(result);
                                }
                            });
                        }
                        
                        @Override
                        public void onTranscriptionError(String error) {
                            mMainHandler.post(() -> {
                                if (mCallback != null) {
                                    mCallback.onError("Transcription error: " + error);
                                }
                            });
                        }
                        
                        @Override
                        public void onTranscriptionStats(String stats) {
                            // Handle stats if needed
                            Log.d(TAG, "Transcription stats: " + stats);
                        }
                    }
                );
                
                int result = mWhisperModule.load();
                
                if (result == 0) {
                    mIsWhisperLoaded = true;
                    Log.i(TAG, "Whisper model loaded successfully");
                    
                    mMainHandler.post(() -> {
                        if (mCallback != null) {
                            mCallback.onModelLoaded("Whisper", true, null);
                        }
                    });
                } else {
                    String error = "Failed to load Whisper model. Error code: " + result;
                    Log.e(TAG, error);
                    
                    mMainHandler.post(() -> {
                        if (mCallback != null) {
                            mCallback.onModelLoaded("Whisper", false, error);
                        }
                    });
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception loading Whisper model: " + e.getMessage());
                mMainHandler.post(() -> {
                    if (mCallback != null) {
                        mCallback.onModelLoaded("Whisper", false, e.getMessage());
                    }
                });
            }
        });
    }
    
    /**
     * Analyze a camera frame for presentation skills
     */
    public void analyzeFrame(Bitmap frame) {
        // Validate model is loaded and ready
        if (!mIsLlavaLoaded || !mIsLlavaReady || mLlavaModule == null) {
            Log.w(TAG, "LLaVA model not ready for analysis. Loaded: " + mIsLlavaLoaded + ", Ready: " + mIsLlavaReady);
            return;
        }
        
        if (frame == null) {
            Log.w(TAG, "Null frame provided, skipping analysis");
            return;
        }
        
        Log.d(TAG, "Analyzing frame: " + frame.getWidth() + "x" + frame.getHeight());
        
        mExecutor.execute(() -> {
            try {
                // Convert bitmap to ETImage
                ETImage etImage = new ETImage(frame, 336);  // LLaVA uses 336x336
                
                // Prefill the image
                Log.d(TAG, "Prefilling image for analysis...");
                mLlavaModule.prefillImages(
                    etImage.getInts(),
                    etImage.getWidth(),
                    etImage.getHeight(),
                    ModelUtils.VISION_MODEL_IMAGE_CHANNELS
                );
                
                // Generate analysis
                mCurrentResponse = new StringBuilder();
                String prompt = PromptFormat.getPresentationCoachSystemPrompt();
                
                Log.d(TAG, "Starting analysis generation...");
                mLlavaModule.generate(
                    prompt,
                    ModelUtils.VISION_MODEL_SEQ_LEN,
                    this,
                    false
                );
                
            } catch (Exception e) {
                Log.e(TAG, "Error analyzing frame: " + e.getMessage());
                mMainHandler.post(() -> {
                    if (mCallback != null) {
                        mCallback.onError("Analysis error: " + e.getMessage());
                    }
                });
            }
        });
    }
    
    /**
     * Transcribe audio data
     */
    public void transcribeAudio(float[] audioData, int sampleRate) {
        if (!mIsWhisperLoaded || mWhisperModule == null) {
            Log.w(TAG, "Whisper model not loaded, skipping transcription");
            return;
        }
        
        mExecutor.execute(() -> {
            try {
                mWhisperModule.transcribe(audioData, sampleRate, 448);
            } catch (Exception e) {
                Log.e(TAG, "Error transcribing audio: " + e.getMessage());
                mMainHandler.post(() -> {
                    if (mCallback != null) {
                        mCallback.onError("Transcription error: " + e.getMessage());
                    }
                });
            }
        });
    }
    
    @Override
    public void onResult(String result) {
        if (result.equals(PromptFormat.getStopToken(ModelType.LLAVA_1_5))) {
            // Analysis complete, parse results
            parseAndDeliverResults();
            return;
        }
        
        mCurrentResponse.append(result);
    }
    
    @Override
    public void onStats(String stats) {
        Log.d(TAG, "Model stats: " + stats);
    }
    
    private void parseAndDeliverResults() {
        String response = mCurrentResponse.toString();
        Log.d(TAG, "Analysis complete: " + response);
        
        // Parse the response to extract grades
        String eyeContact = "B";
        String framing = "B";
        String posture = "B";
        String lighting = "B";
        String explanation = "Analysis complete.";
        
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.contains("Eye Contact:")) {
                    eyeContact = extractGrade(line);
                } else if (line.contains("Framing:")) {
                    framing = extractGrade(line);
                } else if (line.contains("Posture:")) {
                    posture = extractGrade(line);
                } else if (line.contains("Lighting:")) {
                    lighting = extractGrade(line);
                } else if (line.contains("Explanation:")) {
                    explanation = line.substring(line.indexOf(":") + 1).trim();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing results: " + e.getMessage());
        }
        
        final String finalEyeContact = eyeContact;
        final String finalFraming = framing;
        final String finalPosture = posture;
        final String finalLighting = lighting;
        final String finalExplanation = explanation;
        
        mMainHandler.post(() -> {
            if (mCallback != null) {
                mCallback.onAnalysisResult(
                    finalEyeContact,
                    finalFraming,
                    finalPosture,
                    finalLighting,
                    finalExplanation
                );
            }
        });
    }
    
    private String extractGrade(String line) {
        // Extract single letter grade (A-F)
        String[] parts = line.split(":");
        if (parts.length > 1) {
            String gradePart = parts[1].trim();
            if (gradePart.length() > 0) {
                char firstChar = gradePart.charAt(0);
                if (firstChar >= 'A' && firstChar <= 'F') {
                    return String.valueOf(firstChar);
                }
            }
        }
        return "B";  // Default grade
    }
    
    public boolean isLlavaLoaded() {
        return mIsLlavaLoaded;
    }
    
    public boolean isLlavaReady() {
        return mIsLlavaReady;
    }
    
    public boolean isWhisperLoaded() {
        return mIsWhisperLoaded;
    }
    
    public String getCurrentModelPath() {
        return mCurrentModelPath;
    }
    
    public String getCurrentTokenizerPath() {
        return mCurrentTokenizerPath;
    }
    
    public void cleanup() {
        Log.d(TAG, "Cleaning up models...");
        if (mLlavaModule != null) {
            mLlavaModule.stop();
            mLlavaModule = null;
        }
        if (mWhisperModule != null) {
            mWhisperModule.unload();
            mWhisperModule = null;
        }
        mIsLlavaLoaded = false;
        mIsLlavaReady = false;
        mIsWhisperLoaded = false;
        mCurrentModelPath = "";
        mCurrentTokenizerPath = "";
        Log.d(TAG, "Cleanup complete");
    }
}

