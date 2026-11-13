import subprocess
import sys
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent
RAW_DIR = BASE_DIR / "raw"
APP_DIR = BASE_DIR / "app_captures"
AUDIO_DIR = BASE_DIR / "audio"
OUTPUT_DIR = BASE_DIR / "output"
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
FONT_PATH = "ad_production/font.ttf"

clips_info = [
    {
        "src": RAW_DIR / "scene1_frustration.mp4",
        "out": OUTPUT_DIR / "part1.mp4",
        "ss": "0.5",
        "duration": 3.5,
        "text": "Stop throwing away expired food"
    },
    {
        "src": RAW_DIR / "scene2_groceries.mp4",
        "out": OUTPUT_DIR / "part2.mp4",
        "ss": "0.5",
        "duration": 3.5,
        "text": "Point your camera at the date"
    },
    {
        "src": APP_DIR / "scene3_app_scan.mp4",
        "out": OUTPUT_DIR / "part3.mp4",
        "ss": "0.0",
        "duration": 4.5,
        "text": "Instant reminder. Fully offline."
    },
    {
        "src": RAW_DIR / "scene4_phone_alert.mp4",
        "out": OUTPUT_DIR / "part4.mp4",
        "ss": "0.5",
        "duration": 4.5,
        "text": "Never miss an expiration date"
    }
]

print("Processing scenes to 1080x1920 vertical format with captions...")

part_files = []
for i, c in enumerate(clips_info):
    text_filter = (
        f"drawtext=fontfile='{FONT_PATH}':text='{c['text']}':fontsize=48:fontcolor=white:borderw=4:bordercolor=black:"
        f"x=(w-text_w)/2:y=h*0.82:box=1:boxcolor=black@0.65:boxborderw=18"
    )
    scale_filter = (
        f"scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920,setsar=1,{text_filter}"
    )

    cmd = [
        "ffmpeg", "-y",
        "-ss", c["ss"],
        "-i", str(c["src"]),
        "-t", str(c["duration"]),
        "-vf", scale_filter,
        "-r", "30",
        "-c:v", "libx264", "-pix_fmt", "yuv420p",
        "-an",
        str(c["out"])
    ]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Error processing clip {i+1}:", res.stderr)
        sys.exit(1)
    print(f"-> Rendered part {i+1} ({c['duration']}s)")
    part_files.append(c["out"])

# Create concat list
concat_list_file = OUTPUT_DIR / "concat_list.txt"
with open(concat_list_file, "w") as f:
    for pf in part_files:
        escaped_path = str(pf.resolve()).replace("\\", "/")
        f.write(f"file '{escaped_path}'\n")

# Concatenate and attach narration audio
final_output = OUTPUT_DIR / "memora_ugc_short.mp4"
audio_file = AUDIO_DIR / "narration.mp3"

concat_cmd = [
    "ffmpeg", "-y",
    "-f", "concat", "-safe", "0",
    "-i", str(concat_list_file),
    "-i", str(audio_file),
    "-c:v", "libx264", "-pix_fmt", "yuv420p",
    "-c:a", "aac", "-b:a", "192k",
    "-shortest",
    str(final_output)
]

print("\nAssembling final video with audio track...")
concat_res = subprocess.run(concat_cmd, capture_output=True, text=True)
if concat_res.returncode == 0:
    print(f"\nSUCCESS! Ad video created at:\n{final_output}")
else:
    print("Concatenation error:", concat_res.stderr)
