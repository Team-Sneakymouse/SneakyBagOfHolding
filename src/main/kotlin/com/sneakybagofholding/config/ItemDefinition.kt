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
    /** Fallback `item-model` id (e.g. `lom:legacy/cod/1`) when MagicSpells cannot provide the item stack. */
    val itemModel: String? = null,
    /** Optional `item-model` when autopickup is enabled (defaults to MagicSpells item appearance). */
    val autopickupOnItemModel: String? = null,
    /** Optional `item-model` when autopickup is disabled (defaults to MagicSpells item appearance if unset). */
    val autopickupOffItemModel: String? = null,
    /** Legacy integer `custom-model-data` fallback; [itemModel] wins when both are set. */
    val customModelData: Int? = null,
    /** Legacy integer for [autopickupOnItemModel]. */
    val autopickupOnCustomModelData: Int? = null,
    /** Legacy integer for [autopickupOffItemModel]. */
    val autopickupOffCustomModelData: Int? = null,
)
