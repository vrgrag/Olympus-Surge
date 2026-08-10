"""Adaptive launcher icon generator for Olympus Surge.

The source icon is a full-bleed square composition: a golden bolt in the middle
with four gemstones near the corners and a meander frame at the edges. An
adaptive icon crops anything outside the central 66% safe zone, so the artwork
is scaled to fit that zone on the foreground layer, while the background layer
is a blurred, over-scaled copy of the same art. That keeps every gemstone
visible under any mask shape and leaves no empty margins.
"""

from __future__ import annotations

import os

from PIL import Image, ImageEnhance, ImageFilter

SRC = r"D:\flutter_proj\Olympus_Surge\assets\ui\Icon.png"
RES = r"D:\flutter_proj\Olympus_Surge\app\src\main\res"
REVIEW = r"D:\flutter_proj\Olympus_Surge\art\review"

# Adaptive icons are 108dp; the guaranteed-visible circle is 66dp across.
ADAPTIVE_DP = 108
SAFE_DP = 68
DENSITIES = {"mdpi": 1, "hdpi": 1.5, "xhdpi": 2, "xxhdpi": 3, "xxxhdpi": 4}
LEGACY_DP = 48

FOREGROUND_XML = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@mipmap/ic_launcher_background" />
    <foreground android:drawable="@mipmap/ic_launcher_foreground" />
    <monochrome android:drawable="@mipmap/ic_launcher_monochrome" />
