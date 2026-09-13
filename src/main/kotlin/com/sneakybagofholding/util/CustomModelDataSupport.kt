package com.sneakybagofholding.util

import org.bukkit.inventory.meta.ItemMeta

/**
 * Applies custom model data using Paper's [CustomModelDataComponent] API (Paper 26.2+).
 * Legacy integer CMD maps to the first float entry, matching MagicSpells' format.
 */
object CustomModelDataSupport {

    fun applyLegacyInt(meta: ItemMeta, value: Int) {
        val component = meta.customModelDataComponent
        component.floats = listOf(value.toFloat())
        meta.setCustomModelDataComponent(component)
    }
}
