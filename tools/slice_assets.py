"""Asset slicing pipeline for Olympus Surge.

Source art ships as 1584x672 RGBA WebP with a ready alpha channel. Files named
"*_Set_*" hold several objects in a grid. This script cuts sets into individual
sprites, trims transparent padding, and writes metadata (size + anchor) that the
renderer uses to place sprites in the isometric world.

Full-screen backgrounds and loading screens are RGB and are left untouched.
"""

from __future__ import annotations

import json
import os
from dataclasses import dataclass, asdict

import numpy as np
from PIL import Image

SRC = r"D:\flutter_proj\Olympus_Surge\assets"
OUT = r"D:\flutter_proj\Olympus_Surge\art\sprites"

ALPHA_TRIM = 8
# A gap must span this fraction of the axis to count as a cut line.
MIN_GAP_FRAC = 0.015
MIN_PIECE_FRAC = 0.05
# Blobs below this fraction of the frame are stray specks, not objects.
MIN_BLOB_FRAC = 0.0004

SKIP = {"example_gameplay_1", "Horizontal_Loading_Screen", "Vertical_Loading_Screen"}
# Every "*_Set_*" source in this pack holds exactly four objects.
SET_PIECES = 4

CATEGORY = {
    "heroes": ["Chosen_Olympus_Warrior", "Zeus_God", "Athena_Goddess", "Ares_God", "Hermes_God"],
    "enemies": ["Mythical_Warriors_Set", "Flying_Enemies_Set"],
    "elites": ["Mythical_Creatures_Set"],
    "gems": ["Olympus_Gemstones_Set", "Olympus_Energy_Stone"],
    "vfx": [
        "Zeus_Lightning_Effect",
        "Ares_Fire_Strike_Effect",
        "Athena_Shield_Effect",
        "Hermes_Wind_Vortex_Effect",
    ],
    "arena": ["Celestial_Olympus_Arena", "Floating_Cloud_Platforms_Set"],
    "backgrounds": [
        "Olympus_Clouds_Background",
        "Celestial_Temple_Background",
        "Ancient_Ruins_Background",
    ],
    "ui": ["Game_Name"],
}
# Characters and props pivot at their feet so depth sorting matches the floor.
FEET_ANCHORED = {"heroes", "enemies", "elites", "props"}

# Readable names in reading order, replacing the generic _1.._4 suffixes.
PIECE_NAMES = {
    "Mythical_Creatures_Set": ["minotaur", "harpy", "cerberus", "medusa"],
    "Olympus_Gemstones_Set": ["sapphire_zeus", "ruby_ares", "emerald_athena", "amethyst_hermes"],
    "Flying_Enemies_Set": ["flame_wraith", "frost_wraith", "shadow_wraith", "winged_demon"],
    "Mythical_Warriors_Set": [
        "bronze_hoplite",
        "stone_guardian",
        "shadow_hoplite",
        "spectral_champion",
    ],
    "God_Statues_Set": ["statue_zeus", "statue_athena", "statue_ares", "statue_hermes"],
    "Decorative_Stones_Set": [
        "stone_sapphire",
        "stone_column",
        "stone_amethyst",
        "stone_emerald",
    ],
    "Golden_Artifacts_Set": ["artifact_amphora", "artifact_helmet", "artifact_chalice",
                             "artifact_relief"],
    "Olympus_Plants_Set": ["plant_tree", "plant_flowers", "plant_bush", "plant_sprout"],
    "Marble_Columns_Set": ["column_tall", "column_ornate", "column_slim", "column_broken"],
    "Floating_Cloud_Platforms_Set": [
        "cloud_platform_large",
        "cloud_platform_gate",
        "cloud_platform_wide",
        "cloud_platform_shrine",
    ],
}


@dataclass
class Placed:
    """A candidate sprite together with its offset inside the source frame."""

    image: Image.Image
    x: int
    y: int


@dataclass
class SpriteMeta:
    name: str
    category: str
    source: str
    file: str
    width: int
    height: int
    anchor_x: float
    anchor_y: float


def category_of(stem: str) -> str:
    for cat, names in CATEGORY.items():
        if stem in names:
            return cat
    return "props"


def find_runs(mask: np.ndarray, axis: int) -> list[tuple[int, int]]:
    """Split an occupancy mask into runs separated by sufficiently wide gaps."""
    occupied = mask.any(axis=axis)
    span = occupied.size
    min_gap = max(4, int(span * MIN_GAP_FRAC))

    runs: list[tuple[int, int]] = []
    start = None
    gap = 0
    for i, filled in enumerate(occupied):
        if filled:
            if start is None:
                start = i
            gap = 0
        elif start is not None:
            gap += 1
            if gap >= min_gap:
                runs.append((start, i - gap + 1))
                start = None
    if start is not None:
        runs.append((start, span))

    return [r for r in runs if (r[1] - r[0]) >= span * MIN_PIECE_FRAC]


