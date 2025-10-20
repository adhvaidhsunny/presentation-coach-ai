package com.speechcoach.ai.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.speechcoach.ai.ai.AIAnalysisEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing speech coaching AI analysis state
 */
class SpeechCoachViewModel(application: Application) : AndroidViewModel(application) {
    
    private val aiEngine = AIAnalysisEngine(application)
    private var analysisJob: Job? = null
    
    // UI State
    private val _uiState = MutableStateFlow(SpeechCoachUiState())
    val uiState: StateFlow<SpeechCoachUiState> = _uiState.asStateFlow()
    
    // Analysis State
    private val _analysisState = MutableStateFlow(AnalysisState())
    val analysisState: StateFlow<AnalysisState> = _analysisState.asStateFlow()
    
    fun startAnalysis() {
        _uiState.value = _uiState.value.copy(isAnalyzing = true)
        _analysisState.value = AnalysisState(isAnalyzing = true)
    }
    
    fun stopAnalysis() {
        _uiState.value = _uiState.value.copy(isAnalyzing = false)
        _analysisState.value = AnalysisState(isAnalyzing = false)
        analysisJob?.cancel()
    }
    
    fun analyzeFrame(bitmap: Bitmap) {
        if (!_uiState.value.isAnalyzing) return
        
        analysisJob?.cancel()
        analysisJob = viewModelScope.launch {
            try {
                val result = aiEngine.analyzeFrame(bitmap)
                updateAnalysisState(result)
            } catch (e: Exception) {
                e.printStackTrace()
                // Handle error gracefully
                _analysisState.value = _analysisState.value.copy(
                    error = "Analysis failed: ${e.message}"
                )
            }
        }
    }
    
    private fun updateAnalysisState(result: AIAnalysisEngine.AnalysisResult) {
        val currentState = _analysisState.value
        
        _analysisState.value = currentState.copy(
            eyeContact = FeedbackItem(
                score = result.eyeContact.score,
                grade = scoreToGrade(result.eyeContact.score),
                message = generateEyeContactMessage(result.eyeContact),
                color = getScoreColor(result.eyeContact.score),
                isGood = result.eyeContact.score > 0.7f
            ),
            framing = FeedbackItem(
                score = result.framing.score,
                grade = scoreToGrade(result.framing.score),
                message = generateFramingMessage(result.framing),
                color = getScoreColor(result.framing.score),
                isGood = result.framing.score > 0.7f
            ),
            posture = FeedbackItem(
                score = result.posture.score,
                grade = scoreToGrade(result.posture.score),
                message = generatePostureMessage(result.posture),
                color = getScoreColor(result.posture.score),
                isGood = result.posture.score > 0.7f
            ),
            lighting = FeedbackItem(
                score = result.lighting.score,
                grade = scoreToGrade(result.lighting.score),
                message = generateLightingMessage(result.lighting),
                color = getScoreColor(result.lighting.score),
                isGood = result.lighting.score > 0.7f
            ),
            overallScore = result.overallScore,
            overallGrade = scoreToGrade(result.overallScore),
            lastUpdated = System.currentTimeMillis(),
            error = null
        )
    }
    
    private fun scoreToGrade(score: Float): String {
        return when {
            score >= 0.9f -> "A+"
            score >= 0.8f -> "A"
            score >= 0.7f -> "B+"
            score >= 0.6f -> "B"
            score >= 0.5f -> "C+"
            score >= 0.4f -> "C"
            else -> "D"
        }
    }
    
    private fun getScoreColor(score: Float): androidx.compose.ui.graphics.Color {
        return when {
            score >= 0.8f -> androidx.compose.ui.graphics.Color(0xFF4CAF50) // Green
            score >= 0.6f -> androidx.compose.ui.graphics.Color(0xFFFF9800) // Orange
            else -> androidx.compose.ui.graphics.Color(0xFFF44336) // Red
        }
    }
    
    private fun generateEyeContactMessage(analysis: AIAnalysisEngine.EyeContactAnalysis): String {
        return when {
            analysis.score >= 0.8f -> "Excellent eye contact! Keep looking at the camera."
            analysis.score >= 0.6f -> "Good eye contact. Try to maintain more consistent focus on the camera."
            else -> "Look directly at the camera more often for better engagement."
        }
    }
    
    private fun generateFramingMessage(analysis: AIAnalysisEngine.FramingAnalysis): String {
        return when (analysis.facePosition) {
            AIAnalysisEngine.FacePosition.CENTERED -> "Perfect framing! You're well-positioned in the frame."
            AIAnalysisEngine.FacePosition.LEFT -> "Move slightly to the right to center yourself better."
            AIAnalysisEngine.FacePosition.RIGHT -> "Move slightly to the left to center yourself better."
            AIAnalysisEngine.FacePosition.TOP -> "Move down slightly to center yourself better."
            AIAnalysisEngine.FacePosition.BOTTOM -> "Move up slightly to center yourself better."
            AIAnalysisEngine.FacePosition.TOO_CLOSE -> "Move back a bit - you're too close to the camera."
            AIAnalysisEngine.FacePosition.TOO_FAR -> "Move closer to the camera for better visibility."
        }
    }
    
    private fun generatePostureMessage(analysis: AIAnalysisEngine.PostureAnalysis): String {
        return when {
            analysis.score >= 0.8f -> "Great posture! Keep your shoulders back and head up."
            analysis.score >= 0.6f -> "Good posture. Try to sit up straighter and align your shoulders."
            else -> "Improve your posture by sitting up straight and keeping your shoulders level."
        }
    }
    
    private fun generateLightingMessage(analysis: AIAnalysisEngine.LightingAnalysis): String {
        return when {
            analysis.score >= 0.8f -> "Perfect lighting! The camera captures you clearly."
            analysis.score >= 0.6f -> "Good lighting. Consider adjusting the light source for better visibility."
            analysis.hasShadows -> "Try to reduce shadows by adjusting your lighting setup."
            analysis.brightness < 0.3f -> "The lighting is too dim. Increase the brightness."
            analysis.brightness > 0.8f -> "The lighting is too bright. Reduce the intensity."
            else -> "Adjust your lighting for better visibility and clarity."
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        aiEngine.cleanup()
        analysisJob?.cancel()
    }
}

data class SpeechCoachUiState(
    val isAnalyzing: Boolean = false,
    val isCameraReady: Boolean = false
)

data class AnalysisState(
    val eyeContact: FeedbackItem = FeedbackItem(),
    val framing: FeedbackItem = FeedbackItem(),
    val posture: FeedbackItem = FeedbackItem(),
    val lighting: FeedbackItem = FeedbackItem(),
    val overallScore: Float = 0f,
    val overallGrade: String = "N/A",
    val isAnalyzing: Boolean = false,
    val lastUpdated: Long = 0L,
    val error: String? = null
)

data class FeedbackItem(
    val score: Float = 0f,
    val grade: String = "N/A",
    val message: String = "Analysis in progress...",
    val color: androidx.compose.ui.graphics.Color = androidx.compose.ui.graphics.Color.Gray,
    val isGood: Boolean = false
)
