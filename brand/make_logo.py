# -*- coding: utf-8 -*-
"""Generates the Wergêr brand logo in several sizes + an Android adaptive foreground."""
import os, math
from PIL import Image, ImageDraw, ImageFont, ImageFilter

OUT = os.path.dirname(os.path.abspath(__file__))

TEAL = (13, 110, 95)      # deep teal
GREEN = (27, 94, 32)      # deep green
ACCENT = (255, 204, 51)   # warm yellow (Kurdish flag sun)
WHITE = (255, 255, 255)

def font(path_list, size):
    for p in path_list:
        try:
            return ImageFont.truetype(p, size)
        except Exception:
            continue
    return ImageFont.load_default()

BOLD = [r"C:\Windows\Fonts\arialbd.ttf", r"C:\Windows\Fonts\seguisb.ttf"]

def gradient(size, c1, c2):
    base = Image.new("RGB", (size, size), c1)
    top = Image.new("RGB", (size, size), c2)
    mask = Image.new("L", (size, size))
    md = mask.load()
    for y in range(size):
        for x in range(size):
            # diagonal gradient
            md[x, y] = int(255 * ((x + y) / (2 * size)))
    base.paste(top, (0, 0), mask)
    return base

def rounded_mask(size, radius):
    m = Image.new("L", (size, size), 0)
    d = ImageDraw.Draw(m)
    d.rounded_rectangle([0, 0, size - 1, size - 1], radius=radius, fill=255)
    return m

def draw_glyph(img, size, with_bubbles=True):
    d = ImageDraw.Draw(img)
    # Speech bubbles suggesting translation/dialogue
    if with_bubbles:
        d.ellipse([size*0.12, size*0.14, size*0.40, size*0.40], fill=(255, 255, 255, 40))
    # Big "W"
    f = font(BOLD, int(size * 0.52))
    text = "W"
    bb = d.textbbox((0, 0), text, font=f)
    tw, th = bb[2]-bb[0], bb[3]-bb[1]
    tx = (size - tw) / 2 - bb[0]
    ty = (size - th) / 2 - bb[1] + size*0.04
    # subtle shadow
    d.text((tx+size*0.012, ty+size*0.012), text, font=f, fill=(0, 0, 0, 90))
    d.text((tx, ty), text, font=f, fill=WHITE)
    # circumflex accent (ê) above, in accent color
    cf = font(BOLD, int(size * 0.20))
    ctext = "^"
    cbb = d.textbbox((0, 0), ctext, font=cf)
    cw = cbb[2]-cbb[0]
    cx = (size - cw) / 2 - cbb[0]
    cy = ty - size*0.18
    d.text((cx, cy), ctext, font=cf, fill=ACCENT)

def make_logo(size, radius_frac=0.22):
    g = gradient(size, TEAL, GREEN).convert("RGBA")
    glyph_layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw_glyph(glyph_layer, size)
    g = Image.alpha_composite(g, glyph_layer)
    mask = rounded_mask(size, int(size * radius_frac))
    out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    out.paste(g, (0, 0), mask)
    return out

def make_foreground(size=432):
    # Transparent background, centered glyph at ~62% safe zone for adaptive icons.
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    inner = int(size * 0.62)
    layer = Image.new("RGBA", (inner, inner), (0, 0, 0, 0))
    draw_glyph(layer, inner, with_bubbles=False)
    img.paste(layer, ((size-inner)//2, (size-inner)//2), layer)
    return img

for s in (1024, 512, 256, 192):
    make_logo(s).save(os.path.join(OUT, f"logo_{s}.png"))
make_foreground(432).save(os.path.join(OUT, "icon_foreground.png"))
# Simple flat background tile for adaptive icon
bg = gradient(432, TEAL, GREEN)
bg.save(os.path.join(OUT, "icon_background.png"))
print("logos written to", OUT)
