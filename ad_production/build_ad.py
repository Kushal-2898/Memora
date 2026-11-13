import os
import subprocess
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
RAW_DIR = BASE_DIR / "raw"
APP_DIR = BASE_DIR / "app_captures"
AUDIO_DIR = BASE_DIR / "audio"
OUTPUT_DIR = BASE_DIR / "output"

def check_files():
    required = [
        RAW_DIR / "scene1_frustration.mp4",
        RAW_DIR / "scene2_unpacking.mp4",
        APP_DIR / "scene3_app_scan.mp4",
        RAW_DIR / "scene4_relief.mp4",
    ]
    missing = [f.name for f in required if not f.exists()]
    return missing

def main():
    missing = check_files()
    if missing:
        print(f"Waiting for scenes: {', '.join(missing)}")
        print(f"Place generated clips into: {RAW_DIR}")
        print(f"Place app capture into: {APP_DIR}")
        return

    out_file = OUTPUT_DIR / "memora_ugc_short.mp4"
    print("All scene assets found! Assembling 9:16 vertical video via FFmpeg...")
    # FFmpeg assembly command will compile the 4 clips into 1080x1920 with smooth transitions
    print(f"Target output: {out_file}")

if __name__ == "__main__":
    main()
