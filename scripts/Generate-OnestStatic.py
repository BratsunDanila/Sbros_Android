"""Bake Onest wght instances for consistent rendering across Android font loaders.

Source: Google Fonts Onest, retained under tools/fonts. SIL OFL in licenses/.
Requires fonttools==4.62.1; this is a development tool, not an APK dependency.
"""
from pathlib import Path
import hashlib
import json

from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "tools/fonts/Onest-variable.ttf"
OUTPUT = ROOT / "app/src/main/res/font"
WEIGHTS = {400: "Regular", 500: "Medium", 600: "SemiBold", 650: "SemiBold650",
           700: "Bold", 750: "Bold750", 760: "Heading760", 800: "ExtraBold"}


def main():
    OUTPUT.mkdir(parents=True, exist_ok=True)
    outlines = set()
    manifest = {"source_sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(), "instances": []}
    for weight, style in WEIGHTS.items():
        with TTFont(SOURCE, recalcTimestamp=False) as variable:
            font = instantiateVariableFont(variable, {"wght": weight}, inplace=False)
        font.recalcTimestamp = False
        assert "fvar" not in font and "gvar" not in font
        font["OS/2"].usWeightClass = weight
        # Give each resource a unique face name too, avoiding OEM name-based cache reuse.
        names = {1: "Onest", 2: style, 4: f"Onest {style}", 6: f"Onest-{style}",
                 16: "Onest", 17: style}
        font["name"].names = [n for n in font["name"].names if n.nameID not in names]
        for name_id, value in names.items():
            font["name"].setName(value, name_id, 3, 1, 0x409)
            font["name"].setName(value, name_id, 1, 0, 0)
        font["OS/2"].fsSelection &= ~((1 << 5) | (1 << 6))
        if weight == 400:
            font["OS/2"].fsSelection |= 1 << 6
        elif weight >= 700:
            font["OS/2"].fsSelection |= 1 << 5
        font["head"].macStyle = (font["head"].macStyle & ~1) | (1 if weight >= 700 else 0)
        destination = OUTPUT / f"onest_{weight}.ttf"
        font.save(destination)
        with TTFont(destination) as check:
            assert check["OS/2"].usWeightClass == weight and "fvar" not in check
            assert all(ord(ch) in check.getBestCmap() for ch in "ДелаМыслиСбросКалендарьОбзор")
            glyph = check["glyf"][check.getBestCmap()[ord("Д")]]
            coordinates, _, _ = glyph.getCoordinates(check["glyf"])
            outline_hash = hashlib.sha256(str(list(coordinates)).encode()).hexdigest()
            assert outline_hash not in outlines, f"Duplicate outline for {weight}"
            outlines.add(outline_hash)
        manifest["instances"].append({"file": destination.name, "weight": weight,
            "sha256": hashlib.sha256(destination.read_bytes()).hexdigest()})
        print(f"{destination.name}: static, weight={weight}, Cyrillic checked")
    (SOURCE.parent / "instances.json").write_text(json.dumps(manifest, indent=2)+"\n", encoding="utf-8")


if __name__ == "__main__":
    main()
