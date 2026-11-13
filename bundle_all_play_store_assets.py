import os
import shutil
from PIL import Image, ImageDraw, ImageFilter

base_dir = "play_store_assets"
fmt_dir = os.path.join(base_dir, "formatted_screenshots")
os.makedirs(fmt_dir, exist_ok=True)

# 1. Copy App Icon (512x512) into formatted_screenshots
icon_src = os.path.join(base_dir, "icon_512x512.png")
icon_dst = os.path.join(fmt_dir, "app_icon_512x512.png")
if os.path.exists(icon_src):
    shutil.copy(icon_src, icon_dst)
    print("Copied app_icon_512x512.png")

# 2. Copy Feature Graphic (1024x500) into formatted_screenshots
fg_src = os.path.join(base_dir, "feature_graphic_1024x500.png")
fg_dst = os.path.join(fmt_dir, "feature_graphic_1024x500.png")
if os.path.exists(fg_src):
    shutil.copy(fg_src, fg_dst)
    print("Copied feature_graphic_1024x500.png")

# 3. Generate Tablet Screenshots (7-inch: 1200x1920, 10-inch: 1600x2560)
# Google Play Console has separate upload boxes for 7-inch and 10-inch tablets.
# We create studio-framed tablet assets on the warm linen background for the top 4 screens.
tablet_7_dir = os.path.join(fmt_dir, "7_inch_tablet")
tablet_10_dir = os.path.join(fmt_dir, "10_inch_tablet")
os.makedirs(tablet_7_dir, exist_ok=True)
os.makedirs(tablet_10_dir, exist_ok=True)

key_screens = [
    ("01_welcome.png", "01_welcome_tablet.png"),
    ("02_smart_scan.png", "02_smart_scan_tablet.png"),
    ("03_set_reminders.png", "03_set_reminders_tablet.png"),
    ("07_home_timeline.png", "04_home_timeline_tablet.png"),
]

bg_color = (250, 247, 242) # Warm linen

def create_tablet_asset(src_path, dst_path, canvas_w, canvas_h, phone_h):
    canvas = Image.new("RGB", (canvas_w, canvas_h), bg_color)
    draw = ImageDraw.Draw(canvas)
    
    # Soft background ambiance
    draw.ellipse((-100, -100, canvas_w // 2, canvas_h // 2), fill=(241, 233, 220))
    draw.ellipse((canvas_w // 2, canvas_h // 2, canvas_w + 100, canvas_h + 100), fill=(252, 240, 236))
    
    screen = Image.open(src_path).convert("RGBA")
    sw, sh = screen.size
    
    # Scale screen
    ratio = phone_h / float(sh)
    pw = int(sw * ratio)
    screen_scaled = screen.resize((pw, phone_h), Image.Resampling.LANCZOS)
    
    # Phone frame
    bezel = 8
    fw = pw + bezel * 2
    fh = phone_h + bezel * 2
    frame = Image.new("RGBA", (fw, fh), (0, 0, 0, 0))
    fdraw = ImageDraw.Draw(frame)
    fdraw.rounded_rectangle((0, 0, fw - 1, fh - 1), radius=36, fill=(35, 30, 27, 255), outline=(60, 54, 49), width=1)
    
    mask = Image.new("L", (pw, phone_h), 0)
    mdraw = ImageDraw.Draw(mask)
    mdraw.rounded_rectangle((0, 0, pw - 1, phone_h - 1), radius=30, fill=255)
    
    frame.paste(screen_scaled, (bezel, bezel), mask)
    
    # Shadow
    shadow = Image.new("RGBA", (fw + 60, fh + 60), (0, 0, 0, 0))
    sdraw = ImageDraw.Draw(shadow)
    sdraw.rounded_rectangle((20, 24, fw + 28, fh + 32), radius=44, fill=(44, 39, 35, 60))
    shadow = shadow.filter(ImageFilter.GaussianBlur(18))
    
    # Center phone on tablet canvas
    px = (canvas_w - fw) // 2
    py = (canvas_h - fh) // 2
    
    canvas.paste(shadow, (px - 20, py - 12), shadow)
    canvas.paste(frame, (px, py), frame)
    
    canvas.save(dst_path, "PNG")

for src_name, dst_name in key_screens:
    src_p = os.path.join(fmt_dir, src_name)
    if os.path.exists(src_p):
        # 7-inch tablet: 1200 x 1920
        create_tablet_asset(src_p, os.path.join(tablet_7_dir, dst_name), 1200, 1920, 1680)
        # 10-inch tablet: 1600 x 2560
        create_tablet_asset(src_p, os.path.join(tablet_10_dir, dst_name), 1600, 2560, 2240)
        print(f"Created tablet versions for {src_name}")

print("All Play Store graphics generated in formatted_screenshots successfully!")
