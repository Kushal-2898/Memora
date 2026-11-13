import urllib.request
import re
from pathlib import Path

headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}

def find_clips_with_info(query):
    url = f"https://mixkit.co/free-stock-video/{query}/"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            # Extract video cards: title, url
            # e.g. href="/free-stock-video/young-woman-opening-a-refrigerator-10337/"
            matches = re.findall(r'href="(/free-stock-video/[a-z0-9\-]+-(\d+)/)"', html)
            results = []
            for path, vid_id in matches:
                title = path.replace("/free-stock-video/", "").replace(f"-{vid_id}/", "").replace("-", " ")
                video_url = f"https://assets.mixkit.co/videos/{vid_id}/{vid_id}-720.mp4"
                results.append((title, video_url))
            return results
    except Exception as e:
        print(f"Error {query}: {e}")
        return []

print("=== FRIDGE / FOOD WASTE CANDIDATES ===")
for title, vurl in find_clips_with_info("fridge")[:6]:
    print(f"Title: {title} | {vurl}")

print("\n=== GROCERY CANDIDATES ===")
for title, vurl in find_clips_with_info("groceries")[:6]:
    print(f"Title: {title} | {vurl}")

print("\n=== SMARTPHONE / NOTIFICATION CANDIDATES ===")
for title, vurl in find_clips_with_info("smartphone")[:6]:
    print(f"Title: {title} | {vurl}")
