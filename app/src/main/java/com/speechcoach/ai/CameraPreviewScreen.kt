package com.speechcoach.ai

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
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
import androidx.compose.ui.draw.clip
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.speechcoach.ai.viewmodel.SpeechCoachViewModel
import kotlinx.coroutines.delay
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraPreviewScreen(
    onBack: () -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val viewModel: SpeechCoachViewModel = viewModel()

    when {
        cameraPermissionState.status.isGranted -> {
            CameraView(
                context = context,
                lifecycleOwner = lifecycleOwner,
                viewModel = viewModel,
                onBack = onBack
            )
        }
        cameraPermissionState.status.shouldShowRationale -> {
            PermissionRationaleScreen(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                onBack = onBack
            )
        }
        else -> {
            PermissionRequestScreen(
                onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                onBack = onBack
            )
        }
    }
}

@Composable
fun CameraView(
    context: Context,
    lifecycleOwner: LifecycleOwner,
    viewModel: SpeechCoachViewModel,
    onBack: () -> Unit
) {
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var isAnalysisStarted by remember { mutableStateOf(false) }
    
    val analysisState by viewModel.analysisState.collectAsState()
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProvider = cameraProviderFuture.get()
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            viewModel.stopAnalysis()
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
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                // Image analysis for AI processing
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analyzer ->
                        analyzer.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (isAnalysisStarted) {
                                // Convert ImageProxy to Bitmap for AI analysis
                                val bitmap = imageProxy.toBitmap()
                                viewModel.analyzeFrame(bitmap)
                            }
                            imageProxy.close()
                        }
                    }
                
                preview.setSurfaceProvider(previewView.surfaceProvider)
                
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Top bar with back button and analysis controls
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
            
            Text(
                text = "Speech Coach AI",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Button(
                onClick = {
                    if (isAnalysisStarted) {
                        viewModel.stopAnalysis()
                        isAnalysisStarted = false
                    } else {
                        viewModel.startAnalysis()
                        isAnalysisStarted = true
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isAnalysisStarted) Color.Red.copy(alpha = 0.8f) else Color.Green.copy(alpha = 0.8f)
                )
            ) {
                Text(
                    text = if (isAnalysisStarted) "Stop" else "Start",
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }

        // Left side feedback overlay
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 16.dp, top = 80.dp)
                .width(140.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            EnhancedFeedbackItem(
                label = "Eye Contact",
                feedbackItem = analysisState.eyeContact
            )
            
            EnhancedFeedbackItem(
                label = "Framing",
                feedbackItem = analysisState.framing
            )
            
            EnhancedFeedbackItem(
                label = "Posture",
                feedbackItem = analysisState.posture
            )
            
            EnhancedFeedbackItem(
                label = "Lighting",
                feedbackItem = analysisState.lighting
            )
        }

        // Overall score display
        Card(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp, 80.dp, 16.dp, 0.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Overall Score",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = analysisState.overallGrade,
                    color = getScoreColor(analysisState.overallScore),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${(analysisState.overallScore * 100).toInt()}%",
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }

        // Bottom explanation overlay
        if (analysisState.eyeContact.message.isNotEmpty()) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.8f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "AI Feedback",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = analysisState.eyeContact.message,
                        color = Color.White,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center
                    )
                }
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
fun EnhancedFeedbackItem(
    label: String,
    feedbackItem: com.speechcoach.ai.viewmodel.FeedbackItem
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = Color.Black.copy(alpha = 0.7f)
        )
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = feedbackItem.grade,
                color = feedbackItem.color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            
            Text(
                text = "${(feedbackItem.score * 100).toInt()}%",
                color = Color.White,
                fontSize = 8.sp
            )
            
            if (feedbackItem.isGood) {
                Text(
                    text = "✓",
                    color = Color.Green,
                    fontSize = 12.sp
                )
            }
        }
    }
}

// Extension function to convert ImageProxy to Bitmap
fun ImageProxy.toBitmap(): Bitmap {
    val buffer: java.nio.ByteBuffer = planes[0].buffer
    val pixelStride = planes[0].pixelStride
    val rowStride = planes[0].rowStride
    val rowPadding = rowStride - pixelStride * width
    
    val bitmap = Bitmap.createBitmap(
        width + rowPadding / pixelStride,
        height,
        Bitmap.Config.ARGB_8888
    )
    bitmap.copyPixelsFromBuffer(buffer)
    
    return if (rowPadding == 0) {
        bitmap
    } else {
        Bitmap.createBitmap(bitmap, 0, 0, width, height)
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
            text = "This app needs camera access to provide real-time feedback on your speaking performance.",
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
            text = "To use the camera feedback features, please grant camera permission in your device settings.",
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
        "A+", "A" -> Color(0xFF4CAF50) // Green
        "B+", "B" -> Color(0xFFFF9800) // Orange
        "C+", "C" -> Color(0xFFF44336) // Red
        else -> Color.White
    }
}

fun getScoreColor(score: Float): Color {
    return when {
        score >= 0.8f -> Color(0xFF4CAF50) // Green
        score >= 0.6f -> Color(0xFFFF9800) // Orange
        else -> Color(0xFFF44336) // Red
    }
}
