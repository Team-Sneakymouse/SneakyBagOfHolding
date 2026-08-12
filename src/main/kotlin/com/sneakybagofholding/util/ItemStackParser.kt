package com.sneakybagofholding.util

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Parses Bukkit-style item sections from plugin config.
 */
object ItemStackParser {

    data class Options(
        /** When true, tooltip is hidden unless `hide-tooltip: false` is set in config. */
        val hideTooltipByDefault: Boolean = false,
    )

    fun parse(section: ConfigurationSection?, options: Options = Options()): ItemStack? {
        if (section == null) return null
        val typeName = section.getString("material") ?: section.getString("type") ?: return null
        val material = Material.matchMaterial(typeName.uppercase()) ?: return null
        val stack = ItemStack(material)
        val meta = stack.itemMeta ?: return stack
        section.getString("name")?.let { ItemMetaText.setDisplayName(meta, it) }
        if (section.isList("lore")) {
            @Suppress("UNCHECKED_CAST")
            val lore = section.getList("lore") as? List<String> ?: emptyList()
            ItemMetaText.setLore(meta, lore)
        }
        if (section.contains("custom-model-data")) {
            CustomModelDataSupport.applyLegacyInt(meta, section.getInt("custom-model-data"))
        }
        val hideTooltip = when {
            section.contains("hide-tooltip") -> section.getBoolean("hide-tooltip")
            options.hideTooltipByDefault -> true
            else -> false
        }
        if (hideTooltip) {
            meta.isHideTooltip = true
        }
        stack.itemMeta = meta
        return stack
    }

    fun applyCustomModelData(stack: ItemStack, customModelData: Int?) {
        if (customModelData == null) return
        val meta = stack.itemMeta ?: return
        CustomModelDataSupport.applyLegacyInt(meta, customModelData)
        stack.itemMeta = meta
    }
}
