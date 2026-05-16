package com.sneakybagofholding.config

import org.bukkit.inventory.ItemStack

/**
 * Hub and category menu layout options from `settings.menu`.
 *
 * @property mainMenuSlot50 Decorative item for slot 50 on the main hub (optional).
 * @property categoryMenuSlot50 Default decorative item for slot 50 on category browsers.
 * @property mainMenuCategorySlots Category id → inventory slot for hub icons; empty = sequential from 0.
 */
data class MenuLayoutSettings(
    val mainMenuSlot50: ItemStack? = null,
    val categoryMenuSlot50: ItemStack? = null,
    val mainMenuCategorySlots: Map<String, Int> = emptyMap(),
)
