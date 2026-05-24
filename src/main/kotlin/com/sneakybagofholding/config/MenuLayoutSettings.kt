package com.sneakybagofholding.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import com.sneakybagofholding.util.ItemStackParser

/**
 * Hub filler that fills all unused slots except for one open slot.
 *
 * @property item Display stack (tooltip hidden by default).
 * @property openSlot The single inventory slot left empty (defaults to 0).
 */
data class HubFiller(val item: ItemStack, val openSlot: Int = 0) {
    companion object {
        fun parse(section: ConfigurationSection?): HubFiller? {
            if (section == null) return null
            val itemSection = when {
                section.contains("material") || section.contains("type") -> section
                else -> section.getConfigurationSection("item") ?: return null
            }
            val item = ItemStackParser.parse(
                itemSection,
                ItemStackParser.Options(hideTooltipByDefault = true),
            ) ?: return null
            val openSlot = section.getInt("open-slot", 0).coerceIn(0, 53)
            return HubFiller(item, openSlot)
        }
    }
}

/**
 * Hub and category menu layout options from `settings.menu`.
 *
 * @property mainMenuDecorative Optional hub filler item and slot (`hub-decorative` or legacy `slot-50`).
 * @property categoryMenuDecorative Default filler for category browsers (`category.decorative` or `slot-50`).
 * @property mainMenuCategorySlots Category id → inventory slot for hub icons; empty = sequential from 0.
 * @property hubFiller Fills all empty slots in the main menu except for one slot.
 */
data class MenuLayoutSettings(
    val mainMenuDecorative: MenuDecorative? = null,
    val categoryMenuDecorative: MenuDecorative? = null,
    val mainMenuCategorySlots: Map<String, Int> = emptyMap(),
    val hubFiller: HubFiller? = null,
)
