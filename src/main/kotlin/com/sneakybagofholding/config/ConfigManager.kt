package com.sneakybagofholding.config

import com.sneakybagofholding.SneakyBagOfHolding
import com.sneakybagofholding.util.ItemStackParser
import org.bukkit.configuration.file.FileConfiguration

/**
 * Loads and exposes immutable configuration snapshots from config.yml.
 */
class ConfigManager(private val plugin: SneakyBagOfHolding) {

    private var categories: Map<String, CategoryDefinition> = emptyMap()
    private var items: Map<String, ItemDefinition> = emptyMap()
    private var settings: PluginSettings = defaultSettings()

    /** All loaded categories keyed by id. */
    fun getCategories(): Map<String, CategoryDefinition> = categories

    /** All loaded items keyed by MagicSpells internal name. */
    fun getItems(): Map<String, ItemDefinition> = items

    /** Categories that have a menu icon and can be opened from the hub. */
    fun getBrowsableCategories(): List<CategoryDefinition> =
        categories.values.filter { it.isBrowsable }

    /** Items belonging to a category, in config iteration order. */
    fun getItemsInCategory(categoryId: String): List<ItemDefinition> =
        items.values.filter { categoryId in it.categories }

    /** Global plugin settings. */
    fun getSettings(): PluginSettings = settings

    /**
     * Reloads configuration from disk.
     */
    fun reload() {
        plugin.reloadConfig()
        val config = plugin.config
        settings = loadSettings(config)
        categories = loadCategories(config)
        items = loadItems(config)
        validateItemCategories()
    }

    private fun loadSettings(config: FileConfiguration): PluginSettings {
        val menu = config.getConfigurationSection("settings.menu")
        val autopickup = config.getConfigurationSection("settings.autopickup")
        val lore = config.getStringList("settings.menu.default-item-lore")
        return PluginSettings(
            mainMenuTitle = menu?.getString("main-title") ?: "<gold>Bag of Holding",
            categoryRows = menu?.getInt("category-rows") ?: 6,
            commandAliases = menu?.getStringList("commands")?.takeIf { it.isNotEmpty() }
                ?: listOf("boh", "bag", "bagofholding"),
            suppressAutopickupSound = autopickup?.getBoolean("suppress-vanilla-pickup-sound") ?: true,
            defaultItemLore = lore.ifEmpty {
                listOf(
                    "<yellow>Stored: <aqua>{stored}<yellow>/<aqua>{max}",
                    "<yellow>L-Click:<gray> Withdraw Item",
                    "<yellow>R-Click:<gray> Deposit Item",
                    "<gray>Hold Shift to move Stacks",
                    "",
                    "<yellow>Autoloot: {autoloot_status}",
                    "<gray>F to Toggle"
                )
            }
        )
    }

    private fun loadCategories(config: FileConfiguration): Map<String, CategoryDefinition> {
        val section = config.getConfigurationSection("categories") ?: return emptyMap()
        return section.getKeys(false).associateWith { id ->
            val cat = section.getConfigurationSection(id)!!
            val title = cat.getString("menu-title") ?: "<gold>${id.replace('_', ' ').replaceFirstChar { it.uppercase() }}"
            CategoryDefinition(
                id = id,
                defaultCapacity = cat.getInt("default-capacity", 0),
                menuIcon = ItemStackParser.parse(cat.getConfigurationSection("menu-icon")),
                menuTitle = title
            )
        }
    }

    private fun loadItems(config: FileConfiguration): Map<String, ItemDefinition> {
        val section = config.getConfigurationSection("items") ?: return emptyMap()
        return section.getKeys(false).associateWith { id ->
            val item = section.getConfigurationSection(id)!!
            val displaySection = item.getConfigurationSection("display")
            val display = if (displaySection != null) {
                val onSection = displaySection.getConfigurationSection("autopickup-on")
                val offSection = displaySection.getConfigurationSection("autopickup-off")
                ItemDisplayDefinition(
                    name = displaySection.getString("name"),
                    lore = displaySection.getStringList("lore"),
                    autopickupOnCustomModelData = onSection?.getInt("custom-model-data"),
                    autopickupOffCustomModelData = offSection?.getInt("custom-model-data")
                )
            } else null
            ItemDefinition(
                id = id,
                categories = item.getStringList("categories"),
                defaultCapacity = item.getInt("default-capacity", 0),
                display = display
            )
        }
    }

    private fun validateItemCategories() {
        for (item in items.values) {
            for (cat in item.categories) {
                if (cat !in categories) {
                    plugin.logger.warning("Item ${item.id} references unknown category: $cat")
                }
            }
        }
    }

    companion object {
        fun defaultSettings() = PluginSettings(
            mainMenuTitle = "<gold>Bag of Holding",
            categoryRows = 6,
            commandAliases = listOf("boh", "bag", "bagofholding"),
            suppressAutopickupSound = true,
            defaultItemLore = emptyList()
        )
    }
}
