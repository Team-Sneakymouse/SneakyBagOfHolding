#!/usr/bin/env python3
"""
Bootstrap SneakyBagOfHolding config.yml items from MagicSpells spells-system-bagofholding.yml.

Usage:
  python bootstrap_config_from_spells.py \\
    --spells-yml /path/to/spells-system-bagofholding.yml \\
    --output /path/to/config-items-snippet.yml \\
    [--menu-mapping menu_mapping.json]

menu_mapping.json example:
{
  "system-bagofholding-menuFishing": "fish",
  "system-bagofholding-menuMining": "ore",
  "system-bagofholding-menuMagic": "crafting_reagent",
  "system-bagofholding-menuFood": "food",
  "system-bagofholding-menuGathering": "gathering",
  "system-bagofholding-menuOther": "other"
}
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

try:
    import yaml
except ImportError:
    print("PyYAML required: pip install pyyaml", file=sys.stderr)
    sys.exit(1)


def extract_items_from_variables(section: dict) -> set[str]:
    ids: set[str] = set()
    for key in section:
        if key.startswith("bank_"):
            # bank_itembanana -> cannot recover dashes reliably
            pass
    return ids


def extract_from_autoloot(spells: dict) -> set[str]:
    ids: set[str] = set()
    spell = spells.get("system-bagofholding-autolootPickup") or {}
    options = spell.get("options") or {}
    for opt in options.values():
        item = opt.get("item")
        if item:
            ids.add(item)
    return ids


def extract_from_menu_args(content: str) -> set[str]:
    ids: set[str] = set()
    for match in re.finditer(r'args=\["([^"]+)"\]', content):
        ids.add(match.group(1))
    return ids


def extract_from_deposit_maps(spells: dict) -> set[str]:
    ids: set[str] = set()
    main = spells.get("system-bagofholding-menuMain") or {}
    drop = main.get("spells-on-drop") or {}
    ids.update(drop.keys())
    for name, spell in spells.items():
        if "menu" in name.lower() and isinstance(spell, dict):
            drop = spell.get("spells-on-drop")
            if isinstance(drop, dict):
                ids.update(drop.keys())
    return ids


def load_menu_mapping(path: Path | None) -> dict[str, list[str]]:
    """Spell menu name -> category ids (manual)."""
    default = {
        "system-bagofholding-menuFishing": ["fish"],
        "system-bagofholding-menuMining": ["ore"],
        "system-bagofholding-menuMagic": ["crafting_reagent"],
        "system-bagofholding-menuFood": ["food"],
        "system-bagofholding-menuGathering": ["gathering"],
        "system-bagofholding-menuOther": ["other"],
    }
    if path is None:
        return default
    with path.open(encoding="utf-8") as f:
        raw = json.load(f)
    return {k: v if isinstance(v, list) else [v] for k, v in raw.items()}


def infer_categories(item_id: str, menu_mapping: dict[str, list[str]], spells: dict) -> list[str]:
    cats: set[str] = set()
    if item_id.startswith("fish-"):
        cats.add("fish")
    if item_id.startswith("item-ore") or "ore" in item_id.lower():
        cats.add("ore")
    for menu_name, categories in menu_mapping.items():
        menu = spells.get(menu_name) or {}
        drop = menu.get("spells-on-drop") or {}
        options = menu.get("options") or {}
        in_menu = item_id in drop or any(
            item_id in str(v) for v in options.values()
        )
        if in_menu:
            cats.update(categories)
    if not cats:
        cats.add("other")
    return sorted(cats)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spells-yml", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--menu-mapping", type=Path, default=None)
    args = parser.parse_args()

    text = args.spells_yml.read_text(encoding="utf-8")
    data = yaml.safe_load(text) or {}
    spells = data if "system-bagofholding-menuMain" in data else data.get("spells", data)

    item_ids: set[str] = set()
    item_ids.update(extract_from_autoloot(spells))
    item_ids.update(extract_from_deposit_maps(spells))
    item_ids.update(extract_from_menu_args(text))

    menu_mapping = load_menu_mapping(args.menu_mapping)

    items_block: dict = {}
    for item_id in sorted(item_ids):
        items_block[item_id] = {
            "categories": infer_categories(item_id, menu_mapping, spells),
            "default-capacity": 1000,
        }

    snippet = {"items": items_block}
    args.output.write_text(
        yaml.dump(snippet, default_flow_style=False, sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )
    print(f"Wrote {len(items_block)} items to {args.output}")
    print("Merge the 'items' section into config.yml and define matching 'categories'.")


if __name__ == "__main__":
    main()
