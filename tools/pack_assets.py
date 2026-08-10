"""Downscale sliced art to on-screen sizes and stage it in the APK assets.

Sliced sprites are kept at source resolution in art/sprites for reuse, but the
game only ever draws them a few hundred pixels tall. Shipping the originals
would waste texture memory and VRAM bandwidth, so each category is resampled to
the height it actually occupies on a 1080p landscape screen.
"""

from __future__ import annotations

import json
import os
import shutil

from PIL import Image

SRC = r"D:\flutter_proj\Olympus_Surge\art\sprites"
DST = r"D:\flutter_proj\Olympus_Surge\app\src\main\assets\sprites"

# Target height in pixels on a 1920x1080 screen. Sprites are never upscaled.
TARGET_HEIGHT = {
    "heroes": 320,
    "enemies": 300,
    "elites": 460,
    "gems": 110,
    "vfx": 420,
    "arena": 900,
    "props": 300,
    "backgrounds": 1080,
    "ui": 420,
}


def main() -> None:
    if os.path.isdir(DST):
        shutil.rmtree(DST)

    with open(os.path.join(SRC, "sprites.json"), encoding="utf-8") as fh:
        sprites = json.load(fh)

    manifest = []
    total = 0
    for meta in sprites:
        path = os.path.join(SRC, meta["file"].replace("/", os.sep))
        img = Image.open(path)

        target = TARGET_HEIGHT[meta["category"]]
        if img.height > target:
            width = max(1, round(img.width * target / img.height))
            img = img.resize((width, target), Image.LANCZOS)

        out_dir = os.path.join(DST, meta["category"])
        os.makedirs(out_dir, exist_ok=True)
        out_path = os.path.join(out_dir, os.path.basename(meta["file"]))
        img.save(out_path, optimize=True)
        total += os.path.getsize(out_path)

        manifest.append({
            "name": meta["name"],
            "category": meta["category"],
            "file": f"sprites/{meta['category']}/{os.path.basename(meta['file'])}",
            "width": img.width,
            "height": img.height,
            "anchorX": meta["anchor_x"],
            "anchorY": meta["anchor_y"],
        })

    with open(os.path.join(os.path.dirname(DST), "sprites.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, indent=2)

    print(f"{len(manifest)} sprites staged, {total / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    main()
