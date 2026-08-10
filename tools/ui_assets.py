"""Stage the sprites the Compose UI needs as drawables.

The battle scene streams its art from `assets/` through libGDX, but the menus
are ordinary Android views, so the handful of images they use is copied into
`res/drawable-nodpi` at the size the UI actually draws them.
"""

from __future__ import annotations

import os

from PIL import Image

SRC = r"D:\flutter_proj\Olympus_Surge\art\sprites"
DST = r"D:\flutter_proj\Olympus_Surge\app\src\main\res\drawable-nodpi"

# (source file, drawable name, target height)
ITEMS = [
    # Avatars the player can pick for their profile.
    ("heroes/zeus_god.png", "avatar_zeus", 320),
    ("heroes/athena_goddess.png", "avatar_athena", 320),
    ("heroes/ares_god.png", "avatar_ares", 320),
    ("heroes/hermes_god.png", "avatar_hermes", 320),
    ("heroes/chosen_olympus_warrior.png", "avatar_warrior", 320),

    # Temple upgrade icons.
    ("props/statue_athena.png", "icon_vitality", 260),
    ("props/statue_ares.png", "icon_wrath", 260),
    ("props/statue_hermes.png", "icon_swiftness", 260),
    ("props/statue_zeus.png", "icon_storm", 260),
    ("props/temple_treasure_chest.png", "icon_fortune", 260),
    ("props/gemstone_altar.png", "icon_magnet", 260),
    ("props/artifact_helmet.png", "icon_resilience", 260),
    ("props/divine_crystal_altar.png", "icon_starting_gem", 260),

    # Gem chips shown in the profile and the results screen.
    ("gems/sapphire_zeus.png", "gem_sapphire", 140),
    ("gems/ruby_ares.png", "gem_ruby", 140),
    ("gems/emerald_athena.png", "gem_emerald", 140),
    ("gems/amethyst_hermes.png", "gem_amethyst", 140),
    ("gems/olympus_energy_stone.png", "icon_essence", 140),
]

# Backgrounds get their own pass: opaque JPEGs, sized for a 1080p screen.
BACKGROUNDS = [
    ("backgrounds/celestial_temple_background.png", "bg_temple", (1920, 816)),
    ("backgrounds/ancient_ruins_background.png", "bg_ruins", (1920, 816)),
    # The menu needs artwork without the logo baked in, since it draws its own.
    ("backgrounds/olympus_clouds_background.png", "bg_clouds", (1920, 816)),
]


def main() -> None:
    os.makedirs(DST, exist_ok=True)
    total = 0

    for source, name, height in ITEMS:
        img = Image.open(os.path.join(SRC, source.replace("/", os.sep)))
        if img.height > height:
            width = max(1, round(img.width * height / img.height))
            img = img.resize((width, height), Image.LANCZOS)
        path = os.path.join(DST, f"{name}.png")
        img.save(path, optimize=True)
        total += os.path.getsize(path)

    for source, name, size in BACKGROUNDS:
        img = Image.open(os.path.join(SRC, source.replace("/", os.sep))).convert("RGB")
        img = img.resize(size, Image.LANCZOS)
        path = os.path.join(DST, f"{name}.jpg")
        img.save(path, quality=86, optimize=True)
        total += os.path.getsize(path)

    print(f"{len(ITEMS) + len(BACKGROUNDS)} drawables, {total / 1024 / 1024:.1f} MB")


if __name__ == "__main__":
    main()
