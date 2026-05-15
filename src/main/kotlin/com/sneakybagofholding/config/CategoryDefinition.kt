package com.sneakybagofholding.config

import org.bukkit.inventory.ItemStack

/**
 * A storage category that can contribute additive capacity to items.
 *
 * @property id Unique category id (e.g. `fish`, `ore`).
 * @property defaultCapacity Base capacity bonus applied to every item in this category.
 *   Falls back to [PluginSettings.defaultCategoryCapacity] when omitted in config.
 * @property menuIcon Optional icon for the main hub; null means browse-only via other means / storage-only.
 * @property menuTitle Title for the category browser inventory.
 */
data class CategoryDefinition(
    val id: String,
    val defaultCapacity: Int,
    val menuIcon: ItemStack?,
    val menuTitle: String
) {
    /** Whether this category appears as a button on the main hub menu. */
    val isBrowsable: Boolean get() = menuIcon != null
}