</adaptive-icon>
"""


def soft_squircle(size: int, radius_frac: float, feather_frac: float) -> Image.Image:
    """Rounded-square mask with a soft edge, drawn oversampled for smoothness."""
    from PIL import ImageDraw

    scale = 4
    big = size * scale
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        (0, 0, big - 1, big - 1), radius=big * radius_frac, fill=255
    )
    mask = mask.filter(ImageFilter.GaussianBlur(big * feather_frac))
    return mask.resize((size, size), Image.LANCZOS)


def foreground(source: Image.Image, size: int) -> Image.Image:
    """Artwork inside the safe zone, edges feathered so no hard frame shows."""
    inner = round(size * SAFE_DP / ADAPTIVE_DP)
    art = source.resize((inner, inner), Image.LANCZOS)
    art.putalpha(soft_squircle(inner, radius_frac=0.24, feather_frac=0.018))

    # A faint golden bloom ties the artwork into the background gradient.
    glow_size = round(inner * 1.22)
    glow = Image.new("RGBA", (glow_size, glow_size), (0, 0, 0, 0))
    glow.paste((255, 205, 90, 150), (0, 0, glow_size, glow_size),
               soft_squircle(glow_size, radius_frac=0.30, feather_frac=0.06))
    glow = glow.filter(ImageFilter.GaussianBlur(glow_size * 0.05))

    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.alpha_composite(glow, ((size - glow_size) // 2, (size - glow_size) // 2))
    canvas.alpha_composite(art, ((size - inner) // 2, (size - inner) // 2))
    return canvas


def background(source: Image.Image, size: int) -> Image.Image:
    """Storm-blue radial gradient sampled from the artwork's own palette."""
    # Brand palette: stormy Olympus night, so the golden bolt reads at 48dp.
    center, edge = (30, 52, 104), (8, 13, 34)

    supersample = 4
    small_size = max(16, size // supersample)
    grad = Image.new("RGB", (small_size, small_size))
    mid = (small_size - 1) / 2
    peak = (mid**2 + mid**2) ** 0.5
    for y in range(small_size):
        for x in range(small_size):
            d = min((((x - mid) ** 2 + (y - mid) ** 2) ** 0.5) / peak, 1.0)
            t = d**1.4
            grad.putpixel(
                (x, y),
                tuple(int(center[i] + (edge[i] - center[i]) * t) for i in range(3)),
            )
    grad = grad.resize((size, size), Image.LANCZOS)
    return ImageEnhance.Color(grad).enhance(1.2).convert("RGBA")


# Zeus' bolt drawn as a clean shape; tracing it out of the painted artwork
# produces ragged edges that read badly at launcher sizes.
BOLT = [
    (0.60, 0.02), (0.20, 0.56), (0.42, 0.56), (0.30, 0.98),
    (0.80, 0.40), (0.55, 0.40), (0.78, 0.02),
]


def monochrome(source: Image.Image, size: int) -> Image.Image:
    """Themed-icon layer: a flat white bolt silhouette."""
    from PIL import ImageDraw

    scale = 4
    inner = round(size * SAFE_DP / ADAPTIVE_DP) * scale
    mask = Image.new("L", (inner, inner), 0)
    ImageDraw.Draw(mask).polygon(
        [(x * inner, y * inner) for x, y in BOLT], fill=255
    )
    mask = mask.resize((inner // scale, inner // scale), Image.LANCZOS)

    art = Image.new("RGBA", mask.size, (255, 255, 255, 0))
    art.putalpha(mask)

    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    offset = (size - mask.width) // 2
    canvas.alpha_composite(art, (offset, offset))
    return canvas


def legacy(source: Image.Image, size: int, circular: bool) -> Image.Image:
    """Pre-API-26 icon: art composited over its own blurred backdrop."""
    icon = background(source, size)
    icon.alpha_composite(foreground(source, size))
    if circular:
        mask = Image.new("L", (size * 4, size * 4), 0)
        from PIL import ImageDraw

        ImageDraw.Draw(mask).ellipse((0, 0, size * 4, size * 4), fill=255)
        icon.putalpha(mask.resize((size, size), Image.LANCZOS))
    return icon


def main() -> None:
    source = Image.open(SRC).convert("RGBA")

    for density, scale in DENSITIES.items():
        folder = os.path.join(RES, f"mipmap-{density}")
        os.makedirs(folder, exist_ok=True)

        size = round(ADAPTIVE_DP * scale)
        foreground(source, size).save(os.path.join(folder, "ic_launcher_foreground.png"))
        background(source, size).convert("RGB").save(
            os.path.join(folder, "ic_launcher_background.png")
        )
        monochrome(source, size).save(os.path.join(folder, "ic_launcher_monochrome.png"))

        legacy_size = round(LEGACY_DP * scale)
        legacy(source, legacy_size, False).convert("RGB").save(
            os.path.join(folder, "ic_launcher.png")
        )
        legacy(source, legacy_size, True).save(os.path.join(folder, "ic_launcher_round.png"))
        print(f"mipmap-{density}: adaptive {size}px, legacy {legacy_size}px")

    anydpi = os.path.join(RES, "mipmap-anydpi-v26")
    os.makedirs(anydpi, exist_ok=True)
    for name in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi, name), "w", encoding="utf-8") as fh:
            fh.write(FOREGROUND_XML)

    # Play Store listing icon.
    os.makedirs(REVIEW, exist_ok=True)
    store = legacy(source, 512, False).convert("RGB")
    store.save(os.path.join(REVIEW, "play_store_icon.png"))

    preview(source)
    print("Adaptive icon written to app/src/main/res/mipmap-*")


def preview(source: Image.Image) -> None:
    """Render circle / squircle / square masks side by side for review."""
    from PIL import ImageDraw

    size = 288
    composed = background(source, size)
    composed.alpha_composite(foreground(source, size))

    shapes = {}
    circle = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(circle).ellipse((0, 0, size * 4, size * 4), fill=255)
    shapes["circle"] = circle.resize((size, size), Image.LANCZOS)

    squircle = Image.new("L", (size * 4, size * 4), 0)
    ImageDraw.Draw(squircle).rounded_rectangle(
        (0, 0, size * 4, size * 4), radius=size * 4 * 0.30, fill=255
    )
    shapes["squircle"] = squircle.resize((size, size), Image.LANCZOS)
    shapes["square"] = Image.new("L", (size, size), 255)

    themed = Image.new("RGBA", (size, size), (70, 74, 88, 255))
    themed.alpha_composite(monochrome(source, size))
    themed.putalpha(shapes["circle"])

    gap = 24
    cells = list(shapes.items()) + [("monochrome (themed)", None)]
    sheet = Image.new("RGB", (size * 4 + gap * 5, size + gap * 2 + 22), (28, 32, 54))
    draw = ImageDraw.Draw(sheet)
    for i, (name, mask) in enumerate(cells):
        if mask is None:
            cell = themed
        else:
            cell = composed.copy()
            cell.putalpha(mask)
        sheet.paste(cell, (gap + i * (size + gap), gap), cell)
        draw.text((gap + i * (size + gap), gap + size + 4), name, fill=(210, 220, 255))
    sheet.save(os.path.join(REVIEW, "icon_masks.png"))


if __name__ == "__main__":
    main()
