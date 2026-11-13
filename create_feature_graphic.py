import os
from PIL import Image, ImageDraw, ImageFont, ImageFilter

def draw_camera_icon(draw, x, y, color):
    draw.rounded_rectangle((x + 2, y + 6, x + 22, y + 20), radius=3, fill=color)
    draw.rounded_rectangle((x + 7, y + 2, x + 17, y + 6), radius=2, fill=color)
    draw.ellipse((x + 8, y + 9, x + 16, y + 17), fill=(255, 255, 255))
    draw.ellipse((x + 10, y + 11, x + 14, y + 15), fill=color)

def draw_bell_icon(draw, x, y, color):
    draw.pieslice((x + 4, y + 3, x + 20, y + 22), 180, 360, fill=color)
    draw.rectangle((x + 4, y + 12, x + 20, y + 18), fill=color)
    draw.rounded_rectangle((x + 2, y + 17, x + 22, y + 20), radius=2, fill=color)
    draw.ellipse((x + 10, y + 20, x + 14, y + 23), fill=color)

def draw_shield_icon(draw, x, y, color):
    draw.polygon([(x + 12, y + 2), (x + 22, y + 6), (x + 22, y + 14), (x + 12, y + 22), (x + 2, y + 14), (x + 2, y + 6)], fill=color)
    draw.line([(x + 6, y + 11), (x + 10, y + 16), (x + 17, y + 8)], fill=(255, 255, 255), width=2)

