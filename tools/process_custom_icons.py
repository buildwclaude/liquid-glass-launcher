#!/usr/bin/env python3
"""
Turns user-supplied artwork into icon-pack drawables.

Usage: process_custom_icons.py <src_dir> <out_dir>

Every image in src_dir becomes <name>.png in out_dir:
center-cropped to a square, resized to 512x512, and the corners rounded
to the same tile shape the generated icons use. The file name (minus
extension) becomes the drawable name, e.g. spotify.png -> "spotify".
"""
import os
import sys

from PIL import Image, ImageChops, ImageDraw

S = 512

src, out = sys.argv[1], sys.argv[2]
os.makedirs(out, exist_ok=True)

for f in sorted(os.listdir(src)):
    name, ext = os.path.splitext(f)
    if ext.lower() not in (".png", ".jpg", ".jpeg", ".webp"):
        continue
    img = Image.open(os.path.join(src, f)).convert("RGBA")
    w, h = img.size
    side = min(w, h)
    img = img.crop(
        ((w - side) // 2, (h - side) // 2, (w + side) // 2, (h + side) // 2)
    )
    img = img.resize((S, S), Image.LANCZOS)
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [10, 10, S - 10, S - 10], radius=112, fill=255
    )
    alpha = ImageChops.multiply(img.split()[3], mask)
    img.putalpha(alpha)
    img.save(os.path.join(out, f"{name}.png"))
    print("processed", f, "->", f"{name}.png")
