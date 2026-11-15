package com.sneakybagofholding.util

import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Applies the `item_model` data component from config `item-model: "<namespace:id>"` keys.
 * The integer `custom-model-data` key is still accepted as an optional legacy fallback.
 */
object ItemModelSupport {

    /** Parses a namespaced item model id (e.g. `lom:gui/bag-of-holding`); null when blank or invalid. */
    fun parseKey(raw: String?): NamespacedKey? {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return null
        return NamespacedKey.fromString(value.lowercase())
    }

    /** Applies [itemModel] when it parses; otherwise falls back to [legacyCustomModelData]. Returns true if anything was applied. */
    fun apply(meta: ItemMeta, itemModel: String?, legacyCustomModelData: Int? = null): Boolean {
        val key = parseKey(itemModel)
        if (key != null) {
            meta.itemModel = key
            return true
        }
        if (legacyCustomModelData != null) {
            CustomModelDataSupport.applyLegacyInt(meta, legacyCustomModelData)
            return true
        }
        return false
    }

    fun apply(stack: ItemStack, itemModel: String?, legacyCustomModelData: Int? = null) {
        if (itemModel == null && legacyCustomModelData == null) return
        val meta = stack.itemMeta ?: return
        if (apply(meta, itemModel, legacyCustomModelData)) {
            stack.itemMeta = meta
        }
    }

    /** Reads `item-model` (string) from [section]. */
    fun readItemModel(section: ConfigurationSection?): String? =
        section?.getString("item-model")?.takeIf { it.isNotBlank() }

    /** Reads the legacy integer `custom-model-data` key from [section], if present. */
    fun readLegacyCustomModelData(section: ConfigurationSection?): Int? =
        section?.takeIf { it.contains("custom-model-data") }?.getInt("custom-model-data")
}