def split_grid(img: Image.Image, ox: int = 0, oy: int = 0) -> list[Placed]:
    """Recursively cut along fully empty columns, then fully empty rows."""
    mask = np.asarray(img)[..., 3] > ALPHA_TRIM
    if not mask.any():
        return []

    for axis in (0, 1):
        runs = find_runs(mask, axis=axis)
        if len(runs) > 1:
            pieces: list[Placed] = []
            for a, b in runs:
                if axis == 0:
                    pieces += split_grid(img.crop((a, 0, b, img.height)), ox + a, oy)
                else:
                    pieces += split_grid(img.crop((0, a, img.width, b)), ox, oy + a)
            return pieces

    return [Placed(img, ox, oy)]


def best_valley(img: Image.Image) -> tuple[str, int, float] | None:
    """Find the axis and position where the sprite is thinnest.

    Objects in a set often touch through soft glows and shadows, so no column is
    fully empty. The lowest point of the smoothed alpha profile is where two
    neighbours meet.
    """
    alpha = np.asarray(img)[..., 3].astype(np.float64)
    best = None
    for axis in (0, 1):
        profile = alpha.sum(axis=axis)
        window = max(3, int(profile.size * 0.01) | 1)
        profile = np.convolve(profile, np.ones(window) / window, mode="same")

        lo, hi = int(profile.size * 0.20), int(profile.size * 0.80)
        if hi - lo < 8:
            continue
        pos = lo + int(np.argmin(profile[lo:hi]))
        reference = np.median(profile[profile > 0])
        if reference <= 0:
            continue

        depth = 1.0 - profile[pos] / reference
        # A valid seam separates two comparable objects. Weighting by how evenly
        # the mass splits stops the cut from shaving a thin sliver off an edge.
        total = profile.sum()
        balance = min(profile[:pos].sum(), profile[pos:].sum()) / total if total else 0.0
        score = depth * min(balance / 0.30, 1.0)

        if best is None or score > best[2]:
            best = ("x" if axis == 0 else "y", pos, score)
    return best


def split_into(img: Image.Image, expected: int) -> list[Placed]:
    """Cut an image into exactly `expected` pieces, deepest valley first."""
    pieces = split_grid(img)
    while len(pieces) < expected:
        target = max(range(len(pieces)), key=lambda i: opaque_area(pieces[i].image))
        piece = pieces[target]
        cut = best_valley(piece.image)
        if cut is None:
            break
        axis, pos, _ = cut
        w, h = piece.image.width, piece.image.height
        if axis == "x":
            halves = [
                Placed(piece.image.crop((0, 0, pos, h)), piece.x, piece.y),
                Placed(piece.image.crop((pos, 0, w, h)), piece.x + pos, piece.y),
            ]
        else:
            halves = [
                Placed(piece.image.crop((0, 0, w, pos)), piece.x, piece.y),
                Placed(piece.image.crop((0, pos, w, h)), piece.x, piece.y + pos),
            ]
        pieces[target:target + 1] = halves
    return pieces


def opaque_area(img: Image.Image) -> int:
    return int((np.asarray(img)[..., 3] > 40).sum())


def reading_order(pieces: list[Placed]) -> list[Placed]:
    """Sort top-to-bottom, left-to-right, tolerating uneven baselines."""
    if not pieces:
        return pieces
    tol = max(max(p.image.height for p in pieces) * 0.6, 1.0)
    return sorted(pieces, key=lambda p: (round(p.y / tol), p.x))


def drop_specks(img: Image.Image, from_set: bool = False) -> Image.Image:
    """Erase compression specks and, for set pieces, leftovers of neighbours.

    A cut line can clip a bit of the adjacent object into the frame. Such a
    fragment is small and always touches the border it was cut along, which
    distinguishes it from a legitimate detached detail.
    """
    from scipy import ndimage

    arr = np.array(img)
    solid = arr[..., 3] > 40
    labels, count = ndimage.label(solid)
    if count <= 1:
        return img

    sizes = ndimage.sum(solid, labels, range(1, count + 1))
    largest = sizes.max()
    keep = sizes >= max(largest * 0.02, arr[..., 3].size * MIN_BLOB_FRAC)

    if from_set:
        boxes = ndimage.find_objects(labels)
        h, w = solid.shape
        for i, box in enumerate(boxes):
            if not keep[i] or sizes[i] >= largest * 0.25:
                continue
            ys, xs = box
            if ys.start == 0 or xs.start == 0 or ys.stop == h or xs.stop == w:
                keep[i] = False

    kept = np.concatenate([[False], keep])[labels]
    # Keep anti-aliased fringes attached to surviving blobs.
    kept = ndimage.binary_dilation(kept, iterations=2)
    arr[..., 3] = np.where(kept, arr[..., 3], 0)
    return Image.fromarray(arr, "RGBA")


