import urllib.request
from pathlib import Path

raw_dir = Path("ad_production/raw")
raw_dir.mkdir(parents=True, exist_ok=True)

downloads = {
    "scene1_frustration.mp4": "https://assets.mixkit.co/videos/40518/40518-720.mp4", # POV inside fridge opening
    "scene2_groceries.mp4": "https://assets.mixkit.co/videos/6102/6102-720.mp4",     # Grocery products
    "scene4_phone_alert.mp4": "https://assets.mixkit.co/videos/4915/4915-720.mp4"    # Hands checking smartphone alert
}

headers = {'User-Agent': 'Mozilla/5.0'}

for name, url in downloads.items():
    out_path = raw_dir / name
    print(f"Downloading {name} from {url}...")
    req = urllib.request.Request(url, headers=headers)
    with urllib.request.urlopen(req, timeout=30) as resp:
        data = resp.read()
        with open(out_path, "wb") as f:
            f.write(data)
        print(f"-> Saved {name} ({len(data)} bytes)")

print("\nAll 3 lifestyle video clips downloaded successfully!")