def create_feature_graphic():
    W, H = 1024, 500
    # Background: Warm Linen (#FAF7F2)
    canvas = Image.new("RGB", (W, H), (250, 247, 242))
    draw = ImageDraw.Draw(canvas)

    # 1. Subtle warm organic background shapes
    draw.ellipse((-140, -60, 520, 580), fill=(240, 235, 225)) # Warm Parchment
    draw.ellipse((-140, -60, 520, 580), outline=(232, 225, 215), width=1)
    draw.ellipse((600, -180, 1200, 420), fill=(252, 240, 236)) # Terracotta glow
    draw.ellipse((680, 220, 1220, 720), fill=(238, 244, 240)) # Eucalyptus glow

    # 2. Add App Icon with realistic soft drop shadow
    icon_path = "play_store_assets/icon_512x512.png"
    if os.path.exists(icon_path):
        icon = Image.open(icon_path).convert("RGBA")
        icon_size = 130
        icon = icon.resize((icon_size, icon_size), Image.Resampling.LANCZOS)

        shadow = Image.new("RGBA", (icon_size + 36, icon_size + 36), (0, 0, 0, 0))
        shadow_draw = ImageDraw.Draw(shadow)
        shadow_draw.rounded_rectangle((12, 14, 12 + icon_size - 4, 14 + icon_size - 4), radius=34, fill=(44, 39, 35, 70))
        shadow = shadow.filter(ImageFilter.GaussianBlur(12))

        canvas.paste(shadow, (48, 48), shadow)
        canvas.paste(icon, (60, 55), icon)

    # 3. Typography
    title_font = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 46)
    subtitle_font = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 20)
    tagline_font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 15)
    pill_bold = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 15)
    pill_font = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 14)

    primary_navy = (44, 39, 35)       # #2C2723 (Warm charcoal ink)
    terracotta = (224, 109, 83)       # #E06D53 (Warm terracotta)
    text_muted = (125, 115, 106)      # #7D736A (Warm taupe)
    card_border = (237, 231, 221)     # #EDE7DD
    card_bg = (255, 255, 255)         # Card white

    # Brand Title & Subtitle
    text_x = 212
    draw.text((text_x, 56), "Memora", font=title_font, fill=primary_navy)
    draw.text((text_x, 113), "Expiry & Bill Reminder", font=subtitle_font, fill=terracotta)
    draw.text((text_x, 145), "Never miss an important date again.", font=tagline_font, fill=text_muted)

    # 4. Feature Benefit Cards
    pills = [
        ("On-Device OCR Scanner", "Instant date extraction without cloud uploads", "camera"),
        ("Exact Morning Alarms", "Reliable 9:00 AM alerts with snooze & complete", "bell"),
        ("100% Private & Local-First", "Guest mode by default, zero mandatory account", "shield")
    ]

    pill_y = 210
    for title, desc, icon_type in pills:
        pill_w = 460
        pill_h = 66
        
        p_shadow = Image.new("RGBA", (pill_w + 20, pill_h + 20), (0, 0, 0, 0))
        p_draw = ImageDraw.Draw(p_shadow)
        p_draw.rounded_rectangle((6, 7, pill_w + 6, pill_h + 7), radius=16, fill=(44, 39, 35, 18))
        p_shadow = p_shadow.filter(ImageFilter.GaussianBlur(6))
        canvas.paste(p_shadow, (52, pill_y - 2), p_shadow)

        p_img = Image.new("RGBA", (pill_w, pill_h), (0, 0, 0, 0))
        p_img_draw = ImageDraw.Draw(p_img)
        p_img_draw.rounded_rectangle((0, 0, pill_w - 1, pill_h - 1), radius=16, fill=card_bg, outline=card_border, width=1)
        
        badge_size = 40
        p_img_draw.ellipse((14, 13, 14 + badge_size, 13 + badge_size), fill=(252, 240, 236))
        
        if icon_type == "camera":
            draw_camera_icon(p_img_draw, 22, 21, terracotta)
        elif icon_type == "bell":
            draw_bell_icon(p_img_draw, 22, 20, terracotta)
        elif icon_type == "shield":
            draw_shield_icon(p_img_draw, 22, 20, terracotta)
            
        canvas.paste(p_img, (60, pill_y), p_img)
        
        draw.text((120, pill_y + 13), title, font=pill_bold, fill=primary_navy)
        draw.text((120, pill_y + 35), desc, font=pill_font, fill=text_muted)

        pill_y += 82

    # 5. Right side: Authentic, Slender Modern Smartphone (19.5:9 ratio)
    screen_path = "play_store_assets/screenshot_3_reminders_home.png"
    if os.path.exists(screen_path):
        screen = Image.open(screen_path).convert("RGBA")
        
        # Crop to pure screen content: from below status bar down to just below the floating Scan button
        # (1080 x 2400 original)
        crop_box = (0, 110, 1080, 2240)
        screen_cropped = screen.crop(crop_box)
        
        # Modern Phone Chassis: 228px width by 465px height (Aspect ratio ~ 1:2.04 - exact modern smartphone!)
        phone_w = 230
        phone_h = 464
        bezel = 6
        inner_w = phone_w - (bezel * 2)
        inner_h = phone_h - (bezel * 2)
        
        # Scale screenshot to fit inside phone screen
        screen_resized = screen_cropped.resize((inner_w, inner_h), Image.Resampling.LANCZOS)
        
        # Chassis image
        phone_frame = Image.new("RGBA", (phone_w, phone_h), (0, 0, 0, 0))
        pf_draw = ImageDraw.Draw(phone_frame)
        
        # Phone body (Dark charcoal titanium chassis with subtle metallic edge)
        pf_draw.rounded_rectangle((0, 0, phone_w - 1, phone_h - 1), radius=36, fill=(35, 30, 27, 255), outline=(60, 54, 49), width=1)
        
        # Screen mask with modern rounded display corners
        screen_mask = Image.new("L", (inner_w, inner_h), 0)
        sm_draw = ImageDraw.Draw(screen_mask)
        sm_draw.rounded_rectangle((0, 0, inner_w - 1, inner_h - 1), radius=30, fill=255)
        
        # Paste screen into chassis
        phone_frame.paste(screen_resized, (bezel, bezel), screen_mask)
        
        # Modern center punch-hole camera lens at top
        cam_x = phone_w // 2
        cam_y = bezel + 10
        pf_draw.ellipse((cam_x - 5, cam_y - 5, cam_x + 5, cam_y + 5), fill=(10, 10, 10, 255))
        pf_draw.ellipse((cam_x - 2, cam_y - 2, cam_x + 2, cam_y + 2), fill=(30, 45, 60, 200)) # subtle camera glass reflection
        
        # Deep diffused drop shadow behind the phone
        dev_shadow = Image.new("RGBA", (phone_w + 60, phone_h + 60), (0, 0, 0, 0))
        ds_draw = ImageDraw.Draw(dev_shadow)
        ds_draw.rounded_rectangle((18, 22, phone_w + 26, phone_h + 30), radius=44, fill=(44, 39, 35, 75))
        dev_shadow = dev_shadow.filter(ImageFilter.GaussianBlur(16))

        # Position phone mockup on right side
        phone_x = 724
        phone_y = 18
        canvas.paste(dev_shadow, (phone_x - 18, phone_y - 10), dev_shadow)
        canvas.paste(phone_frame, (phone_x, phone_y), phone_frame)

    # Save output
    out_dir = "C:/Users/meruv/.gemini/antigravity/brain/0dd08cc1-7011-4819-a6fd-077b0073e304"
    canvas.save(os.path.join(out_dir, "new_feature_graphic.png"), "PNG")
    
    play_store_path = "play_store_assets/feature_graphic_1024x500.png"
    canvas.save(play_store_path, "PNG")
    print("Updated phone feature graphic:", play_store_path)

if __name__ == "__main__":
    create_feature_graphic()
