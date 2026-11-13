import urllib.request
import re
import json
import sys

headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}

def search_mixkit(query):
    url = f"https://mixkit.co/free-stock-video/{query}/"
    req = urllib.request.Request(url, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            html = resp.read().decode('utf-8', errors='ignore')
            # Look for video src or download links
            matches = re.findall(r'https://assets.mixkit.co/videos/[^\s\"\'\<\>]+\.mp4', html)
            return list(set(matches))
    except Exception as e:
        print(f"Mixkit error for {query}: {e}")
        return []

print("Searching Mixkit for fridge...")
fridge_clips = search_mixkit("fridge")
print(f"Found {len(fridge_clips)} fridge clips:")
for c in fridge_clips[:3]:
    print(" ", c)

print("\nSearching Mixkit for groceries...")
groceries_clips = search_mixkit("groceries")
print(f"Found {len(groceries_clips)} groceries clips:")
for c in groceries_clips[:3]:
    print(" ", c)

print("\nSearching Mixkit for smartphone...")
phone_clips = search_mixkit("smartphone")
print(f"Found {len(phone_clips)} phone clips:")
for c in phone_clips[:3]:
    print(" ", c)
