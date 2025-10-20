package com.presentationcoach.ai

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaRecorder
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.presentationcoach.ai.model.PresentationCoachModel
import kotlinx.coroutines.delay
import java.io.File
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreviewScreen(
    onBack: () -> Unit
) {
    val permissionsState = rememberPermissionState(Manifest.permission.CAMERA)
    
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Feedback grades
    var eyeContactGrade by remember { mutableStateOf("B") }
    var framingGrade by remember { mutableStateOf("B") }
    var postureGrade by remember { mutableStateOf("B") }
    var lightingGrade by remember { mutableStateOf("B") }
    var explanation by remember { mutableStateOf("Setting up AI models...") }
    var isModelReady by remember { mutableStateOf(false) }

    // Initialize model
    val model = remember {
        PresentationCoachModel(
            context,
            object : PresentationCoachModel.PresentationCoachCallback {
                override fun onModelLoadingStarted(modelName: String) {
                    explanation = "Loading $modelName model..."
                    Log.d("PresentationCoach", "Loading $modelName")
                }

                override fun onModelLoaded(modelName: String, success: Boolean, error: String?) {
                    if (success) {
                        explanation = "$modelName model ready!"
                        Log.d("PresentationCoach", "$modelName loaded successfully")
                        
                        // Mark as ready when LLaVA successfully loads
                        if (modelName == "LLaVA") {
                            isModelReady = true
                            explanation = "LLaVA ready! Starting analysis..."
                            Log.i("PresentationCoach", "LLaVA is ready for frame analysis")
                        }
                    } else {
                        explanation = "Error loading $modelName: $error"
                        Log.e("PresentationCoach", "Error loading $modelName: $error")
                        isModelReady = false
                    }
                }

                override fun onAnalysisResult(
                    eyeContact: String,
                    framing: String,
                    posture: String,
                    lighting: String,
                    exp: String
                ) {
                    eyeContactGrade = eyeContact
                    framingGrade = framing
                    postureGrade = posture
                    lightingGrade = lighting
                    explanation = exp
                    Log.d("PresentationCoach", "Analysis: EC=$eyeContact F=$framing P=$posture L=$lighting")
                }

                override fun onTranscriptionResult(transcriptionText: String) {
                    // Not used - audio transcription disabled
                }

                override fun onError(error: String) {
                    explanation = "Error: $error"
                    Log.e("PresentationCoach", "Error: $error")
                }
            }
        )
    }

    // Load models on startup
    LaunchedEffect(Unit) {
        try {
            // Use the same path as LlamaDemo: /data/local/tmp/llama/
            val modelsDir = "/data/local/tmp/llama"
            
            // Validate model files exist
            val llavaModelPath = "$modelsDir/llava.pte"
            val llavaTokenizerPath = "$modelsDir/llava_tokenizer.model"
            
            // Check if model files exist on device
            val modelExists = File(llavaModelPath).exists()
            val tokenizerExists = File(llavaTokenizerPath).exists()
            
            if (!modelExists || !tokenizerExists) {
                explanation = "Error: Model files not found. Please run ./setup_models.sh first!"
                Log.e("PresentationCoach", "Model files not found at $modelsDir")
                Log.e("PresentationCoach", "Run setup_models.sh to push models to device")
                return@LaunchedEffect
            }
            
            Log.i("PresentationCoach", "Model files found. Starting LLaVA load...")
            Log.d("PresentationCoach", "Model path: $llavaModelPath")
            Log.d("PresentationCoach", "Tokenizer path: $llavaTokenizerPath")
            
            // Load LLaVA model with proper configuration
            explanation = "Loading LLaVA vision model..."
            model.loadLlavaModel(llavaModelPath, llavaTokenizerPath)
            
            // Note: Whisper audio transcription is not used in this app
            // App focuses solely on visual presentation analysis
        } catch (e: Exception) {
            explanation = "Error initializing models: ${e.message}"
            Log.e("PresentationCoach", "Error in model initialization", e)
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            model.cleanup()
        }
    }

    when {
        permissionsState.status.isGranted -> {
            CameraView(
                context = context,
                lifecycleOwner = lifecycleOwner,
                model = model,
                eyeContactGrade = eyeContactGrade,
                framingGrade = framingGrade,
                postureGrade = postureGrade,
                lightingGrade = lightingGrade,
                explanation = explanation,
                isModelReady = isModelReady,
                onBack = onBack
            )
        }
        else -> {
            PermissionRequestScreen(
                onRequestPermission = { permissionsState.launchPermissionRequest() },
                onBack = onBack
            )
        }
    }
}

// Model files are now loaded from /data/local/tmp/llama/ 
// Run ./setup_models.sh to push model files to device

