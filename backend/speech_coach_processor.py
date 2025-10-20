import asyncio
import time
import logging
import numpy as np
import cv2
from typing import Optional, Dict, Any, Tuple
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from PIL import Image
import json

from vision_agents.core.processors.base_processor import (
    VideoProcessorMixin,
    VideoPublisherMixin,
    AudioVideoProcessor,
)
from vision_agents.core.utils.queue import LatestNQueue
from vision_agents.core.utils.video_forwarder import VideoForwarder

logger = logging.getLogger(__name__)

class SpeechCoachProcessor(AudioVideoProcessor, VideoProcessorMixin, VideoPublisherMixin):
    """
    Custom processor for speech coaching analysis.
    Analyzes eye contact, framing, posture, and lighting from camera feed.
    """

    def __init__(
        self,
        model_path: str = "yolo11n-pose.pt",
        conf_threshold: float = 0.5,
        imgsz: int = 512,
        device: str = "cpu",
        max_workers: int = 4,
        fps: int = 10,
        interval: int = 0,
        *args,
        **kwargs,
    ):
        super().__init__(interval=interval, receive_audio=False, receive_video=True)
        
        self.model_path = model_path
        self.fps = fps
        self.conf_threshold = conf_threshold
        self.imgsz = imgsz
        self.device = device
        self._last_frame: Optional[Image.Image] = None
        self._video_forwarder: Optional[VideoForwarder] = None
        
        # Initialize YOLO model for pose detection
        self._load_model()
        
        # Thread pool for processing
        self.executor = ThreadPoolExecutor(
            max_workers=max_workers, thread_name_prefix="speech_coach_processor"
        )
        self._shutdown = False
        
        # Analysis state
        self._last_analysis = {
            "eye_contact": "B",
            "framing": "B", 
            "posture": "B",
            "lighting": "B",
            "explanation": "Starting analysis..."
        }
        
        logger.info(f"🎯 Speech Coach Processor initialized with model: {model_path}")

    def _load_model(self):
        """Load the YOLO pose model."""
        try:
            from ultralytics import YOLO
            if not Path(self.model_path).exists():
                logger.warning(f"Model file {self.model_path} not found. YOLO will download it automatically.")
            
            self.pose_model = YOLO(self.model_path)
            self.pose_model.to(self.device)
            logger.info(f"✅ YOLO pose model loaded: {self.model_path} on {self.device}")
        except ImportError:
            logger.error("❌ ultralytics not installed. Please install: pip install ultralytics")
            raise

    async def process_video(
        self,
        incoming_track,
        participant: Any,
        shared_forwarder=None,
    ):
        """Process incoming video stream."""
        logger.info("✅ Speech Coach video processing starting")
        
        if shared_forwarder is not None:
            self._video_forwarder = shared_forwarder
            logger.info(f"🎥 Speech Coach subscribing to shared VideoForwarder at {self.fps} FPS")
            await self._video_forwarder.start_event_consumer(
                self._analyze_frame, fps=float(self.fps), consumer_name="speech_coach"
            )
        else:
            self._video_forwarder = VideoForwarder(
                incoming_track,
                max_buffer=30,
                fps=self.fps,
                name="speech_coach_forwarder",
            )
            await self._video_forwarder.start()
            await self._video_forwarder.start_event_consumer(self._analyze_frame)

    async def _analyze_frame(self, frame):
        """Analyze a single frame for speech coaching feedback."""
        try:
            frame_array = frame.to_ndarray(format="rgb24")
            analysis = await self._analyze_frame_async(frame_array)
            
            # Update last analysis
            self._last_analysis = analysis
            
            logger.debug(f"📊 Analysis: {analysis}")
            
        except Exception as e:
            logger.error(f"❌ Error analyzing frame: {e}")

    async def _analyze_frame_async(self, frame_array: np.ndarray) -> Dict[str, Any]:
        """Async wrapper for frame analysis."""
        loop = asyncio.get_event_loop()
        
        try:
            result = await asyncio.wait_for(
                loop.run_in_executor(
                    self.executor, self._analyze_frame_sync, frame_array
                ),
                timeout=5.0
            )
            return result
        except asyncio.TimeoutError:
            logger.warning("⏰ Frame analysis timeout - returning last analysis")
            return self._last_analysis
        except Exception as e:
            logger.error(f"❌ Error in async frame analysis: {e}")
            return self._last_analysis

    def _analyze_frame_sync(self, frame_array: np.ndarray) -> Dict[str, Any]:
        """Synchronous frame analysis."""
        try:
            if self._shutdown:
                return self._last_analysis

            height, width = frame_array.shape[:2]
            logger.debug(f"🔍 Analyzing frame: {width}x{height}")

            # Run pose detection
            pose_results = self.pose_model(
                frame_array,
                verbose=False,
                conf=self.conf_threshold,
                device=self.device,
            )

            if not pose_results or not pose_results[0].keypoints:
                logger.debug("❌ No pose detected")
                return self._last_analysis

            # Get pose keypoints
            keypoints = pose_results[0].keypoints.data[0].cpu().numpy()
            
            # Analyze different aspects
            eye_contact_grade = self._analyze_eye_contact(keypoints, width, height)
            framing_grade = self._analyze_framing(keypoints, width, height)
            posture_grade = self._analyze_posture(keypoints)
            lighting_grade = self._analyze_lighting(frame_array)
            
            # Generate explanation
            explanation = self._generate_explanation(
                eye_contact_grade, framing_grade, posture_grade, lighting_grade
            )

            analysis = {
                "eye_contact": eye_contact_grade,
                "framing": framing_grade,
                "posture": posture_grade,
                "lighting": lighting_grade,
                "explanation": explanation
            }

            logger.debug(f"✅ Analysis complete: {analysis}")
            return analysis

        except Exception as e:
            logger.error(f"❌ Error in frame analysis: {e}")
            return self._last_analysis

    def _analyze_eye_contact(self, keypoints: np.ndarray, width: int, height: int) -> str:
        """Analyze eye contact based on head position and gaze direction."""
        try:
            # Keypoints: 0=nose, 1=left_eye, 2=right_eye, 3=left_ear, 4=right_ear
            nose = keypoints[0] if len(keypoints) > 0 and keypoints[0][2] > self.conf_threshold else None
            left_eye = keypoints[1] if len(keypoints) > 1 and keypoints[1][2] > self.conf_threshold else None
            right_eye = keypoints[2] if len(keypoints) > 2 and keypoints[2][2] > self.conf_threshold else None
            
            if nose is None or left_eye is None or right_eye is None:
                return "C"
            
            # Check if face is centered horizontally (looking at camera)
            face_center_x = (left_eye[0] + right_eye[0]) / 2
            screen_center_x = width / 2
            horizontal_offset = abs(face_center_x - screen_center_x) / screen_center_x
            
            # Check if head is tilted (looking away)
            eye_distance = abs(left_eye[0] - right_eye[0])
            vertical_diff = abs(left_eye[1] - right_eye[1])
            tilt_ratio = vertical_diff / eye_distance if eye_distance > 0 else 0
            
            # Grade based on centering and tilt
            if horizontal_offset < 0.1 and tilt_ratio < 0.1:
                return "A"  # Great eye contact
            elif horizontal_offset < 0.2 and tilt_ratio < 0.2:
                return "B"  # Good eye contact
            else:
                return "C"  # Poor eye contact
                
        except Exception as e:
            logger.error(f"❌ Error analyzing eye contact: {e}")
            return "C"

    def _analyze_framing(self, keypoints: np.ndarray, width: int, height: int) -> str:
        """Analyze framing - how well the person is positioned in the frame."""
        try:
            # Keypoints: 0=nose, 5=left_shoulder, 6=right_shoulder
            nose = keypoints[0] if len(keypoints) > 0 and keypoints[0][2] > self.conf_threshold else None
            left_shoulder = keypoints[5] if len(keypoints) > 5 and keypoints[5][2] > self.conf_threshold else None
            right_shoulder = keypoints[6] if len(keypoints) > 6 and keypoints[6][2] > self.conf_threshold else None
            
            if nose is None or left_shoulder is None or right_shoulder is None:
                return "C"
            
            # Check if person is centered in frame
            person_center_x = (left_shoulder[0] + right_shoulder[0]) / 2
            screen_center_x = width / 2
            horizontal_offset = abs(person_center_x - screen_center_x) / screen_center_x
            
            # Check if person takes up appropriate amount of frame
            shoulder_width = abs(left_shoulder[0] - right_shoulder[0])
            frame_ratio = shoulder_width / width
            
            # Check vertical positioning (head should be in upper third)
            head_y_ratio = nose[1] / height
            
            # Grade based on positioning
            if (horizontal_offset < 0.15 and 
                0.1 < frame_ratio < 0.4 and 
                0.1 < head_y_ratio < 0.4):
                return "A"  # Perfect framing
            elif (horizontal_offset < 0.25 and 
                  0.05 < frame_ratio < 0.5 and 
                  0.05 < head_y_ratio < 0.5):
                return "B"  # Good framing
            else:
                return "C"  # Poor framing
                
        except Exception as e:
            logger.error(f"❌ Error analyzing framing: {e}")
            return "C"

    def _analyze_posture(self, keypoints: np.ndarray) -> str:
        """Analyze posture based on shoulder and spine alignment."""
        try:
            # Keypoints: 5=left_shoulder, 6=right_shoulder, 11=left_hip, 12=right_hip
            left_shoulder = keypoints[5] if len(keypoints) > 5 and keypoints[5][2] > self.conf_threshold else None
            right_shoulder = keypoints[6] if len(keypoints) > 6 and keypoints[6][2] > self.conf_threshold else None
            left_hip = keypoints[11] if len(keypoints) > 11 and keypoints[11][2] > self.conf_threshold else None
            right_hip = keypoints[12] if len(keypoints) > 12 and keypoints[12][2] > self.conf_threshold else None
            
            if (left_shoulder is None or right_shoulder is None or 
                left_hip is None or right_hip is None):
                return "C"
            
            # Check shoulder alignment (should be level)
            shoulder_y_diff = abs(left_shoulder[1] - right_shoulder[1])
            shoulder_distance = abs(left_shoulder[0] - right_shoulder[0])
            shoulder_tilt = shoulder_y_diff / shoulder_distance if shoulder_distance > 0 else 0
            
            # Check spine alignment (shoulders vs hips)
            shoulder_center_y = (left_shoulder[1] + right_shoulder[1]) / 2
            hip_center_y = (left_hip[1] + right_hip[1]) / 2
            spine_angle = abs(shoulder_center_y - hip_center_y) / shoulder_distance if shoulder_distance > 0 else 0
            
            # Grade based on alignment
            if shoulder_tilt < 0.05 and spine_angle < 0.1:
                return "A"  # Excellent posture
            elif shoulder_tilt < 0.1 and spine_angle < 0.2:
                return "B"  # Good posture
            else:
                return "C"  # Poor posture
                
        except Exception as e:
            logger.error(f"❌ Error analyzing posture: {e}")
            return "C"

    def _analyze_lighting(self, frame_array: np.ndarray) -> str:
        """Analyze lighting quality based on brightness and contrast."""
        try:
            # Convert to grayscale for analysis
            gray = cv2.cvtColor(frame_array, cv2.COLOR_RGB2GRAY)
            
            # Calculate brightness (mean pixel value)
            brightness = np.mean(gray)
            
            # Calculate contrast (standard deviation)
            contrast = np.std(gray)
            
            # Calculate histogram distribution
            hist = cv2.calcHist([gray], [0], None, [256], [0, 256])
            hist_normalized = hist.ravel() / hist.sum()
            
            # Check for overexposure (too many bright pixels)
            overexposed_ratio = np.sum(hist_normalized[200:])  # Pixels > 200
            
            # Check for underexposure (too many dark pixels)
            underexposed_ratio = np.sum(hist_normalized[:50])  # Pixels < 50
            
            # Grade based on lighting quality
            if (100 < brightness < 180 and 
                contrast > 30 and 
                overexposed_ratio < 0.1 and 
                underexposed_ratio < 0.1):
                return "A"  # Excellent lighting
            elif (80 < brightness < 200 and 
                  contrast > 20 and 
                  overexposed_ratio < 0.2 and 
                  underexposed_ratio < 0.2):
                return "B"  # Good lighting
            else:
                return "C"  # Poor lighting
                
        except Exception as e:
            logger.error(f"❌ Error analyzing lighting: {e}")
            return "C"

    def _generate_explanation(self, eye_contact: str, framing: str, posture: str, lighting: str) -> str:
        """Generate explanation based on analysis results."""
        explanations = []
        
        if eye_contact == "A":
            explanations.append("Great eye contact! You're looking directly at the camera.")
        elif eye_contact == "B":
            explanations.append("Good eye contact. Try to look more directly at the camera.")
        else:
            explanations.append("Focus on looking directly at the camera for better engagement.")
        
        if framing == "A":
            explanations.append("Perfect framing! You're well positioned in the frame.")
        elif framing == "B":
            explanations.append("Good framing. Consider adjusting your position slightly.")
        else:
            explanations.append("Try to center yourself better in the frame.")
        
        if posture == "A":
            explanations.append("Excellent posture! Keep your shoulders back and head up.")
        elif posture == "B":
            explanations.append("Good posture. Try to sit up a bit straighter.")
        else:
            explanations.append("Focus on maintaining better posture - shoulders back, head up.")
        
        if lighting == "A":
            explanations.append("Perfect lighting! The camera is picking up your face clearly.")
        elif lighting == "B":
            explanations.append("Good lighting. Consider adjusting your lighting slightly.")
        else:
            explanations.append("Try to improve your lighting - ensure your face is well lit.")
        
        return " ".join(explanations)

    def get_latest_analysis(self) -> Dict[str, Any]:
        """Get the latest analysis results."""
        return self._last_analysis.copy()

    def close(self):
        """Clean up resources."""
        self._shutdown = True
        if hasattr(self, "executor"):
            self.executor.shutdown(wait=False)
