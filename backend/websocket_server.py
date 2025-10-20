import asyncio
import json
import logging
import websockets
from websockets.exceptions import ConnectionClosed
from typing import Set, Dict, Any
import base64
import cv2
import numpy as np
from PIL import Image
import io

from speech_coach_processor import SpeechCoachProcessor

logger = logging.getLogger(__name__)

class SpeechCoachWebSocketServer:
    """WebSocket server for real-time speech coaching feedback."""
    
    def __init__(self, host: str = "0.0.0.0", port: int = 8765):
        self.host = host
        self.port = port
        self.clients: Set = set()
        self.processor = SpeechCoachProcessor()
        self.running = False
        
    async def register_client(self, websocket):
        """Register a new client connection."""
        self.clients.add(websocket)
        logger.info(f"📱 Client connected. Total clients: {len(self.clients)}")
        
    async def unregister_client(self, websocket):
        """Unregister a client connection."""
        self.clients.discard(websocket)
        logger.info(f"📱 Client disconnected. Total clients: {len(self.clients)}")
        
    async def handle_client(self, websocket, path):
        """Handle individual client connection."""
        await self.register_client(websocket)
        
        try:
            async for message in websocket:
                await self.handle_message(websocket, message)
        except ConnectionClosed:
            logger.info("📱 Client connection closed")
        except Exception as e:
            logger.error(f"❌ Error handling client: {e}")
        finally:
            await self.unregister_client(websocket)
            
    async def handle_message(self, websocket, message: str):
        """Handle incoming message from client."""
        try:
            data = json.loads(message)
            message_type = data.get("type")
            
            if message_type == "frame":
                # Process video frame
                await self.process_frame(data.get("frame"))
            elif message_type == "ping":
                # Respond to ping
                await self.send_message(websocket, {"type": "pong"})
            else:
                logger.warning(f"⚠️ Unknown message type: {message_type}")
                
        except json.JSONDecodeError as e:
            logger.error(f"❌ Invalid JSON message: {e}")
        except Exception as e:
            logger.error(f"❌ Error handling message: {e}", exc_info=True)
            
    async def process_frame(self, frame_data: str):
        """Process a video frame and send analysis back to clients."""
        try:
            # Decode base64 image
            image_data = base64.b64decode(frame_data)
            image = Image.open(io.BytesIO(image_data))
            frame_array = np.array(image)
            
            # Convert RGB to BGR for OpenCV
            frame_array = cv2.cvtColor(frame_array, cv2.COLOR_RGB2BGR)
            
            # Analyze frame
            analysis = await self.processor._analyze_frame_async(frame_array)
            
            # Send analysis to all connected clients
            await self.broadcast_analysis(analysis)
            
        except base64.binascii.Error as e:
            logger.error(f"❌ Invalid base64 data: {e}")
            # Send error response instead of crashing
            error_analysis = {
                "eye_contact": "C",
                "framing": "C", 
                "posture": "C",
                "lighting": "C",
                "explanation": "Error processing frame - invalid image data"
            }
            await self.broadcast_analysis(error_analysis)
        except Exception as e:
            logger.error(f"❌ Error processing frame: {e}")
            # Send error response instead of crashing
            error_analysis = {
                "eye_contact": "C",
                "framing": "C", 
                "posture": "C",
                "lighting": "C",
                "explanation": f"Error processing frame: {str(e)}"
            }
            await self.broadcast_analysis(error_analysis)
            
    async def broadcast_analysis(self, analysis: Dict[str, Any]):
        """Broadcast analysis results to all connected clients."""
        if not self.clients:
            return
            
        message = {
            "type": "analysis",
            "data": analysis
        }
        
        # Send to all connected clients
        disconnected_clients = set()
        for client in self.clients:
            try:
                await client.send(json.dumps(message))
            except ConnectionClosed:
                disconnected_clients.add(client)
            except Exception as e:
                logger.error(f"❌ Error sending to client: {e}")
                disconnected_clients.add(client)
                
        # Remove disconnected clients
        for client in disconnected_clients:
            await self.unregister_client(client)
            
    async def send_message(self, websocket, message: Dict[str, Any]):
        """Send a message to a specific client."""
        try:
            await websocket.send(json.dumps(message))
        except ConnectionClosed:
            await self.unregister_client(websocket)
        except Exception as e:
            logger.error(f"❌ Error sending message: {e}")
            
    async def start(self):
        """Start the WebSocket server."""
        logger.info(f"🚀 Starting Speech Coach WebSocket server on {self.host}:{self.port}")
        
        self.running = True
        async with websockets.serve(
            self.handle_client,
            self.host,
            self.port,
            ping_interval=20,
            ping_timeout=10
        ):
            logger.info(f"✅ Speech Coach WebSocket server running on ws://{self.host}:{self.port}")
            await asyncio.Future()  # Run forever
            
    async def stop(self):
        """Stop the WebSocket server."""
        self.running = False
        self.processor.close()
        logger.info("🛑 Speech Coach WebSocket server stopped")

async def main():
    """Main function to run the server."""
    # Configure logging
    logging.basicConfig(
        level=logging.INFO,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    
    server = SpeechCoachWebSocketServer()
    
    try:
        await server.start()
    except KeyboardInterrupt:
        logger.info("🛑 Server stopped by user")
    finally:
        await server.stop()

if __name__ == "__main__":
    asyncio.run(main())
