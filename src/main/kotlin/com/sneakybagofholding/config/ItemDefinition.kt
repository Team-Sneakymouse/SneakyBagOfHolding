package com.sneakybagofholding.config

/**
 * A MagicSpells item that can be stored in the bag of holding.
 *
 * @property id Canonical MagicSpells internal name (e.g. `fish-catfish1`).
 * @property categories Category ids this item belongs to; each adds its capacity bonus (plus global).
 * @property defaultCapacity Per-item base capacity (stacked with category bonuses).
 *   Falls back to [PluginSettings.defaultItemCapacity] when omitted in config.
 * @property soulbound When true, withdrawn stacks get MagicSpells' soulbound_owner PDC for this player.
 * @property display Optional display overrides for the category browser.
 * @property legacyKey Lowercase id without dashes, used for MagicSpells variable migration.
 */
data class ItemDefinition(
    val id: String,
    val categories: List<String>,
    val defaultCapacity: Int,
    val soulbound: Boolean = false,
    val display: ItemDisplayDefinition?
) {
    val legacyKey: String = id.lowercase().replace("-", "")
}

/**
 * Display overrides for category browser rows.
 *
 * Appearance (material, name, base CMD) is taken from the MagicSpells item when available.
 * Lore is always from [com.sneakybagofholding.config.PluginSettings.defaultItemLore] or [lore] here — never from MagicSpells.
 */
data class ItemDisplayDefinition(
    /** Optional override of the MagicSpells item display name. */
    val name: String?,
    /** Per-item lore template; empty uses global default-item-lore from settings. */
    val lore: List<String>,
    /** Fallback material when MagicSpells cannot provide the item stack. */
    val material: String?,
    /** Fallback custom model data when MagicSpells cannot provide the item stack. */
    val customModelData: Int?,
    /** Optional CMD when autopickup is enabled (defaults to MagicSpells item CMD). */
    val autopickupOnCustomModelData: Int?,
    /** Optional CMD when autopickup is disabled (defaults to MagicSpells item CMD if unset). */
    val autopickupOffCustomModelData: Int?
)