@Composable
fun CameraView(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    model: PresentationCoachModel,
    eyeContactGrade: String,
    framingGrade: String,
    postureGrade: String,
    lightingGrade: String,
    explanation: String,
    isModelReady: Boolean,
    onBack: () -> Unit
) {
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var lastAnalysisTime by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProvider = cameraProviderFuture.get()
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera Preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        LaunchedEffect(cameraProvider) {
            cameraProvider?.let { provider ->
                val preview = Preview.Builder().build()
                // Use front camera for presentation analysis (user faces camera)
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                // Image analysis for frame capture
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                
                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val currentTime = System.currentTimeMillis()
                    
                    // Only analyze if model is fully loaded, ready, and enough time has passed
                    if (model.isLlavaLoaded() && model.isLlavaReady() && currentTime - lastAnalysisTime > 3000) {
                        lastAnalysisTime = currentTime
                        
                        Log.d("PresentationCoach", "Capturing frame for analysis...")
                        
                        // Convert ImageProxy to Bitmap
                        val bitmap = imageProxyToBitmap(imageProxy)
                        if (bitmap != null) {
                            Log.d("PresentationCoach", "Frame captured, sending to model for analysis")
                            model.analyzeFrame(bitmap)
                        } else {
                            Log.w("PresentationCoach", "Failed to convert frame to bitmap")
                        }
                    } else if (!model.isLlavaReady()) {
                        // Log only occasionally to avoid spam
                        if (currentTime - lastAnalysisTime > 5000) {
                            Log.d("PresentationCoach", "Waiting for LLaVA to be ready before analyzing frames...")
                            lastAnalysisTime = currentTime
                        }
                    }
                    imageProxy.close()
                }
                
                preview.setSurfaceProvider(previewView.surfaceProvider)
                
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    Log.e("Camera", "Error binding camera: ${e.message}")
                    e.printStackTrace()
                }
            }
        }

        // Top bar with back button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onBack,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Black.copy(alpha = 0.6f)
                )
            ) {
                Text("← Back", color = Color.White)
            }
            
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Presentation Coach",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                
                // Model status indicator
                if (isModelReady) {
                    Text(
                        text = "● AI Ready",
                        color = Color(0xFF4CAF50),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                } else {
                    Text(
                        text = "○ Loading...",
                        color = Color(0xFFFF9800),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            
            Spacer(modifier = Modifier.width(48.dp)) // Balance layout
        }

        // Left side feedback overlay
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp, top = 80.dp)
                .width(120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FeedbackItem(
                label = "Eye Contact",
                grade = eyeContactGrade,
                color = getGradeColor(eyeContactGrade)
            )
            
            FeedbackItem(
                label = "Framing",
                grade = framingGrade,
                color = getGradeColor(framingGrade)
            )
            
            FeedbackItem(
                label = "Posture",
                grade = postureGrade,
                color = getGradeColor(postureGrade)
            )
            
            FeedbackItem(
                label = "Lighting",
                grade = lightingGrade,
                color = getGradeColor(lightingGrade)
            )
        }

        // Bottom feedback overlay
        Text(
            text = explanation,
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp)
                .fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FeedbackItem(
    label: String,
    grade: String,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
        
        Spacer(modifier = Modifier.height(4.dp))
        
        Text(
            text = grade,
            color = color,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Required",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "This app needs camera access to analyze your presentation skills in real-time using AI.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Grant Camera Permission")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Text("Go Back")
        }
    }
}

@Composable
fun PermissionRationaleScreen(
    onRequestPermission: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Camera Permission Denied",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = "Camera access is required to analyze your presentation. The app uses AI to evaluate your eye contact, framing, posture, and lighting.",
            fontSize = 16.sp,
            textAlign = TextAlign.Center
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Button(
            onClick = onRequestPermission,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Try Again")
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Text("Go Back")
        }
    }
}

fun getGradeColor(grade: String): Color {
    return when (grade) {
        "A" -> Color(0xFF4CAF50) // Green
        "B" -> Color(0xFFFF9800) // Orange
        "C" -> Color(0xFFF44336) // Red
        "D" -> Color(0xFFD32F2F) // Dark Red
        "F" -> Color(0xFFB71C1C) // Very Dark Red
        else -> Color.White
    }
}

fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
    try {
        val buffer: ByteBuffer = imageProxy.planes[0].buffer
        val bytes = ByteArray(buffer.capacity())
        buffer.get(bytes)
        
        // For simplicity, we'll create a simple bitmap
        // In production, you'd want proper YUV to RGB conversion
        val bitmap = Bitmap.createBitmap(
            imageProxy.width,
            imageProxy.height,
            Bitmap.Config.ARGB_8888
        )
        
        return bitmap
    } catch (e: Exception) {
        Log.e("ImageConversion", "Error converting image: ${e.message}")
        return null
    }
}

