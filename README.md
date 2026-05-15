# SneakyBagOfHolding

Paper plugin that replaces the MagicSpells-based Bag of Holding with config-driven categories, stacked storage capacity, inventory GUIs, and per-item autopickup.

**Requires [MagicSpells](https://github.com/TheGreyGhost/MagicSpells)** on the server for item matching and giving items on withdraw.

## Features

- Store MagicSpells items virtually per player (UUID JSON files)
- **Categories** with optional hub icons (categories without `menu-icon` still affect capacity)
- **Stacked capacity**: `effectiveMax = itemCapacity + sum(categoryCapacity for each category) + globalCapacity`
- Category browser: deposit, withdraw, toggle autopickup (F / swap offhand); optional enchant glint when autopickup is on
- Hub menu: open categories, drag-drop or shift-click deposit
- Admin commands for capacity overrides and stored amounts
- Config reload without restart

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/boh` | `sneakybagofholding.use` | Open main menu |
| `/bag`, `/bagofholding` | `sneakybagofholding.use` | Aliases of `/boh` |
| `/boh reload` | `sneakybagofholding.reload` | Reload config |
| `/boh capacity <player> global <value>` | `sneakybagofholding.admin.capacity` | Set player global capacity bonus |
| `/boh capacity <player> item\|category <id> <value>` | `sneakybagofholding.admin.capacity` | Set player item or category capacity override |
| `/boh give <player> <itemId> <amount>` | `sneakybagofholding.admin` | Add to stored count (clamped to max) |
| `/boh take <player> <itemId> <amount>` | `sneakybagofholding.admin` | Remove from stored count |

## Permissions

| Node | Default | Description |
|------|---------|-------------|
| `sneakybagofholding.use` | `true` | Open menu |
| `sneakybagofholding.reload` | `op` | Reload config |
| `sneakybagofholding.admin` | `op` | give/take |
| `sneakybagofholding.admin.capacity` | `op` | capacity subcommand |

## Configuration

See `config.yml` for structure.

### Global defaults

```yaml
settings:
  defaults:
    item-capacity: 1000        # per-item base when an item omits default-capacity
    category-capacity: 0       # category bonus when a category omits default-capacity
    global-capacity: 0         # per-player bonus on every item when not overridden
```

### Categories

```yaml
categories:
  fish:
    default-capacity: 100      # optional; overrides global category-capacity for this category
    menu-title: "<gold>Fish"
    menu-icon:                 # omit = not browsable from hub
      material: COD
```

### Menu display

```yaml
settings:
  menu:
    autopickup-enchant-glow: true   # glint on icon when autopickup is on; no glint when off
```

Per-item `display.autopickup-off.custom-model-data` still overrides the model while off (optional).

### Items

```yaml
items:
  fish-catfish1:
    categories: [fish, crafting_reagent]
    # default-capacity omitted → uses settings.defaults.item-capacity (1000)
    display:
      autopickup-on:
        custom-model-data: 94
      autopickup-off:
        custom-model-data: 95  # e.g. border when autopickup off
```

### Stacked capacity example

| Component | Value |
|-----------|-------|
| Item `fish-catfish1` | 0 |
| Category `fish` | 100 |
| Category `crafting_reagent` | 200 |
| Global (player) | 50 |
| **Effective max** | **350** |

Player overrides via `/boh capacity` replace the config default for that component (global uses `settings.defaults.global-capacity` when unset).

## Migration from MagicSpells

1. Generate categories (hub icons + titles from legacy menus):

   ```bash
   python tools/generate_categories.py \
     --spells-yml /path/to/spells-system-bagofholding.yml \
     --spells-items-dir /path/to/dev/spells-items \
     --merge-into plugins/SneakyBagOfHolding/config.yml
   ```

   Use `--list-icons` to preview representative items. Use `--legacy-hub-style` for the old jigsaw/rabbit_foot hub buttons.

2. Bootstrap items from legacy spells YAML:

   ```bash
   python tools/bootstrap_config_from_spells.py \
     --spells-yml /path/to/spells-system-bagofholding.yml \
     --output config-items-snippet.yml
   ```

   Merge `items` into `config.yml` and define `categories` to match your server.

3. Migrate player variable files:

   ```bash
   python tools/migrate_magicspells_variables.py \
     --variables-dir /path/to/MagicSpells/variables \
     --config plugins/SneakyBagOfHolding/config.yml \
     --output plugins/SneakyBagOfHolding/data
   ```

   Output files use **dashed UUIDs** (`550e8400-e29b-41d4-a716-446655440000.json`) to match Paper player UUIDs. MagicSpells `PLAYER_<32hex>.txt` names are normalized automatically.

   If you already migrated with undashed filenames, rename them in place:

   ```bash
   python tools/migrate_magicspells_variables.py \
     --output plugins/SneakyBagOfHolding/data \
     --fix-uuid-filenames
   ```

   Legacy mapping: dashes removed from the item id, camelCase kept — `item-spellThread` → `bank_itemspellThread`, `max_itemspellThread`, `autoloot_itemspellThread` (lookup is case-insensitive).

4. Remove old spells (`system-bagofholding-*`), variables block, and `autolootPickup` from spellbooks.

## Build

```bash
./gradlew build
```

Output JAR: `build/libs/SneakyBagOfHolding-1.0.0.jar`

## Data files

Player data: `plugins/SneakyBagOfHolding/data/<uuid-with-dashes>.json`

```json
{
  "stored": { "item-banana": 42 },
  "autopickup": { "item-banana": true },
  "itemCapacity": {},
  "categoryCapacity": {},
  "globalCapacity": null
}
```
