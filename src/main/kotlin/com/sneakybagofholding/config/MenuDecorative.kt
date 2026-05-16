package com.sneakybagofholding.config

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import com.sneakybagofholding.util.ItemStackParser

/**
 * A non-interactive filler item at a specific inventory slot.
 *
 * @property slot Inventory index (0–53 for a 6-row chest).
 * @property item Display stack (tooltip hidden by default when parsed via [parse]).
 */
data class MenuDecorative(val slot: Int, val item: ItemStack) {

    companion object {
        const val DEFAULT_CATEGORY_SLOT = 50

        /**
         * Parses a decorative block from config.
         *
         * Supports:
         * - Legacy item-only section (implicit [defaultSlot]): `slot-50: { material: ... }`
         * - Explicit slot: `{ slot: 49, material: ... }` or `{ slot: 49, item: { material: ... } }`
         */
        fun parse(
            section: ConfigurationSection?,
            defaultSlot: Int,
        ): MenuDecorative? {
            if (section == null) return null
            val slot = if (section.contains("slot")) section.getInt("slot") else defaultSlot
            val itemSection = when {
                section.contains("material") || section.contains("type") -> section
                else -> section.getConfigurationSection("item") ?: return null
            }
            val item = ItemStackParser.parse(
                itemSection,
                ItemStackParser.Options(hideTooltipByDefault = true),
            ) ?: return null
            return MenuDecorative(slot.coerceIn(0, 53), item)
        }
    }
}
