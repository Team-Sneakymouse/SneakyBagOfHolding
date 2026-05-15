#!/usr/bin/env python3
"""
Generate SneakyBagOfHolding categories with menu-icon ItemStacks from legacy MagicSpells data.

Reads menu titles from spells-system-bagofholding.yml, picks a representative magic item
per category (from menu deposit lists or known defaults), and copies material / custom-model-data
from spells-items/*.yml (and optional magic-items in the bagofholding file).

Usage:
  python generate_categories.py \\
    --spells-yml /path/to/spells-system-bagofholding.yml \\
    --spells-items-dir /path/to/dev/spells-items \\
    --output /path/to/categories-snippet.yml

  # Merge into existing config (replaces categories: block only):
  python generate_categories.py ... --merge-into /path/to/config.yml

Requires: pip install pyyaml
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path
from typing import Any

try:
    import yaml
except ImportError:
    print("PyYAML required: pip install pyyaml", file=sys.stderr)
    sys.exit(1)


# Legacy MenuSpell name -> SneakyBagOfHolding category id
MENU_TO_CATEGORY: dict[str, str] = {
    "system-bagofholding-menuFood": "food",
    "system-bagofholding-menuMagic": "crafting_reagent",
    "system-bagofholding-menuGathering": "gathering",
    "system-bagofholding-menuMining": "ore",
    "system-bagofholding-menuFishing": "fish",
    "system-bagofholding-menuOther": "other",
}

# Preferred hub icons per category (first match in menu or magic-items index wins)
PREFERRED_ICON_ITEMS: dict[str, list[str]] = {
    "food": ["item-banana", "item-goatmilk", "item-cheese", "item-honey"],
    "crafting_reagent": [
        "item-moonstone",
        "item-soulstone",
        "item-firePetal",
        "item-arcanePetal",
        "item-manaBloom",
    ],
    "gathering": ["item-honeycomb", "item-honey", "item-acorn", "item-seeds", "item-mintleaf"],
    "ore": ["item-ore", "item-muk", "item-goldOre", "item-stone", "item-bone"],
    "fish": ["fish-brickjaw1", "fish-ancientkoi1", "fish-catfish1"],
    "other": ["item-playingcard", "item-bolt", "item-glue"],
}

# Last resort if nothing resolves
FALLBACK_ICON_ITEM: dict[str, str] = {
    "food": "item-banana",
    "crafting_reagent": "item-moonstone",
    "gathering": "item-honeycomb",
    "ore": "item-muk",
    "fish": "fish-brickjaw1",
    "other": "item-playingcard",
}

# Vanilla fallbacks when magic item definition is missing
VANILLA_FALLBACK_ICONS: dict[str, dict[str, Any]] = {
    "food": {"material": "APPLE", "custom-model-data": 94, "name": "<gold>Food & Drink"},
    "crafting_reagent": {"material": "NETHER_WART", "name": "<gold>Magic Reagents"},
    "gathering": {"material": "HONEYCOMB", "name": "<gold>Gathering Mats"},
    "ore": {"material": "IRON_ORE", "name": "<gold>Digging & Mining"},
    "fish": {"material": "COD", "name": "<gold>Fish and Bait"},
    "other": {"material": "PAPER", "name": "<gold>Other Items"},
}

# Legacy hub GUI items from spells-system-bagofholding.yml magic-items (optional --legacy-hub-style)
LEGACY_HUB_ICONS = {
    "filled": {"material": "JIGSAW", "custom-model-data": 3076},
    "empty": {"material": "RABBIT_FOOT", "custom-model-data": 7},
}


def legacy_colors_to_minimessage(text: str) -> str:
    """Convert common MagicSpells & codes to MiniMessage (plugin accepts both)."""
    if not text:
        return text
    replacements = [
        ("&0", "<black>"),
        ("&1", "<dark_blue>"),
        ("&2", "<dark_green>"),
        ("&3", "<dark_aqua>"),
        ("&4", "<dark_red>"),
        ("&5", "<dark_purple>"),
        ("&6", "<gold>"),
        ("&7", "<gray>"),
        ("&8", "<dark_gray>"),
        ("&9", "<blue>"),
        ("&a", "<green>"),
        ("&b", "<aqua>"),
        ("&c", "<red>"),
        ("&d", "<light_purple>"),
        ("&e", "<yellow>"),
        ("&f", "<white>"),
        ("&l", "<bold>"),
        ("&o", "<italic>"),
        ("&r", "<reset>"),
    ]
    out = text.strip()
    for old, new in replacements:
        out = out.replace(old, new)
    # Strip trailing reset if entire string was one color prefix
    if out.startswith("<gold>") and not out.endswith("</gold>"):
        pass
    return out


def load_yaml(path: Path) -> dict:
    if not path.exists():
        return {}
    with path.open(encoding="utf-8") as f:
        return yaml.safe_load(f) or {}


def load_magic_items_from_dir(directory: Path, combined: dict[str, dict]) -> None:
    if not directory.is_dir():
        return
    for path in sorted(directory.glob("*.yml")):
        data = load_yaml(path)
        section = data.get("magic-items") or {}
        if isinstance(section, dict):
            combined.update(section)


def load_all_magic_items(
    spells_items_dir: Path | None,
    spells_yml: Path,
    extra_dirs: list[Path] | None = None,
) -> dict[str, dict]:
    """Index magic-items by internal name from spells-items/, extra dirs, and bagofholding file."""
    combined: dict[str, dict] = {}
    bag = load_yaml(spells_yml)
    mi = bag.get("magic-items") or {}
    if isinstance(mi, dict):
        combined.update(mi)
    if spells_items_dir:
        load_magic_items_from_dir(spells_items_dir, combined)
        # Common layout: dev/spells-items + dev/spells-city
        sibling_city = spells_items_dir.parent / "spells-city"
        load_magic_items_from_dir(sibling_city, combined)
    for directory in extra_dirs or []:
        load_magic_items_from_dir(directory, combined)
    return combined


def extract_spells_root(data: dict) -> dict:
    if "system-bagofholding-menuMain" in data:
        return data
    return data.get("spells") or data


def menu_title(spell: dict) -> str:
    raw = (spell.get("title") or "").strip()
    if raw:
        return legacy_colors_to_minimessage(raw)
    return "<gold>Category"


def items_in_menu(spell: dict, menu_text: str, menu_name: str) -> set[str]:
    """All magic item ids referenced by a menu spell."""
    found: set[str] = set()
    drop = spell.get("spells-on-drop") or {}
    if isinstance(drop, dict):
        found.update(drop.keys())
    options = spell.get("options") or {}
    for opt in options.values():
        if not isinstance(opt, dict):
            continue
        for key in ("spell", "spell-right", "spell-sneak-right", "spell-swap"):
            val = opt.get(key) or ""
            for m in re.finditer(r'args=\["([^"]+)"\]', str(val)):
                found.add(m.group(1))
    pattern = re.compile(
        rf"^{re.escape(menu_name)}:.*?(?=^[a-zA-Z0-9_-]+:|\\Z)",
        re.MULTILINE | re.DOTALL,
    )
    block = pattern.search(menu_text)
    if block:
        for m in re.finditer(r'args=\["([^"]+)"\]', block.group(0)):
            found.add(m.group(1))
    return {i for i in found if i.startswith("item-") or i.startswith("fish-")}


def pick_icon_item(
    category_id: str,
    spell: dict,
    menu_text: str,
    menu_name: str,
    magic_items: dict[str, dict],
) -> str:
    in_menu = items_in_menu(spell, menu_text, menu_name)
    for candidate in PREFERRED_ICON_ITEMS.get(category_id, []):
        if candidate in in_menu or candidate in magic_items:
            return candidate
    if in_menu:
        return sorted(in_menu)[0]
    return FALLBACK_ICON_ITEM[category_id]


def magic_item_to_menu_icon(
    magic_item: dict,
    menu_title_text: str,
    *,
    use_item_display_name: bool = False,
) -> dict[str, Any]:
    mat = magic_item.get("type") or magic_item.get("material") or "CHEST"
    icon: dict[str, Any] = {
        "material": str(mat).upper(),
        "name": menu_title_text,
        "lore": ["<gray>Click to browse"],
    }
    if "custom-model-data" in magic_item:
        icon["custom-model-data"] = magic_item["custom-model-data"]
    if use_item_display_name and magic_item.get("name"):
        icon["name"] = legacy_colors_to_minimessage(str(magic_item["name"]))
    return icon


def build_category(
    category_id: str,
    menu_spell_name: str,
    spells: dict,
    menu_text: str,
    magic_items: dict[str, dict],
    *,
    legacy_hub_style: bool = False,
) -> dict[str, Any]:
    spell = spells.get(menu_spell_name) or {}
    title = menu_title(spell)
    rep_id = pick_icon_item(category_id, spell, menu_text, menu_spell_name, magic_items)
    if legacy_hub_style:
        base = LEGACY_HUB_ICONS["filled"].copy()
        icon = {
            **base,
            "name": title,
            "lore": ["<gray>Click to browse"],
        }
    elif rep_id and rep_id in magic_items:
        icon = magic_item_to_menu_icon(magic_items[rep_id], title)
    else:
        icon = VANILLA_FALLBACK_ICONS.get(category_id, {"material": "CHEST", "name": title}).copy()
        if "lore" not in icon:
            icon["lore"] = ["<gray>Click to browse"]
    return {
        "menu-title": title,
        "menu-icon": icon,
        # default-capacity omitted → plugin global settings.defaults.category-capacity (0)
    }


def generate_categories(
    spells_yml: Path,
    spells_items_dir: Path | None,
    *,
    legacy_hub_style: bool = False,
    extra_items_dirs: list[Path] | None = None,
) -> dict[str, dict]:
    text = spells_yml.read_text(encoding="utf-8")
    data = load_yaml(spells_yml)
    spells = extract_spells_root(data)
    magic_items = load_all_magic_items(spells_items_dir, spells_yml, extra_items_dirs)
    categories: dict[str, dict] = {}
    for menu_name, category_id in MENU_TO_CATEGORY.items():
        categories[category_id] = build_category(
            category_id,
            menu_name,
            spells,
            text,
            magic_items,
            legacy_hub_style=legacy_hub_style,
        )
    return categories


def merge_into_config(config_path: Path, categories: dict[str, dict]) -> None:
    config = load_yaml(config_path)
    config["categories"] = categories
    config_path.write_text(
        yaml.dump(config, default_flow_style=False, sort_keys=False, allow_unicode=True),
        encoding="utf-8",
    )


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--spells-yml",
        type=Path,
        default=Path("/mnt/files/Desktop/New folder/dev/spells-systems/spells-system-bagofholding.yml"),
        help="Legacy bagofholding spells YAML",
    )
    parser.add_argument(
        "--spells-items-dir",
        type=Path,
        default=Path("/mnt/files/Desktop/New folder/dev/spells-items"),
        help="Directory of MagicSpells item definition YAML files",
    )
    parser.add_argument(
        "--output",
        type=Path,
        default=Path("categories-snippet.yml"),
        help="Write categories: snippet here",
    )
    parser.add_argument(
        "--merge-into",
        type=Path,
        default=None,
        help="Replace categories: in an existing config.yml",
    )
    parser.add_argument(
        "--extra-items-dir",
        type=Path,
        action="append",
        default=[],
        help="Additional directories to scan for magic-items (repeatable)",
    )
    parser.add_argument(
        "--legacy-hub-style",
        action="store_true",
        help="Use jigsaw/rabbit_foot hub icons from old gui-bagofholding (same look as MS menu)",
    )
    parser.add_argument(
        "--list-icons",
        action="store_true",
        help="Print chosen representative item per category and exit",
    )
    args = parser.parse_args()

    if not args.spells_yml.exists():
        print(f"Spells file not found: {args.spells_yml}", file=sys.stderr)
        sys.exit(1)

    items_dir = args.spells_items_dir if args.spells_items_dir.exists() else None
    categories = generate_categories(
        args.spells_yml,
        items_dir,
        legacy_hub_style=args.legacy_hub_style,
        extra_items_dirs=args.extra_items_dir,
    )

    if args.list_icons:
        spells = extract_spells_root(load_yaml(args.spells_yml))
        text = args.spells_yml.read_text(encoding="utf-8")
        magic_items = load_all_magic_items(
            args.spells_items_dir if args.spells_items_dir.exists() else None,
            args.spells_yml,
            args.extra_items_dir,
        )
        for menu_name, cat_id in MENU_TO_CATEGORY.items():
            spell = spells.get(menu_name) or {}
            rep = pick_icon_item(cat_id, spell, text, menu_name, magic_items)
            found = rep in magic_items if rep else False
            mat = categories[cat_id]["menu-icon"].get("material")
            cmd = categories[cat_id]["menu-icon"].get("custom-model-data", "-")
            print(f"{cat_id}: title={categories[cat_id]['menu-title']!r} icon_item={rep} resolved={found} material={mat} cmd={cmd}")
        return

    snippet = {"categories": categories}
    if args.merge_into:
        merge_into_config(args.merge_into, categories)
        print(f"Updated categories in {args.merge_into}")
    else:
        args.output.write_text(
            yaml.dump(snippet, default_flow_style=False, sort_keys=False, allow_unicode=True),
            encoding="utf-8",
        )
        print(f"Wrote {len(categories)} categories to {args.output}")
        print("Merge into config.yml or use --merge-into")


if __name__ == "__main__":
    main()
