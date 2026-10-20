"""Every banner a theme names in app_banners is bundled with the launcher.

Run from the repository root: python3 -m unittest discover -s tests -v
"""
import json
from pathlib import Path
import unittest


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "launcher" / "app" / "src" / "main" / "res"


def bundled_banners():
    return {f.stem for d in RES.glob("drawable*") for f in d.iterdir() if f.stem.startswith("banner_")}


class ThemeBannersTest(unittest.TestCase):
    def test_theme_banners_exist(self):
        bundled = bundled_banners()
        for config in sorted((ROOT / "themes").glob("*/config.json")):
            entries = json.loads(config.read_text(encoding="utf-8")).get("app_banners", [])
            for entry in entries:
                with self.subTest(theme=config.parent.name, entry=entry):
                    pkg, sep, name = entry.partition("=")
                    self.assertTrue(sep and pkg.strip(), "not package=banner_name")
                    self.assertTrue(name.startswith("banner_"), "the launcher only loads banner_* drawables")
                    self.assertIn(name, bundled, "no such drawable in res/drawable*")


if __name__ == "__main__":
    unittest.main()
