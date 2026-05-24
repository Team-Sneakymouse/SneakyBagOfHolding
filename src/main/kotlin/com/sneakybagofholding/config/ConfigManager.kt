package com.sneakybagofholding.config

import com.sneakybagofholding.SneakyBagOfHolding
import com.sneakybagofholding.util.ItemStackParser
import org.bukkit.configuration.file.FileConfiguration
import org.bukkit.inventory.ItemStack

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
        val defaults = config.getConfigurationSection("settings.defaults")
        val lore = config.getStringList("settings.menu.default-item-lore")
        return PluginSettings(
            mainMenuTitle = menu?.getString("main-title") ?: "<gold>Bag of Holding",
            categoryRows = menu?.getInt("category-rows") ?: 6,
            commandAliases = menu?.getStringList("commands")?.takeIf { it.isNotEmpty() }
                ?: listOf("boh", "bag", "bagofholding"),
            suppressAutopickupSound = autopickup?.getBoolean("suppress-vanilla-pickup-sound") ?: false,
            preventThrownPickup = autopickup?.getBoolean("prevent-thrown-pickup") ?: true,
            autopickupEnchantGlow = menu?.getBoolean("autopickup-enchant-glow") ?: true,
            defaultItemCapacity = defaults?.getInt(
                "item-capacity",
                PluginSettings.DEFAULT_ITEM_CAPACITY
            ) ?: PluginSettings.DEFAULT_ITEM_CAPACITY,
            defaultCategoryCapacity = defaults?.getInt(
                "category-capacity",
                PluginSettings.DEFAULT_CATEGORY_CAPACITY
            ) ?: PluginSettings.DEFAULT_CATEGORY_CAPACITY,
            defaultGlobalCapacity = defaults?.getInt(
                "global-capacity",
                PluginSettings.DEFAULT_GLOBAL_CAPACITY
            ) ?: PluginSettings.DEFAULT_GLOBAL_CAPACITY,
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
            },
            menuLayout = loadMenuLayout(menu),
        )
    }

    private fun loadMenuLayout(menu: org.bukkit.configuration.ConfigurationSection?): MenuLayoutSettings {
        val categorySection = menu?.getConfigurationSection("category")
        val iconSlotsSection = menu?.getConfigurationSection("category-icon-slots")
        val iconSlots = iconSlotsSection?.getKeys(false)?.associateWith { key ->
            iconSlotsSection.getInt(key)
        } ?: emptyMap()
        return MenuLayoutSettings(
            mainMenuDecorative = parseMenuDecorative(
                menu?.getConfigurationSection("hub-decorative")
                    ?: menu?.getConfigurationSection("slot-50"),
                defaultSlot = MenuDecorative.DEFAULT_CATEGORY_SLOT,
            ),
            categoryMenuDecorative = parseMenuDecorative(
                categorySection?.getConfigurationSection("decorative")
                    ?: categorySection?.getConfigurationSection("slot-50"),
                defaultSlot = MenuDecorative.DEFAULT_CATEGORY_SLOT,
            ),
            mainMenuCategorySlots = iconSlots,
            hubFiller = HubFiller.parse(menu?.getConfigurationSection("hub-filler")),
        )
    }

    private fun parseMenuDecorative(
        section: org.bukkit.configuration.ConfigurationSection?,
        defaultSlot: Int,
    ): MenuDecorative? = MenuDecorative.parse(section, defaultSlot)

    /** Category browser filler: per-category → global category default. */
    fun resolveCategoryMenuDecorative(category: CategoryDefinition): MenuDecorative? =
        category.menuDecorative ?: settings.menuLayout.categoryMenuDecorative

    private fun loadCategoryMenuDecorative(cat: org.bukkit.configuration.ConfigurationSection): MenuDecorative? {
        val menuSection = cat.getConfigurationSection("menu") ?: return null
        return parseMenuDecorative(
            menuSection.getConfigurationSection("decorative")
                ?: menuSection.getConfigurationSection("slot-50"),
            MenuDecorative.DEFAULT_CATEGORY_SLOT,
        )
    }

    private fun loadCategoryMainMenuSlot(
        cat: org.bukkit.configuration.ConfigurationSection,
        categoryId: String,
        layout: MenuLayoutSettings,
    ): Int? {
        val menuSection = cat.getConfigurationSection("menu")
        if (menuSection != null && menuSection.contains("hub-slot")) {
            return menuSection.getInt("hub-slot")
        }
        if (cat.contains("hub-slot")) {
            return cat.getInt("hub-slot")
        }
        return layout.mainMenuCategorySlots[categoryId]
    }

    private fun loadCategories(config: FileConfiguration): Map<String, CategoryDefinition> {
        val globalCategoryDefault = settings.defaultCategoryCapacity
        val section = config.getConfigurationSection("categories") ?: return emptyMap()
        return section.getKeys(false).associateWith { id ->
            val cat = section.getConfigurationSection(id)!!
            val title = cat.getString("menu-title") ?: "<gold>${id.replace('_', ' ').replaceFirstChar { it.uppercase() }}"
            val layout = settings.menuLayout
            CategoryDefinition(
                id = id,
                defaultCapacity = resolveCapacity(cat, "default-capacity", globalCategoryDefault),
                menuIcon = ItemStackParser.parse(cat.getConfigurationSection("menu-icon")),
                menuTitle = title,
                menuDecorative = loadCategoryMenuDecorative(cat),
                mainMenuSlot = loadCategoryMainMenuSlot(cat, id, layout),
            )
        }.also { loaded ->
            validateMainMenuCategorySlots(loaded.values.toList(), settings.menuLayout)
        }
    }

    private fun validateMainMenuCategorySlots(
        categories: List<CategoryDefinition>,
        layout: MenuLayoutSettings,
    ) {
        if (layout.mainMenuCategorySlots.isEmpty()) return
        val byId = categories.associateBy { it.id }
        val browsableIds = categories.filter { it.isBrowsable }.map { it.id }.toSet()
        for ((categoryId, slot) in layout.mainMenuCategorySlots) {
            if (categoryId !in byId) {
                plugin.logger.warning("category-icon-slots: unknown category '$categoryId'")
            } else if (categoryId !in browsableIds) {
                plugin.logger.warning("category-icon-slots: '$categoryId' has no menu-icon")
            }
            if (slot < 0 || slot > 53) {
                plugin.logger.warning("category-icon-slots: slot $slot for '$categoryId' is out of range (0-53)")
            }
        }
        val slotCounts = layout.mainMenuCategorySlots.values.groupingBy { it }.eachCount()
        for ((slot, count) in slotCounts) {
            if (count > 1) {
                plugin.logger.warning("category-icon-slots: slot $slot is assigned to $count categories")
            }
        }
    }

    private fun loadItems(config: FileConfiguration): Map<String, ItemDefinition> {
        val globalItemDefault = settings.defaultItemCapacity
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
                    material = displaySection.getString("material") ?: displaySection.getString("type"),
                    customModelData = displaySection.getInt("custom-model-data").takeIf {
                        displaySection.contains("custom-model-data")
                    },
                    autopickupOnCustomModelData = onSection?.getInt("custom-model-data"),
                    autopickupOffCustomModelData = offSection?.getInt("custom-model-data")
                )
            } else null
            ItemDefinition(
                id = id,
                categories = item.getStringList("categories"),
                defaultCapacity = resolveCapacity(item, "default-capacity", globalItemDefault),
                display = display
            )
        }
    }

    /**
     * Uses [globalDefault] when the section does not define [key]; otherwise uses the configured value.
     */
    private fun resolveCapacity(section: org.bukkit.configuration.ConfigurationSection, key: String, globalDefault: Int): Int {
        if (!section.contains(key)) return globalDefault
        return section.getInt(key)
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
            preventThrownPickup = true,
            defaultItemLore = emptyList(),
            defaultItemCapacity = PluginSettings.DEFAULT_ITEM_CAPACITY,
            defaultCategoryCapacity = PluginSettings.DEFAULT_CATEGORY_CAPACITY,
            defaultGlobalCapacity = PluginSettings.DEFAULT_GLOBAL_CAPACITY,
            autopickupEnchantGlow = true,
        )
    }
}