def trim(img: Image.Image) -> Image.Image | None:
    mask = np.asarray(img)[..., 3] > ALPHA_TRIM
    if not mask.any():
        return None
    ys, xs = np.where(mask)
    return img.crop((int(xs.min()), int(ys.min()), int(xs.max()) + 1, int(ys.max()) + 1))


def process(path: str) -> list[SpriteMeta]:
    stem = os.path.splitext(os.path.basename(path))[0]
    base = stem.replace("_asset", "")
    category = category_of(base)

    img = Image.open(path)
    if img.mode != "RGBA":
        # Backgrounds and loading art are opaque full-frame images: copy as-is.
        img = img.convert("RGB")
        folder = os.path.join(OUT, category)
        os.makedirs(folder, exist_ok=True)
        file_name = f"{base.lower()}.png"
        img.save(os.path.join(folder, file_name), optimize=True)
        return [
            SpriteMeta(base.lower(), category, stem, f"{category}/{file_name}",
                       img.width, img.height, 0.5, 0.5)
        ]

    img = img.convert("RGBA")
    is_set = "_Set" in base
    parts = reading_order(split_into(img, SET_PIECES)) if is_set else [Placed(img, 0, 0)]

    placed = []
    for part in parts:
        cleaned = trim(drop_specks(part.image, from_set=is_set))
        if cleaned is not None:
            placed.append(cleaned)

    folder = os.path.join(OUT, category)
    os.makedirs(folder, exist_ok=True)

    labels = PIECE_NAMES.get(base)
    root = base.replace("_Set", "").lower()
    anchor_y = 1.0 if category in FEET_ANCHORED else 0.5

    metas = []
    for index, sprite in enumerate(placed):
        if labels and len(placed) == len(labels):
            name = labels[index]
        elif len(placed) > 1:
            name = f"{root}_{index + 1}"
        else:
            name = root
        file_name = f"{name}.png"
        sprite.save(os.path.join(folder, file_name), optimize=True)
        metas.append(
            SpriteMeta(name, category, stem, f"{category}/{file_name}",
                       sprite.width, sprite.height, 0.5, anchor_y)
        )
    return metas


def contact_sheet(metas: list[SpriteMeta]) -> None:
    """Render one review image per category so slicing can be eyeballed."""
    review = os.path.join(os.path.dirname(OUT), "review")
    os.makedirs(review, exist_ok=True)

    by_cat: dict[str, list[SpriteMeta]] = {}
    for m in metas:
        by_cat.setdefault(m.category, []).append(m)

    from PIL import ImageDraw

    cell, label_h = 260, 22
    for cat, items in by_cat.items():
        cols = min(4, len(items))
        rows = (len(items) + cols - 1) // cols
        sheet = Image.new("RGBA", (cols * cell, rows * (cell + label_h)), (28, 32, 54, 255))
        draw = ImageDraw.Draw(sheet)
        for i, m in enumerate(items):
            sprite = Image.open(os.path.join(OUT, m.file.replace("/", os.sep))).convert("RGBA")
            sprite.thumbnail((cell - 16, cell - 16))
            col, row = i % cols, i // cols
            x = col * cell + (cell - sprite.width) // 2
            y = row * (cell + label_h) + (cell - sprite.height) // 2
            sheet.alpha_composite(sprite, (x, y))
            draw.text(
                (col * cell + 6, row * (cell + label_h) + cell + 4),
                f"{m.name}  {m.width}x{m.height}",
                fill=(210, 220, 255, 255),
            )
        sheet.convert("RGB").save(os.path.join(review, f"{cat}.png"))


def main() -> None:
    os.makedirs(OUT, exist_ok=True)
    all_meta: list[SpriteMeta] = []

    files = []
    for root, _, names in os.walk(SRC):
        for n in sorted(names):
            if n.lower().endswith(".webp") and os.path.splitext(n)[0] not in SKIP:
                files.append(os.path.join(root, n))

    for path in sorted(files):
        metas = process(path)
        all_meta.extend(metas)
        sizes = ", ".join(f"{m.name} {m.width}x{m.height}" for m in metas)
        print(f"{os.path.basename(path):<44} -> {len(metas):>2}  {sizes}")

    with open(os.path.join(OUT, "sprites.json"), "w", encoding="utf-8") as fh:
        json.dump([asdict(m) for m in all_meta], fh, indent=2)

    contact_sheet(all_meta)
    print(f"\nTotal: {len(all_meta)} sprites")


if __name__ == "__main__":
    main()
