#!/usr/bin/env python3
"""
Move legacy per-item capacity overrides into category bonuses, then clear itemCapacity.

MagicSpells migration stored extra max on specific items (petals, goldOre, ore, stone).
SneakyBagOfHolding stacks category capacity instead. This script:

  - item-arcanePetal (and any other *Petal / item-seeds overrides) → category "gathering"
    bonus = item_capacity - item_base (default 1000)
  - item-goldOre (and item-ore / item-stone if present) → category "ore" (mining tab)
    bonus = item_capacity - item_base
  - Removes all petal, ore, and stone keys from itemCapacity

Usage:
  python migrate_item_capacity_to_categories.py \\
    --data-dir /path/to/plugins/SneakyBagOfHolding/data \\
    [--item-base 1000] \\
    [--dry-run]
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

UUID_JSON = re.compile(
    r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.json$",
    re.IGNORECASE,
)

GATHERING_CATEGORY = "gathering"
ORE_CATEGORY = "ore"  # hub "Digging & Mining" category id in config.yml

GATHERING_SOURCE_ITEM = "item-arcanePetal"
ORE_SOURCE_ITEM = "item-goldOre"

ORE_ITEMS = frozenset({"item-goldOre", "item-ore", "item-stone"})
SEEDS_ITEM = "item-seeds"


def is_petal_item(item_id: str) -> bool:
    return item_id.endswith("Petal")


def should_remove_from_item_capacity(item_id: str) -> bool:
    return is_petal_item(item_id) or item_id in ORE_ITEMS or item_id == SEEDS_ITEM


def category_bonus(item_value: int, item_base: int) -> int:
    return max(0, int(item_value) - item_base)


def gathering_bonus(item_capacity: dict[str, int], item_base: int) -> int | None:
    """Prefer arcane petal; otherwise highest petal/seeds override (same tier in legacy data)."""
    if GATHERING_SOURCE_ITEM in item_capacity:
        return category_bonus(item_capacity[GATHERING_SOURCE_ITEM], item_base)
    candidates = [
        v
        for k, v in item_capacity.items()
        if is_petal_item(k) or k == SEEDS_ITEM
    ]
    if not candidates:
        return None
    return category_bonus(max(candidates), item_base)


def ore_bonus(item_capacity: dict[str, int], item_base: int) -> int | None:
    """Prefer goldOre; otherwise highest ore/stone override."""
    if ORE_SOURCE_ITEM in item_capacity:
        return category_bonus(item_capacity[ORE_SOURCE_ITEM], item_base)
    candidates = [v for k, v in item_capacity.items() if k in ORE_ITEMS]
    if not candidates:
        return None
    return category_bonus(max(candidates), item_base)


def migrate_player_data(data: dict, item_base: int) -> tuple[dict, dict]:
    """
    Returns (updated_data, stats) where stats describes changes for this file.
    """
    stats = {
        "gathering_bonus": None,
        "ore_bonus": None,
        "removed_keys": [],
    }

    item_capacity = data.get("itemCapacity")
    if not isinstance(item_capacity, dict) or not item_capacity:
        return data, stats

    to_remove = [k for k in item_capacity if should_remove_from_item_capacity(k)]
    if not to_remove:
        return data, stats

    g_bonus = gathering_bonus(item_capacity, item_base)
    o_bonus = ore_bonus(item_capacity, item_base)

    category_capacity = data.get("categoryCapacity")
    if not isinstance(category_capacity, dict):
        category_capacity = {}
        data["categoryCapacity"] = category_capacity

    if g_bonus is not None and g_bonus > 0:
        prev = category_capacity.get(GATHERING_CATEGORY, 0)
        category_capacity[GATHERING_CATEGORY] = max(int(prev), g_bonus)
        stats["gathering_bonus"] = g_bonus

    if o_bonus is not None and o_bonus > 0:
        prev = category_capacity.get(ORE_CATEGORY, 0)
        category_capacity[ORE_CATEGORY] = max(int(prev), o_bonus)
        stats["ore_bonus"] = o_bonus

    for key in to_remove:
        del item_capacity[key]
        stats["removed_keys"].append(key)

    if not item_capacity:
        data["itemCapacity"] = {}

    return data, stats


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--data-dir",
        type=Path,
        required=True,
        help="Directory with <uuid>.json player data files",
    )
    parser.add_argument(
        "--item-base",
        type=int,
        default=1000,
        help="Default per-item base capacity to subtract (settings.defaults.item-capacity)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Print changes without writing files",
    )
    args = parser.parse_args()

    if not args.data_dir.is_dir():
        print(f"Data directory not found: {args.data_dir}", file=sys.stderr)
        sys.exit(1)

    files_changed = 0
    gathering_players = 0
    ore_players = 0
    keys_removed = 0

    for path in sorted(args.data_dir.glob("*.json")):
        if not UUID_JSON.match(path.name):
            continue
        try:
            data = json.loads(path.read_text(encoding="utf-8"))
        except (json.JSONDecodeError, OSError) as e:
            print(f"Skip {path.name}: {e}", file=sys.stderr)
            continue

        updated, stats = migrate_player_data(data, args.item_base)
        if not stats["removed_keys"]:
            continue

        files_changed += 1
        keys_removed += len(stats["removed_keys"])
        if stats["gathering_bonus"]:
            gathering_players += 1
        if stats["ore_bonus"]:
            ore_players += 1

        action = "would update" if args.dry_run else "updated"
        parts = [f"{action} {path.name}"]
        if stats["gathering_bonus"]:
            parts.append(f"gathering={stats['gathering_bonus']}")
        if stats["ore_bonus"]:
            parts.append(f"ore={stats['ore_bonus']}")
        parts.append(f"removed={len(stats['removed_keys'])}")
        print(" ".join(parts))

        if not args.dry_run:
            path.write_text(json.dumps(updated, indent=2) + "\n", encoding="utf-8")

    mode = "Dry-run: " if args.dry_run else ""
    print(
        f"\n{mode}{files_changed} file(s) with item capacity migrated; "
        f"{gathering_players} gathering, {ore_players} ore; "
        f"{keys_removed} itemCapacity key(s) removed."
    )


if __name__ == "__main__":
    main()
