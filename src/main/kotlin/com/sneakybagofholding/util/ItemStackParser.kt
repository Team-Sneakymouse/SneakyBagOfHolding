package com.sneakybagofholding.util

import org.bukkit.Material
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Parses Bukkit-style item sections from plugin config.
 */
object ItemStackParser {

    fun parse(section: ConfigurationSection?): ItemStack? {
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
            meta.setCustomModelData(section.getInt("custom-model-data"))
        }
        stack.itemMeta = meta
        return stack
    }

    fun applyCustomModelData(stack: ItemStack, customModelData: Int?) {
        if (customModelData == null) return
        val meta = stack.itemMeta ?: return
        meta.setCustomModelData(customModelData)
        stack.itemMeta = meta
    }
}
