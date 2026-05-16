package com.sneakybagofholding.util

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ItemLore
import net.kyori.adventure.text.Component
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Applies MiniMessage / legacy-formatted text to item meta via the Adventure API.
 */
object ItemMetaText {

    fun setDisplayName(meta: ItemMeta, text: String) {
        meta.displayName(TextUtility.toComponent(text))
    }

    fun toLoreComponents(lines: List<String>): List<Component> =
        lines.map { line -> if (line.isEmpty()) Component.empty() else TextUtility.toComponent(line) }

    /** Writes the LORE data component to match meta (call after [setLore] on meta in the same build). */
    fun syncLoreComponent(stack: ItemStack, components: List<Component>) {
        if (components.isEmpty()) {
            stack.unsetData(DataComponentTypes.LORE)
            return
        }
        stack.setData(DataComponentTypes.LORE, ItemLore.lore().lines(components).build())
    }

    fun setLore(meta: ItemMeta, lines: List<String>) {
        meta.lore(toLoreComponents(lines))
    }
}
