import os
from PIL import Image

in_dir = "C:/Users/meruv/.gemini/antigravity/brain/0dd08cc1-7011-4819-a6fd-077b0073e304/extracted_user_images"
out_dir = "play_store_assets/formatted_screenshots"
os.makedirs(out_dir, exist_ok=True)

# Correct chronological / marketing order:
# 1. Onboarding Welcome (user_screen_1)
# 2. Scan product labels (user_screen_4)
# 3. Set reminders (user_screen_2)
# 4. Stay on top / Notifications (user_screen_7)
# 5. Track everything / Categories (user_screen_3)
# 6. You're all set (user_screen_5)
# 7. Home screen (user_screen_6)
# 8. Settings & Privacy (user_screen_8)

ordered_files = [
    ("user_screen_1.jpg", "01_welcome.png"),
    ("user_screen_4.jpg", "02_smart_scan.png"),
    ("user_screen_2.jpg", "03_set_reminders.png"),
    ("user_screen_7.jpg", "04_notifications.png"),
    ("user_screen_3.jpg", "05_categories.png"),
    ("user_screen_5.jpg", "06_all_set.png"),
    ("user_screen_6.jpg", "07_home_timeline.png"),
    ("user_screen_8.jpg", "08_privacy_settings.png"),
]

for src_name, dst_name in ordered_files:
    src_path = os.path.join(in_dir, src_name)
    im = Image.open(src_path).convert("RGB")
    w, h = im.size
    
    # Crop the black status bar at the top (top ~65px in 610x1356 space)
    # and navigation bar at the bottom (bottom ~45px)
    crop_box = (0, 65, w, h - 45)
    cropped = im.crop(crop_box)
    
    # Scale to 1080 x 2160 (exact 1:2 ratio, 100% Google Play compliant)
    target_w, target_h = 1080, 2160
    scaled = cropped.resize((target_w, target_h), Image.Resampling.LANCZOS)
    
    dst_path = os.path.join(out_dir, dst_name)
    scaled.save(dst_path, "PNG")
    print(f"Saved {dst_name}: {target_w}x{target_h}")
