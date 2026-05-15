package com.sneakybagofholding.registry

import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.config.ItemDefinition
import org.bukkit.inventory.ItemStack

/**
 * Registry of configured bag items with reverse lookup from [ItemStack] to item id.
 */
class ItemRegistry(
    private val configManager: ConfigManager,
    private val magicItemResolver: MagicItemResolver
) {

    private var itemsById: Map<String, ItemDefinition> = emptyMap()
    private var templates: List<Pair<String, Any>> = emptyList()

    fun reload() {
        itemsById = configManager.getItems()
        if (!magicItemResolver.isAvailable()) {
            templates = emptyList()
            return
        }
        templates = itemsById.keys.mapNotNull { id ->
            magicItemResolver.getTemplateData(id)?.let { id to it }
        }
    }

    fun getItem(id: String): ItemDefinition? = itemsById[id]

    fun getAllItems(): Collection<ItemDefinition> = itemsById.values

    /**
     * Resolves a picked-up or clicked [stack] to a configured item id, or null.
     */
    fun resolveItemId(stack: ItemStack): String? {
        val stackData = magicItemResolver.getDataFromStack(stack) ?: return null
        for ((id, template) in templates) {
            if (magicItemResolver.matches(stackData, template)) return id
        }
        return null
    }

    fun isRegistered(id: String): Boolean = id in itemsById
}
