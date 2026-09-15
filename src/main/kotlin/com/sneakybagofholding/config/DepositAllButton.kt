package com.sneakybagofholding.config

import com.sneakybagofholding.util.ItemMetaText
import com.sneakybagofholding.util.ItemStackParser
import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack

/**
 * Hub-menu action that deposits every registered, storable stack from the player's inventory.
 *
 * @property slot Inventory index (0–53 for a 6-row chest). Defaults to [DEFAULT_SLOT].
 * @property item Display stack for the button.
 */
data class DepositAllButton(val slot: Int, val item: ItemStack) {

    companion object {
        const val DEFAULT_SLOT = 38

        fun parse(section: ConfigurationSection?): DepositAllButton {
            val slot = (section?.getInt("slot", DEFAULT_SLOT) ?: DEFAULT_SLOT).coerceIn(0, 53)
            val itemSection = when {
                section == null -> null
                section.contains("material") || section.contains("type") -> section
                else -> section.getConfigurationSection("item")
            }
            val item = itemSection?.let {
                ItemStackParser.parse(it, ItemStackParser.Options(hideTooltipByDefault = false))
            } ?: defaultItem()
            return DepositAllButton(slot, item)
        }

        fun defaultItem(): ItemStack {
            val stack = ItemStack(Material.CHEST)
            val meta = stack.itemMeta ?: return stack
            ItemMetaText.setDisplayName(meta, "<green>Deposit All")
            ItemMetaText.setLore(
                meta,
                listOf("<gray>Deposit all bag items", "<gray>from your inventory"),
            )
            stack.itemMeta = meta
            return stack
        }
    }
}
