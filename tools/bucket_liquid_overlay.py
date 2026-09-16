#!/usr/bin/env python3
"""Regenerates assets/casualtiesbelow/textures/item/bucket_liquid.png.

The overlay is the exact liquid silhouette of the vanilla water bucket: every pixel where
water_bucket.png differs from bucket.png becomes a white opaque pixel, everything else transparent.
Re-run this when a Minecraft upgrade changes the vanilla bucket sprites, then commit the result:

    python3 tools/bucket_liquid_overlay.py <path-to-minecraft-client.jar>

The jar is the loom-cached client jar, e.g.:
    ~/.gradle/caches/fabric-loom/<version>/minecraft-client.jar
"""

import sys
import zipfile

from PIL import Image

OVERLAY_SIZE = (16, 16)
ASSET_BASE = "assets/minecraft/textures/item/"
OUTPUT = "src/main/resources/assets/casualtiesbelow/textures/item/bucket_liquid.png"


def main() -> None:
    jar_path = sys.argv[1] if len(sys.argv) > 1 else input("minecraft client jar path: ").strip()
    with zipfile.ZipFile(jar_path) as jar:
        base = Image.open(jar.open(ASSET_BASE + "bucket.png")).convert("RGBA")
        water = Image.open(jar.open(ASSET_BASE + "water_bucket.png")).convert("RGBA")

    if base.size != OVERLAY_SIZE or water.size != OVERLAY_SIZE:
        raise SystemExit(f"unexpected sprite size {base.size}/{water.size}")

    overlay = Image.new("RGBA", OVERLAY_SIZE, (0, 0, 0, 0))
    changed = 0
    for y in range(OVERLAY_SIZE[1]):
        for x in range(OVERLAY_SIZE[0]):
            if base.getpixel((x, y)) != water.getpixel((x, y)) and water.getpixel((x, y))[3] > 10:
                overlay.putpixel((x, y), (255, 255, 255, 255))
                changed += 1

    overlay.save(OUTPUT)
    print(f"wrote {OUTPUT} ({changed} liquid pixels)")


if __name__ == "__main__":
    main()
