package com.sneakybagofholding.config

/**
 * Global plugin settings loaded from config.yml `settings` section.
 *
 * @property defaultItemCapacity Global per-item base capacity when an item omits `default-capacity`.
 * @property defaultCategoryCapacity Global category bonus when a category omits `default-capacity`.
 */
data class PluginSettings(
    val mainMenuTitle: String,
    val categoryRows: Int,
    val commandAliases: List<String>,
    val suppressAutopickupSound: Boolean,
    val defaultItemLore: List<String>,
    val defaultItemCapacity: Int = DEFAULT_ITEM_CAPACITY,
    val defaultCategoryCapacity: Int = DEFAULT_CATEGORY_CAPACITY
) {
    companion object {
        const val DEFAULT_ITEM_CAPACITY = 1000
        const val DEFAULT_CATEGORY_CAPACITY = 0
    }
}
