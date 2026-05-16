package com.sneakybagofholding.config

import org.bukkit.inventory.ItemStack

/**
 * Hub and category menu layout options from `settings.menu`.
 *
 * @property mainMenuDecorative Optional hub filler item and slot (`hub-decorative` or legacy `slot-50`).
 * @property categoryMenuDecorative Default filler for category browsers (`category.decorative` or `slot-50`).
 * @property mainMenuCategorySlots Category id → inventory slot for hub icons; empty = sequential from 0.
 */
data class MenuLayoutSettings(
    val mainMenuDecorative: MenuDecorative? = null,
    val categoryMenuDecorative: MenuDecorative? = null,
    val mainMenuCategorySlots: Map<String, Int> = emptyMap(),
)
