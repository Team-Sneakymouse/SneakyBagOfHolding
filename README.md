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
    autopickup-enchant-glow: true
    hub-decorative:                   # level 1: main hub filler (slot configurable)
      slot: 49
      material: GRAY_STAINED_GLASS_PANE
      name: "<gray> "
      # hide-tooltip: true            # default true for decorative items
    category:
      decorative:                     # level 2: default for category browsers (slot defaults to 50)
        slot: 50
        material: GRAY_STAINED_GLASS_PANE
    category-icon-slots:              # hub icon positions (omit = slots 0, 1, 2… skipping 50)
      fish: 10
      crafting_reagent: 12

categories:
  fish:
    menu:
      decorative: { slot: 50, material: LIME_STAINED_GLASS_PANE, name: "<green>Fish" }  # level 3
      hub-slot: 10                    # optional per-category hub slot
```

Decorative slots are non-interactive (no clicks, no deposits). Tooltip is hidden by default (`hide-tooltip: false` to show). Legacy key `slot-50:` still works (implies slot 50). Per-item `display.autopickup-off.custom-model-data` still overrides item icons when off (optional).

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

Plugin JAR (fat, for the Paper plugins folder): `build/libs/SneakyBagOfHolding-<version>.jar`

Library JAR (thin, for Maven — **do not** drop this into `plugins/`): `build/libs/sneakybagofholding-<version>.jar`

## Publishing to Maven Central

Artifacts are published under the **`io.github.team-sneakymouse`** namespace (`io.github.team-sneakymouse:sneakybagofholding`). Kotlin package names remain `com.sneakybagofholding`.

### 1. What to configure before publishing

Complete these steps once per organization / machine (same Sonatype namespace and GPG key as MagicSpells):

1. **Sonatype Central Portal account** — Register at [central.sonatype.com](https://central.sonatype.com/).
2. **Namespace verification** — Ensure `io.github.team-sneakymouse` is verified under [Publishing → Namespaces](https://central.sonatype.com/publishing/namespaces).
3. **User token** — Generate a token at [central.sonatype.com/usertoken](https://central.sonatype.com/usertoken).
4. **GPG signing key** — Maven Central requires signed artifacts. List keys with `gpg --list-secret-keys --keyid-format=long`, then in **`.gradle/gradle.properties`** (gitignored; see [`gradle.properties.example`](gradle.properties.example)):

   ```properties
   signing.keyId=38122A0D
   signing.gnupg.keyName=38122A0D
   signing.password=your-gpg-passphrase
   signing.gnupg.useGpgCmd=true
   mavenCentralUsername=...
   mavenCentralPassword=...
   ```

   You can reuse the same `.gradle/gradle.properties` values from the MagicSpells repo.

5. **Version** — Set `version` in [`gradle.properties`](gradle.properties) (e.g. `1.0.0`). It must **not** end with `-SNAPSHOT` for a release on Maven Central.

**What is published:** plain library JAR, sources, Javadoc, and POM — not the fat plugin JAR from `./gradlew build`. Published POMs only declare Maven Central–safe dependencies (Kotlin, Gson). `paper-api` and `magicspells-core` stay `compileOnly` for consumers to add themselves.

### 2. Commands to publish

Dry-run locally (no Sonatype or GPG credentials required):

```bash
./gradlew publishToMavenLocal
```

Before each upload, **bump `version` in [`gradle.properties`](gradle.properties)** if that version was already published.

Publish to Maven Central (upload + automatic release):

```bash
chmod +x scripts/publish-maven-central.sh
./scripts/publish-maven-central.sh
```

The script loads Sonatype tokens from `.gradle/gradle.properties` and runs `publishToMavenCentral`. If tokens are already in `~/.gradle/gradle.properties`, you can run `./gradlew publishToMavenCentral` instead.

**After a successful upload:** open [Central Portal → Deployments](https://central.sonatype.com/publishing/deployments). Wait until the component is **Validated**, then click **Publish** if you did not use automatic release. Artifacts usually appear on [Maven Central](https://central.sonatype.com/search) within 10–30 minutes.

Every successful Maven Central release **locks that version forever**. If publish fails partway through, or Sonatype reports the component already exists, increment `version` and publish again.

### 3. Using published artifacts in other projects

**Gradle** (Kotlin DSL):

```kotlin
repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.github.team-sneakymouse:magicspells-core:4.0-Beta-14")
    compileOnly("io.github.team-sneakymouse:sneakybagofholding:1.0.0")
}
```

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
