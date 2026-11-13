import urllib.request
import re

headers = {'User-Agent': 'Mozilla/5.0'}

def find_clips(query):
    url = f"https://mixkit.co/free-stock-video/{query}/"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            matches = re.findall(r'href="(/free-stock-video/[a-z0-9\-]+-(\d+)/)"', html)
            seen = set()
            results = []
            for path, vid_id in matches:
                if vid_id not in seen:
                    seen.add(vid_id)
                    title = path.replace("/free-stock-video/", "").replace(f"-{vid_id}/", "").replace("-", " ")
                    video_url = f"https://assets.mixkit.co/videos/{vid_id}/{vid_id}-720.mp4"
                    results.append((title, video_url))
            return results
    except Exception as e:
        return []

print("=== KITCHEN / FOOD ===")
for t, u in find_clips("kitchen-food")[:5]:
    print(t, "|", u)

print("=== MILK ===")
for t, u in find_clips("milk")[:5]:
    print(t, "|", u)

print("=== MEDICINE ===")
for t, u in find_clips("medicine")[:5]:
    print(t, "|", u)
