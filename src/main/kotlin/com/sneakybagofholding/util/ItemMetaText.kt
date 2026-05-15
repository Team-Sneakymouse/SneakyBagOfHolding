package com.sneakybagofholding.util

import net.kyori.adventure.text.Component
import org.bukkit.inventory.meta.ItemMeta

/**
 * Applies MiniMessage / legacy-formatted text to item meta via the Adventure API.
 */
object ItemMetaText {

    fun setDisplayName(meta: ItemMeta, text: String) {
        meta.displayName(TextUtility.toComponent(text))
    }

    fun setLore(meta: ItemMeta, lines: List<String>) {
        meta.lore(lines.map { line ->
            if (line.isEmpty()) Component.empty() else TextUtility.toComponent(line)
        })
    }
}
