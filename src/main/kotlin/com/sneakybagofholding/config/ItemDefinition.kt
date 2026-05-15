package com.sneakybagofholding.config

/**
 * A MagicSpells item that can be stored in the bag of holding.
 *
 * @property id Canonical MagicSpells internal name (e.g. `fish-catfish1`).
 * @property categories Category ids this item belongs to; each adds its capacity bonus.
 * @property defaultCapacity Per-item base capacity (stacked with category bonuses).
 * @property display Optional display overrides for the category browser.
 * @property legacyKey Lowercase id without dashes, used for MagicSpells variable migration.
 */
data class ItemDefinition(
    val id: String,
    val categories: List<String>,
    val defaultCapacity: Int,
    val display: ItemDisplayDefinition?
) {
    val legacyKey: String = id.lowercase().replace("-", "")
}

/**
 * Display overrides for category browser rows and autopickup icon states.
 */
data class ItemDisplayDefinition(
    val name: String?,
    val lore: List<String>,
    val autopickupOnCustomModelData: Int?,
    val autopickupOffCustomModelData: Int?
)
