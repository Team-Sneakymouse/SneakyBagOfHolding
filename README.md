# SneakyBagOfHolding

Paper plugin that replaces the MagicSpells-based Bag of Holding with config-driven categories, stacked storage capacity, inventory GUIs, and per-item autopickup.

**Requires [MagicSpells](https://github.com/TheGreyGhost/MagicSpells)** on the server for item matching and giving items on withdraw.

## Features

- Store MagicSpells items virtually per player (UUID JSON files)
- **Categories** with optional hub icons (categories without `menu-icon` still affect capacity)
- **Stacked capacity**: `effectiveMax = itemCapacity + sum(categoryCapacity for each category)`
- Category browser: deposit, withdraw, toggle autopickup (F / swap offhand)
- Hub menu: open categories, drag-drop or shift-click deposit
- Admin commands for capacity overrides and stored amounts
- Config reload without restart

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/boh` | `sneakybagofholding.use` | Open main menu |
| `/bag`, `/bagofholding` | `sneakybagofholding.use` | Aliases of `/boh` |
| `/boh reload` | `sneakybagofholding.reload` | Reload config |
| `/boh capacity <player> item\|category <id> <value>` | `sneakybagofholding.admin.capacity` | Set player capacity override |
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
| **Effective max** | **300** |

Player overrides via `/boh capacity` replace the config default for that component.

## Migration from MagicSpells

1. Bootstrap items from legacy spells YAML:

   ```bash
   python tools/bootstrap_config_from_spells.py \
     --spells-yml /path/to/spells-system-bagofholding.yml \
     --output config-items-snippet.yml
   ```

   Merge `items` into `config.yml` and define `categories` to match your server.

2. Migrate player variable files:

   ```bash
   python tools/migrate_magicspells_variables.py \
     --variables-dir /path/to/MagicSpells/variables \
     --config plugins/SneakyBagOfHolding/config.yml \
     --output plugins/SneakyBagOfHolding/data
   ```

   Legacy mapping: `item-banana` → `bank_itembanana`, `max_itembanana`, `autoloot_itembanana`.

3. Remove old spells (`system-bagofholding-*`), variables block, and `autolootPickup` from spellbooks.

## Build

```bash
./gradlew build
```

Output JAR: `build/libs/SneakyBagOfHolding-1.0.0.jar`

## Data files

Player data: `plugins/SneakyBagOfHolding/data/<uuid>.json`

```json
{
  "stored": { "item-banana": 42 },
  "autopickup": { "item-banana": true },
  "itemCapacity": {},
  "categoryCapacity": {}
}
```
