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
    var processingStatus by remember { mutableStateOf("Initializing...") }
    var lastAnalysisTimestamp by remember { mutableStateOf(0L) }
    var analysisCount by remember { mutableStateOf(0) }
    var requestSentTime by remember { mutableStateOf<Long?>(null) }
    var responseReceivedTime by remember { mutableStateOf<Long?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var fullModelResponse by remember { mutableStateOf("Waiting for first analysis...") }
    var modelFeedback by remember { mutableStateOf("") }

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
                        processingStatus = "$modelName loaded ✓"
                        Log.d("PresentationCoach", "$modelName loaded successfully")
                        
                        // Mark as ready when LLaVA successfully loads
                        if (modelName == "LLaVA") {
                            isModelReady = true
                            explanation = "LLaVA ready! Waiting for first frame..."
                            processingStatus = "Ready - Waiting for frame"
                            Log.i("PresentationCoach", "LLaVA is ready for frame analysis")
                        }
                    } else {
                        explanation = "Error loading $modelName: $error"
                        processingStatus = "Error loading model"
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
                    responseReceivedTime = System.currentTimeMillis()
                    eyeContactGrade = eyeContact
                    framingGrade = framing
                    postureGrade = posture
                    lightingGrade = lighting
                    explanation = exp
                    analysisCount++
                    lastAnalysisTimestamp = System.currentTimeMillis()
                    isProcessing = false
                    
                    val processingTime = if (requestSentTime != null && responseReceivedTime != null) {
                        ((responseReceivedTime!! - requestSentTime!!) / 1000.0).toString() + "s"
                    } else {
                        "N/A"
                    }
                    
                    processingStatus = "Analysis #$analysisCount ✓ (took $processingTime)"
                    
                    // Build full model response display
                    fullModelResponse = buildString {
                        appendLine("📊 ANALYSIS #$analysisCount")
                        appendLine("━━━━━━━━━━━━━━━━━━━━━")
                        appendLine("Eye Contact: $eyeContact")
                        appendLine("Framing: $framing")
                        appendLine("Posture: $posture")
                        appendLine("Lighting: $lighting")
                        appendLine()
                        appendLine("📝 Explanation:")
                        appendLine(exp)
                        appendLine()
                        appendLine("⏱️ Timing:")
                        appendLine("Request sent: ${formatTime(requestSentTime)}")
                        appendLine("Response received: ${formatTime(responseReceivedTime)}")
                        appendLine("Processing time: $processingTime")
                    }
                    
                    // Extract feedback/improvement tips
                    modelFeedback = if (exp.contains("improve") || exp.contains("Improve") || 
                                        exp.contains("try") || exp.contains("Try") ||
                                        exp.contains("should") || exp.contains("could")) {
                        "💡 Tips: $exp"
                    } else {
                        "💡 Keep up the good work! The AI will provide improvement suggestions after analyzing your presentation."
                    }
                    
                    Log.d("PresentationCoach", "Analysis #$analysisCount complete: EC=$eyeContact F=$framing P=$posture L=$lighting")
                    Log.d("PresentationCoach", "Processing time: $processingTime")
                }

                override fun onTranscriptionResult(transcriptionText: String) {
                    // Not used - audio transcription disabled
                }

                override fun onError(error: String) {
                    explanation = "Error: $error"
                    processingStatus = "Error occurred ✗"
                    Log.e("PresentationCoach", "Error: $error")
                }
            }
        )
    }

    // Load models on startup
    LaunchedEffect(Unit) {
        try {
            processingStatus = "Checking model files..."
            
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
                processingStatus = "❌ Model files missing"
                Log.e("PresentationCoach", "Model files not found at $modelsDir")
                Log.e("PresentationCoach", "Run setup_models.sh to push models to device")
                return@LaunchedEffect
            }
            
            processingStatus = "Model files found ✓"
            Log.i("PresentationCoach", "Model files found. Starting LLaVA load...")
            Log.d("PresentationCoach", "Model path: $llavaModelPath")
            Log.d("PresentationCoach", "Tokenizer path: $llavaTokenizerPath")
            
            // Load LLaVA model with proper configuration
            explanation = "Loading LLaVA vision model..."
            processingStatus = "Loading LLaVA model..."
            model.loadLlavaModel(llavaModelPath, llavaTokenizerPath)
            
            // Note: Whisper audio transcription is not used in this app
            // App focuses solely on visual presentation analysis
        } catch (e: Exception) {
            explanation = "Error initializing models: ${e.message}"
            processingStatus = "❌ Initialization failed"
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
                processingStatus = processingStatus,
                analysisCount = analysisCount,
                requestSentTime = requestSentTime,
                responseReceivedTime = responseReceivedTime,
                isProcessing = isProcessing,
                fullModelResponse = fullModelResponse,
                modelFeedback = modelFeedback,
                onStatusUpdate = { status -> processingStatus = status },
                onRequestSent = { requestSentTime = System.currentTimeMillis(); isProcessing = true },
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
    processingStatus: String,
    analysisCount: Int,
    requestSentTime: Long?,
    responseReceivedTime: Long?,
    isProcessing: Boolean,
    fullModelResponse: String,
    modelFeedback: String,
    onStatusUpdate: (String) -> Unit,
    onRequestSent: () -> Unit,
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
                    
                    // Only analyze ONE frame at a time - wait for previous analysis to complete
                    // Check: model ready, not currently processing, and enough time since last analysis
                    if (model.isLlavaLoaded() && model.isLlavaReady() && 
                        !isProcessing && currentTime - lastAnalysisTime > 3000) {
                        
                        lastAnalysisTime = currentTime
                        
                        Log.d("PresentationCoach", "Capturing single frame for analysis...")
                        onStatusUpdate("📸 Capturing frame...")
                        
                        // Convert ImageProxy to Bitmap
                        val bitmap = imageProxyToBitmap(imageProxy)
                        if (bitmap != null) {
                            Log.d("PresentationCoach", "Frame captured, sending to model (one at a time)")
                            Log.d("PresentationCoach", "Frame dimensions: ${bitmap.width}x${bitmap.height}")
                            
                            // Mark request as sent
                            onRequestSent()
                            onStatusUpdate("🧠 Processing image with AI...")
                            
                            // Send single frame to model
                            model.analyzeFrame(bitmap)
                            
                            Log.d("PresentationCoach", "Frame sent to model. Waiting for response...")
                        } else {
                            Log.w("PresentationCoach", "Failed to convert frame to bitmap")
                            onStatusUpdate("❌ Frame capture failed")
                        }
                    } else if (!model.isLlavaReady()) {
                        // Log only occasionally to avoid spam
                        if (currentTime - lastAnalysisTime > 5000) {
                            Log.d("PresentationCoach", "Waiting for LLaVA to be ready before analyzing frames...")
                            onStatusUpdate("⏳ Waiting for model...")
                            lastAnalysisTime = currentTime
                        }
                    } else if (isProcessing) {
                        // Skip frames while processing current one
                        if (currentTime - lastAnalysisTime > 2000) {
                            Log.d("PresentationCoach", "Skipping frame - still processing previous image")
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
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .background(
                        color = Color.Black.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    .padding(8.dp)
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
                
                // Processing status
                Text(
                    text = processingStatus,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = 4.dp)
                )
                
                // Analysis count
                if (analysisCount > 0) {
                    Text(
                        text = "Analyses: $analysisCount",
                        color = Color(0xFF4CAF50),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Normal
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
        
        // Right side status panel with timing
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp, top = 80.dp)
                .width(160.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Request/Response",
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(2.dp))
            
            StatusIndicator(
                label = "Model",
                status = if (isModelReady) "Ready" else "Loading",
                isActive = isModelReady
            )
            
            StatusIndicator(
                label = "Frames Sent",
                status = "$analysisCount",
                isActive = analysisCount > 0
            )
            
            if (isProcessing) {
                Text(
                    text = "⏳ Processing...",
                    color = Color(0xFFFF9800),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            // Request time
            if (requestSentTime != null) {
                Text(
                    text = "📤 Sent: ${formatTime(requestSentTime)}",
                    color = Color(0xFF64B5F6),
                    fontSize = 9.sp
                )
            }
            
            // Response time
            if (responseReceivedTime != null) {
                Text(
                    text = "📥 Received: ${formatTime(responseReceivedTime)}",
                    color = Color(0xFF4CAF50),
                    fontSize = 9.sp
                )
                
                // Show processing duration
                if (requestSentTime != null) {
                    val duration = (responseReceivedTime - requestSentTime) / 1000.0
                    Text(
                        text = "⚡ ${String.format("%.2f", duration)}s",
                        color = Color(0xFFFFD54F),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Bottom feedback overlay - Full model response
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
                .background(
                    color = Color.Black.copy(alpha = 0.8f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "🤖 AI Model Response",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = fullModelResponse,
                color = Color.White,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth()
            )
            
            if (modelFeedback.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = modelFeedback,
                    color = Color(0xFF4CAF50),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = Color(0xFF1B5E20).copy(alpha = 0.3f),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .padding(8.dp)
                )
            }
        }
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
fun StatusIndicator(
    label: String,
    status: String,
    isActive: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "●",
                color = if (isActive) Color(0xFF4CAF50) else Color(0xFF757575),
                fontSize = 10.sp
            )
            
            Text(
                text = status,
                color = if (isActive) Color(0xFF4CAF50) else Color(0xFFFF9800),
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal
            )
        }
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
        // Get the YUV image
        val yBuffer = imageProxy.planes[0].buffer // Y
        val uBuffer = imageProxy.planes[1].buffer // U
        val vBuffer = imageProxy.planes[2].buffer // V

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        // U and V are swapped
        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = android.graphics.YuvImage(
            nv21,
            android.graphics.ImageFormat.NV21,
            imageProxy.width,
            imageProxy.height,
            null
        )

        val out = java.io.ByteArrayOutputStream()
        yuvImage.compressToJpeg(
            android.graphics.Rect(0, 0, imageProxy.width, imageProxy.height),
            100,
            out
        )

        val imageBytes = out.toByteArray()
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        
        Log.d("ImageConversion", "Successfully converted ImageProxy to Bitmap: ${bitmap.width}x${bitmap.height}")
        return bitmap
    } catch (e: Exception) {
        Log.e("ImageConversion", "Error converting image: ${e.message}", e)
        e.printStackTrace()
        return null
    }
}

fun formatTime(timestamp: Long?): String {
    if (timestamp == null) return "N/A"
    
    val calendar = java.util.Calendar.getInstance()
    calendar.timeInMillis = timestamp
    
    return String.format(
        "%02d:%02d:%02d.%03d",
        calendar.get(java.util.Calendar.HOUR_OF_DAY),
        calendar.get(java.util.Calendar.MINUTE),
        calendar.get(java.util.Calendar.SECOND),
        calendar.get(java.util.Calendar.MILLISECOND)
    )
}

