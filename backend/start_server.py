#!/usr/bin/env python3
"""
Start the Speech Coach WebSocket server.
This server provides real-time vision analysis for speech coaching.
"""

import asyncio
import logging
import sys
from pathlib import Path

# Add the current directory to Python path
sys.path.insert(0, str(Path(__file__).parent))

from websocket_server import main

if __name__ == "__main__":
    print("🎯 Starting Speech Coach AI Backend Server...")
    print("📱 This server will provide real-time vision analysis for your Android app")
    print("🔗 Connect your Android app to: ws://localhost:8765")
    print("⏹️  Press Ctrl+C to stop the server")
    print("-" * 50)
    
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        print("\n🛑 Server stopped by user")
    except Exception as e:
        print(f"❌ Server error: {e}")
        sys.exit(1)
