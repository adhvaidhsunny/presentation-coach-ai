package com.speechcoach.ai

import android.Manifest
import android.content.Context
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
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
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

    // Feedback grades (simulated for now)
    var eyeContactGrade by remember { mutableStateOf("B") }
    var framingGrade by remember { mutableStateOf("A") }
    var postureGrade by remember { mutableStateOf("B") }
    var lightingGrade by remember { mutableStateOf("A") }
    var explanation by remember { mutableStateOf("Great framing! Try to maintain more consistent eye contact with the camera.") }

    // Simulate grade changes
    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            val grades = listOf("A", "B", "C")
            eyeContactGrade = grades.random()
            framingGrade = grades.random()
            postureGrade = grades.random()
            lightingGrade = grades.random()
            
            explanation = when {
                eyeContactGrade == "A" && framingGrade == "A" -> "Excellent! You're maintaining great eye contact and framing."
                eyeContactGrade == "B" -> "Good eye contact, try to look directly at the camera more often."
                framingGrade == "B" -> "Good framing, consider adjusting your position slightly."
                postureGrade == "A" -> "Perfect posture! Keep your shoulders back and head up."
                lightingGrade == "A" -> "Great lighting! The camera is picking up your face clearly."
                else -> "Keep practicing! Focus on maintaining consistent eye contact and good posture."
            }
        }
    }

    when {
        cameraPermissionState.status.isGranted -> {
            CameraView(
                context = context,
                lifecycleOwner = lifecycleOwner,
                eyeContactGrade = eyeContactGrade,
                framingGrade = framingGrade,
                postureGrade = postureGrade,
                lightingGrade = lightingGrade,
                explanation = explanation,
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
    eyeContactGrade: String,
    framingGrade: String,
    postureGrade: String,
    lightingGrade: String,
    explanation: String,
    onBack: () -> Unit
) {
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val previewView = remember { PreviewView(context) }
    var cameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

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
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                preview.setSurfaceProvider(previewView.surfaceProvider)
                
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                } catch (e: Exception) {
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
            
            Text(
                text = "Speech Coach AI",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
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

        // Bottom explanation overlay
        Text(
            text = "Explanation: $explanation",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .background(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(12.dp),
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
        "A" -> Color(0xFF4CAF50) // Green
        "B" -> Color(0xFFFF9800) // Orange
        "C" -> Color(0xFFF44336) // Red
        else -> Color.White
    }
}
