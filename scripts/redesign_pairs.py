"""Builds docs/redesign/after/*.jpg (540px wide) from raw emulator PNGs, and docs/redesign/pairs/*.jpg with the
"before" (v1.3.1) on the left and the redesign on the right, under the same file names.

Run: python scripts/redesign_pairs.py <folder of raw PNG screenshots>
"""
import os
import sys

from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "docs", "redesign")
W, H, GAP, HEAD = 540, 1170, 20, 40


def main(raw):
    after = os.path.join(ROOT, "after")
    pairs = os.path.join(ROOT, "pairs")
    before = os.path.join(ROOT, "before")
    os.makedirs(after, exist_ok=True)
    os.makedirs(pairs, exist_ok=True)
    for f in sorted(os.listdir(raw)):
        if not f.endswith(".png"):
            continue
        im = Image.open(os.path.join(raw, f)).convert("RGB")
        im.resize((W, round(im.height * W / im.width)), Image.LANCZOS).save(os.path.join(after, f[:-4] + ".jpg"), quality=86)
    made = 0
    for f in sorted(os.listdir(after)):
        b = os.path.join(before, f)
        if not os.path.exists(b):
            continue
        sheet = Image.new("RGB", (W * 2 + GAP, H + HEAD), "white")
        d = ImageDraw.Draw(sheet)
        d.text((10, 12), f"BEFORE (v1.3.1)  {f[:-4]}", fill="black")
        d.text((W + GAP + 10, 12), "AFTER (redesign)", fill="black")
        sheet.paste(Image.open(b).convert("RGB").resize((W, H)), (0, HEAD))
        sheet.paste(Image.open(os.path.join(after, f)).convert("RGB").resize((W, H)), (W + GAP, HEAD))
        sheet.save(os.path.join(pairs, f), quality=86)
        made += 1
    print(f"{made} pairs")


if __name__ == "__main__":
    main(sys.argv[1])
