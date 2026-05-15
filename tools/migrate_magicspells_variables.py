#!/usr/bin/env python3
"""
Migrate MagicSpells player variable files to SneakyBagOfHolding JSON data.

Usage:
  python migrate_magicspells_variables.py \\
    --variables-dir /path/to/MagicSpells/variables \\
    --config /path/to/plugins/SneakyBagOfHolding/config.yml \\
    --output /path/to/plugins/SneakyBagOfHolding/data \\
    [--import-all-max]

MagicSpells format: PLAYER_<uuid>.txt with lines like bank_itembanana=42
Legacy keys: item id with dashes removed, camelCase preserved (item-spellThread -> bank_itemspellThread).
"""

from __future__ import annotations

import argparse
import json
import re
import sys
import uuid
from pathlib import Path

try:
    import yaml
except ImportError:
    print("PyYAML required: pip install pyyaml", file=sys.stderr)
    sys.exit(1)


def legacy_key(item_id: str) -> str:
    """MagicSpells variable suffix: item-spellThread -> itemspellThread (not all-lowercase)."""
    return item_id.replace("-", "")


def ms_value_index(ms_values: dict[str, str]) -> dict[str, str]:
    """Case-insensitive lookup for PLAYER_*.txt keys (bank_itemspellThread vs bank_itemspellthread)."""
    return {k.lower(): v for k, v in ms_values.items()}


def normalize_uuid(raw: str) -> str:
    """
    MagicSpells uses PLAYER_<uuid>.txt without dashes; Paper/Java use dashed UUIDs.
    Returns canonical form: 550e8400-e29b-41d4-a716-446655440000
    """
    cleaned = raw.strip().lower().replace("-", "")
    if len(cleaned) == 32:
        return str(uuid.UUID(hex=cleaned))
    return str(uuid.UUID(raw.strip()))


def rename_undashed_json_files(data_dir: Path) -> int:
    """Rename existing <32hex>.json files to dashed UUID filenames."""
    renamed = 0
    hex_pattern = re.compile(r"^[0-9a-f]{32}\.json$", re.IGNORECASE)
    for path in list(data_dir.glob("*.json")):
        if not hex_pattern.match(path.name):
            continue
        raw = path.stem
        dashed = normalize_uuid(raw)
        target = data_dir / f"{dashed}.json"
        if target.exists() and target != path:
            print(f"Skip {path.name}: {dashed}.json already exists", file=sys.stderr)
            continue
        path.rename(target)
        renamed += 1
    return renamed


def load_item_ids(config_path: Path) -> list[str]:
    with config_path.open(encoding="utf-8") as f:
        config = yaml.safe_load(f) or {}
    items = config.get("items") or {}
    return list(items.keys())


def parse_player_file(path: Path) -> dict[str, str]:
    values: dict[str, str] = {}
    for line in path.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or "=" not in line:
            continue
        key, _, val = line.partition("=")
        values[key.strip()] = val.strip()
    return values


def global_item_capacity(config: dict) -> int:
    defaults = (config.get("settings") or {}).get("defaults") or {}
    return int(defaults.get("item-capacity", 1000))


def migrate_file(
    ms_values: dict[str, str],
    item_ids: list[str],
    import_all_max: bool,
    config_path: Path,
) -> dict:
    with config_path.open(encoding="utf-8") as f:
        config = yaml.safe_load(f) or {}
    item_defs = (config.get("items") or {})
    global_default = global_item_capacity(config)

    stored: dict[str, int] = {}
    autopickup: dict[str, bool] = {}
    item_capacity: dict[str, int] = {}
    ms_index = ms_value_index(ms_values)

    for item_id in item_ids:
        key = legacy_key(item_id)
        bank_key = f"bank_{key}".lower()
        max_key = f"max_{key}".lower()
        autoloot_key = f"autoloot_{key}".lower()

        if bank_key in ms_index:
            try:
                amount = int(float(ms_index[bank_key]))
                if amount > 0:
                    stored[item_id] = amount
            except ValueError:
                pass

        if autoloot_key in ms_index:
            try:
                if int(float(ms_index[autoloot_key])) == 1:
                    autopickup[item_id] = True
            except ValueError:
                pass

        if max_key in ms_index:
            try:
                max_val = int(float(ms_index[max_key]))
                item_cfg = item_defs.get(item_id) or {}
                if "default-capacity" in item_cfg:
                    default = int(item_cfg["default-capacity"])
                else:
                    default = global_default
                if import_all_max or max_val != default:
                    item_capacity[item_id] = max_val
            except ValueError:
                pass

    result: dict = {
        "stored": stored,
        "autopickup": autopickup,
        "itemCapacity": item_capacity,
        "categoryCapacity": {},
        "globalCapacity": None,
    }
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--variables-dir", type=Path, required=True)
    parser.add_argument("--config", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument(
        "--import-all-max",
        action="store_true",
        help="Import max_* even when equal to config default",
    )
    parser.add_argument(
        "--fix-uuid-filenames",
        action="store_true",
        help="Rename existing 32-char hex .json files to dashed UUID names (no migration)",
    )
    args = parser.parse_args()

    if args.fix_uuid_filenames:
        if not args.output.is_dir():
            print(f"Output directory not found: {args.output}", file=sys.stderr)
            sys.exit(1)
        n = rename_undashed_json_files(args.output)
        print(f"Renamed {n} file(s) in {args.output}")
        return

    if not args.config.exists():
        print(f"Config not found: {args.config}", file=sys.stderr)
        sys.exit(1)

    item_ids = load_item_ids(args.config)
    args.output.mkdir(parents=True, exist_ok=True)

    pattern = re.compile(r"^PLAYER_(.+)\.txt$", re.IGNORECASE)
    count = 0
    for path in args.variables_dir.glob("PLAYER_*.txt"):
        m = pattern.match(path.name)
        if not m:
            continue
        uuid_raw = m.group(1)
        uuid_dashed = normalize_uuid(uuid_raw)
        ms_values = parse_player_file(path)
        data = migrate_file(ms_values, item_ids, args.import_all_max, args.config)
        out_path = args.output / f"{uuid_dashed}.json"
        out_path.write_text(json.dumps(data, indent=2), encoding="utf-8")
        count += 1

    print(f"Migrated {count} player file(s) to {args.output}")


if __name__ == "__main__":
    main()
