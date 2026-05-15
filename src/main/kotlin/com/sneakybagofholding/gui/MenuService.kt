package com.sneakybagofholding.gui

import com.sneakybagofholding.SneakyBagOfHolding
import com.sneakybagofholding.capacity.CapacityService
import com.sneakybagofholding.config.CategoryDefinition
import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.config.ItemDefinition
import com.sneakybagofholding.registry.ItemRegistry
import com.sneakybagofholding.registry.MagicItemResolver
import com.sneakybagofholding.service.BagService
import com.sneakybagofholding.storage.PlayerDataStore
import com.sneakybagofholding.util.ItemMetaText
import com.sneakybagofholding.util.TextUtility
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.ClickType
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.ItemStack
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Main hub and category browser GUIs with deposit/withdraw/autopickup interactions.
 */
class MenuService(
    private val configManager: ConfigManager,
    private val itemRegistry: ItemRegistry,
    private val magicItemResolver: MagicItemResolver,
    private val capacityService: CapacityService,
    private val bagService: BagService,
    private val playerDataStore: PlayerDataStore
) : Listener {

    private val openMenus = ConcurrentHashMap<UUID, BagInventoryHolder>()
    private val categoryNavigation by lazy { CategoryNavigation(configManager) }
    private val itemDisplayBuilder by lazy {
        ItemDisplayBuilder(
            configManager,
            magicItemResolver,
            playerDataStore
        ) { player, itemId -> capacityService.effectiveMax(player, itemId) }
    }
    private val plugin get() = SneakyBagOfHolding.instance

    fun openMainMenu(player: Player) {
        val settings = configManager.getSettings()
        val rows = 6
        val holder = BagInventoryHolder.MainMenu()
        val inv = Bukkit.createInventory(holder, rows * 9, TextUtility.convertToComponent(settings.mainMenuTitle))
        holder.bind(inv)
        populateMainMenu(inv, player)
        openMenus[player.uniqueId] = holder
        player.openInventory(inv)
    }

    fun openCategoryMenu(player: Player, categoryId: String, pageIndex: Int = 0) {
        val category = configManager.getCategories()[categoryId] ?: return
        if (!category.isBrowsable) return
        val pages = categoryNavigation.pageCount(categoryId)
        val safePage = pageIndex.coerceIn(0, pages - 1)
        val rows = configManager.getSettings().categoryRows
        val holder = BagInventoryHolder.CategoryMenu(categoryId, safePage)
        val title = categoryNavigation.inventoryTitle(category, safePage)
        val inv = Bukkit.createInventory(holder, rows * 9, TextUtility.convertToComponent(title))
        holder.bind(inv)
        populateCategoryMenu(inv, player, category, safePage)
        openMenus[player.uniqueId] = holder
        player.openInventory(inv)
    }

    fun closeAllMenus() {
        for (uuid in openMenus.keys.toList()) {
            val player = Bukkit.getPlayer(uuid) ?: continue
            player.closeInventory()
        }
        openMenus.clear()
    }

    fun refreshOpenMenu(player: Player) {
        val holder = openMenus[player.uniqueId] ?: return
        val top = player.openInventory.topInventory
        when (holder) {
            is BagInventoryHolder.MainMenu -> populateMainMenu(top, player)
            is BagInventoryHolder.CategoryMenu -> {
                val cat = configManager.getCategories()[holder.categoryId] ?: return
                populateCategoryMenu(top, player, cat, holder.pageIndex)
                player.updateInventory()
            }
        }
    }

    /**
     * Updates a single category row after deposit, withdraw, or autopickup toggle.
     */
    private fun refreshCategoryItem(player: Player, categoryId: String, itemId: String) {
        val holder = openMenus[player.uniqueId] as? BagInventoryHolder.CategoryMenu ?: return
        if (holder.categoryId != categoryId) return
        val inv = player.openInventory.topInventory
        val items = categoryNavigation.itemsInCategory(categoryId)
        val globalIndex = items.indexOfFirst { it.id == itemId }
        if (globalIndex < 0) return
        val pageStart = holder.pageIndex * CategoryMenuLayout.ITEMS_PER_PAGE
        val slot = globalIndex - pageStart
        if (slot !in 0..CategoryMenuLayout.LAST_ITEM_SLOT) return
        inv.setItem(slot, buildItemDisplay(player, items[globalIndex]))
        player.updateInventory()
    }

    private fun scheduleCategoryRefresh(player: Player, categoryId: String, itemId: String) {
        Bukkit.getScheduler().runTask(plugin, Runnable {
            refreshCategoryItem(player, categoryId, itemId)
        })
    }

    private fun populateMainMenu(inv: Inventory, player: Player) {
        inv.clear()
        val categories = configManager.getBrowsableCategories()
        var slot = 0
        for (category in categories) {
            if (slot >= inv.size) break
            val icon = category.menuIcon?.clone() ?: continue
            inv.setItem(slot++, icon)
        }
    }

    private fun populateCategoryMenu(
        inv: Inventory,
        player: Player,
        category: CategoryDefinition,
        pageIndex: Int
    ) {
        inv.clear()
        val items = categoryNavigation.itemsInCategory(category.id)
        val pageStart = pageIndex * CategoryMenuLayout.ITEMS_PER_PAGE
        val pageEnd = minOf(pageStart + CategoryMenuLayout.ITEMS_PER_PAGE, items.size)
        for (globalIndex in pageStart until pageEnd) {
            val slot = globalIndex - pageStart
            inv.setItem(slot, buildItemDisplay(player, items[globalIndex]))
        }
        populateCategoryNavigation(inv, CategoryNavigation.ViewState(category.id, pageIndex))
    }

    private fun populateCategoryNavigation(inv: Inventory, view: CategoryNavigation.ViewState) {
        inv.setItem(CategoryMenuLayout.GAP_SLOT, null)

        inv.setItem(
            CategoryMenuLayout.PREV_TAB_SLOT,
            buildNavButton(
                Material.ARROW,
                "<yellow>Previous",
                categoryNavigation.prevButtonSubtitle(view)
            )
        )

        inv.setItem(
            CategoryMenuLayout.BACK_SLOT,
            buildNavButton(
                Material.OAK_DOOR,
                "<yellow>Back",
                "<gray>Main menu"
            )
        )

        inv.setItem(
            CategoryMenuLayout.NEXT_TAB_SLOT,
            buildNavButton(
                Material.ARROW,
                "<yellow>Next",
                categoryNavigation.nextButtonSubtitle(view)
            )
        )
    }

    private fun buildNavButton(material: Material, title: String, subtitle: String): ItemStack {
        val stack = ItemStack(material)
        val meta = stack.itemMeta ?: return stack
        ItemMetaText.setDisplayName(meta, title)
        ItemMetaText.setLore(meta, listOf(subtitle))
        stack.itemMeta = meta
        return stack
    }

    fun buildItemDisplay(player: Player, item: ItemDefinition): ItemStack =
        itemDisplayBuilder.build(player, item)

    private fun itemIdAtSlot(categoryId: String, pageIndex: Int, slot: Int): String? {
        val globalIndex = CategoryMenuLayout.globalItemIndex(pageIndex, slot) ?: return null
        return categoryNavigation.itemsInCategory(categoryId).getOrNull(globalIndex)?.id
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryClick(event: InventoryClickEvent) {
        val holder = event.inventory.holder as? BagInventoryHolder ?: return
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player
        event.isCancelled = true

        when (holder) {
            is BagInventoryHolder.MainMenu -> handleMainClick(event, player, holder)
            is BagInventoryHolder.CategoryMenu -> handleCategoryClick(event, player, holder)
        }
    }

    private fun handleMainClick(event: InventoryClickEvent, player: Player, holder: BagInventoryHolder.MainMenu) {
        if (tryShiftDeposit(event, player, holder)) return
        if (tryCursorDeposit(event, player, holder) { true }) return

        if (event.clickedInventory == event.view.topInventory) {
            val slot = event.rawSlot
            val categories = configManager.getBrowsableCategories()
            val category = categories.getOrNull(slot) ?: return
            Bukkit.getScheduler().runTask(plugin, Runnable { openCategoryMenu(player, category.id) })
        }
    }

    /** Shift-click from player inventory into the bag. */
    private fun tryShiftDeposit(
        event: InventoryClickEvent,
        player: Player,
        holder: BagInventoryHolder,
    ): Boolean {
        if (event.clickedInventory != event.view.bottomInventory || !event.isShiftClick) return false
        val stack = event.currentItem ?: return false
        val itemId = itemRegistry.resolveItemId(stack) ?: return false
        val deposited = bagService.depositFromStack(player, stack)
        if (deposited > 0) refreshAfterDeposit(player, holder, itemId)
        return deposited > 0
    }

    /** Place held stack into the top inventory (any non-navigation slot). */
    private fun tryCursorDeposit(
        event: InventoryClickEvent,
        player: Player,
        holder: BagInventoryHolder,
        allowTopSlot: (Int) -> Boolean = { !CategoryMenuLayout.isNavigationSlot(it) },
    ): Boolean {
        val cursor = event.cursor ?: return false
        if (cursor.type.isAir || event.clickedInventory != event.view.topInventory) return false
        if (!allowTopSlot(event.rawSlot)) return false
        val itemId = itemRegistry.resolveItemId(cursor) ?: return false
        val deposited = bagService.deposit(player, itemId, cursor.amount)
        if (deposited <= 0) return false
        cursor.amount -= deposited
        event.view.setCursor(if (cursor.amount > 0) cursor else ItemStack(Material.AIR))
        refreshAfterDeposit(player, holder, itemId)
        return true
    }

    private fun refreshAfterDeposit(player: Player, holder: BagInventoryHolder, itemId: String) {
        when (holder) {
            is BagInventoryHolder.MainMenu -> refreshOpenMenu(player)
            is BagInventoryHolder.CategoryMenu ->
                scheduleCategoryRefresh(player, holder.categoryId, itemId)
        }
    }

    private fun handleCategoryClick(event: InventoryClickEvent, player: Player, holder: BagInventoryHolder.CategoryMenu) {
        if (tryShiftDeposit(event, player, holder)) return
        if (tryCursorDeposit(event, player, holder)) return

        if (event.clickedInventory != event.view.topInventory) return
        val slot = event.rawSlot

        val view = CategoryNavigation.ViewState(holder.categoryId, holder.pageIndex)

        when (slot) {
            CategoryMenuLayout.PREV_TAB_SLOT -> {
                val target = categoryNavigation.resolvePrevious(view) ?: return
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    openCategoryMenu(player, target.categoryId, target.pageIndex)
                })
                return
            }
            CategoryMenuLayout.BACK_SLOT -> {
                Bukkit.getScheduler().runTask(plugin, Runnable { openMainMenu(player) })
                return
            }
            CategoryMenuLayout.NEXT_TAB_SLOT -> {
                val target = categoryNavigation.resolveNext(view) ?: return
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    openCategoryMenu(player, target.categoryId, target.pageIndex)
                })
                return
            }
            CategoryMenuLayout.GAP_SLOT -> return
        }

        if (!CategoryMenuLayout.isItemSlot(slot)) return

        val itemId = itemIdAtSlot(holder.categoryId, holder.pageIndex, slot) ?: return
        val categoryId = holder.categoryId
        val click = event.click
        when {
            click == ClickType.SWAP_OFFHAND || click == ClickType.NUMBER_KEY && event.hotbarButton == 40 -> {
                bagService.toggleAutopickup(player, itemId)
                scheduleCategoryRefresh(player, categoryId, itemId)
            }
            click == ClickType.LEFT -> {
                bagService.withdraw(player, itemId, 1)
                scheduleCategoryRefresh(player, categoryId, itemId)
            }
            click == ClickType.SHIFT_LEFT -> {
                bagService.withdraw(player, itemId, 99)
                scheduleCategoryRefresh(player, categoryId, itemId)
            }
            click == ClickType.RIGHT -> {
                bagService.deposit(player, itemId, 1)
                scheduleCategoryRefresh(player, categoryId, itemId)
            }
            click == ClickType.SHIFT_RIGHT -> {
                bagService.deposit(player, itemId, 99)
                scheduleCategoryRefresh(player, categoryId, itemId)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.inventory.holder as? BagInventoryHolder ?: return
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player

        if (holder is BagInventoryHolder.CategoryMenu) {
            val onNav = event.rawSlots.any { CategoryMenuLayout.isNavigationSlot(it) }
            if (onNav) {
                event.isCancelled = true
                return
            }
        }

        val topSlots = event.rawSlots.filter { it < event.view.topInventory.size }
        if (topSlots.isEmpty()) return
        event.isCancelled = true
        val cursor = event.oldCursor.clone()
        if (cursor.type.isAir) return
        val itemId = itemRegistry.resolveItemId(cursor) ?: return
        val deposited = bagService.deposit(player, itemId, cursor.amount)
        if (deposited > 0) {
            cursor.amount -= deposited
            event.view.setCursor(if (cursor.amount > 0) cursor else ItemStack(Material.AIR))
            refreshAfterDeposit(player, holder, itemId)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (event.inventory.holder is BagInventoryHolder) {
            openMenus.remove(player.uniqueId)
        }
    }
}
