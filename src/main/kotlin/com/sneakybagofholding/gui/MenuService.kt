package com.sneakybagofholding.gui

import com.sneakybagofholding.SneakyBagOfHolding
import com.sneakybagofholding.capacity.CapacityService
import com.sneakybagofholding.config.CategoryDefinition
import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.config.ItemDefinition
import com.sneakybagofholding.config.MenuLayoutSettings
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
import org.bukkit.event.inventory.InventoryOpenEvent
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
        scheduleMenuRefresh(player, holder)
    }

    /**
     * Rebuilds the open category menu on the next tick (same approach as prev/next navigation)
     * so stored counts, lore, and autopickup icons sync on the client.
     */
    private fun scheduleMenuRefresh(player: Player, holder: BagInventoryHolder) {
        if (holder !is BagInventoryHolder.CategoryMenu) return
        val categoryId = holder.categoryId
        val pageIndex = holder.pageIndex
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            val topHolder = player.openInventory.topInventory.holder
            if (topHolder !is BagInventoryHolder) return@Runnable
            openCategoryMenu(player, categoryId, pageIndex)
        })
    }

    private fun populateMainMenu(inv: Inventory, player: Player) {
        inv.clear()
        val layout = configManager.getSettings().menuLayout
        layout.mainMenuDecorative?.let { deco ->
            inv.setItem(deco.slot, deco.item.clone())
        }

        val categories = configManager.getBrowsableCategories()
        val useExplicitSlots = layout.mainMenuCategorySlots.isNotEmpty() ||
            categories.any { it.mainMenuSlot != null }

        if (useExplicitSlots) {
            for (category in categories) {
                val slot = category.mainMenuSlot ?: layout.mainMenuCategorySlots[category.id] ?: continue
                if (slot !in 0 until inv.size) continue
                val icon = category.menuIcon?.clone() ?: continue
                inv.setItem(slot, icon)
            }
        } else {
            var slot = 0
            for (category in categories) {
                slot = nextMainMenuCategorySlot(slot, inv.size, layout)
                if (slot < 0) break
                val icon = category.menuIcon?.clone() ?: continue
                inv.setItem(slot, icon)
                slot++
            }
        }

        layout.hubFiller?.let { filler ->
            for (i in 0 until inv.size) {
                if (i != filler.openSlot && (inv.getItem(i) == null || inv.getItem(i)!!.type.isAir)) {
                    inv.setItem(i, filler.item.clone())
                }
            }
        }
    }

    private fun nextMainMenuCategorySlot(
        candidate: Int,
        inventorySize: Int,
        layout: MenuLayoutSettings,
    ): Int {
        var slot = candidate
        while (slot < inventorySize && isReservedMainMenuSlot(slot, layout)) {
            slot++
        }
        return if (slot < inventorySize) slot else -1
    }

    private fun isReservedMainMenuSlot(slot: Int, layout: MenuLayoutSettings): Boolean =
        layout.mainMenuDecorative?.slot == slot

    private fun categoryAtMainMenuSlot(slot: Int): CategoryDefinition? {
        val layout = configManager.getSettings().menuLayout
        val categories = configManager.getBrowsableCategories()
        val useExplicitSlots = layout.mainMenuCategorySlots.isNotEmpty() ||
            categories.any { it.mainMenuSlot != null }
        if (useExplicitSlots) {
            return categories.firstOrNull { category ->
                (category.mainMenuSlot ?: layout.mainMenuCategorySlots[category.id]) == slot
            }
        }
        if (isReservedMainMenuSlot(slot, layout)) return null
        var index = 0
        for (category in categories) {
            val categorySlot = nextMainMenuCategorySlot(index, 54, layout)
            if (categorySlot == slot) return category
            index = categorySlot + 1
        }
        return null
    }

    private fun populateCategoryMenu(
        inv: Inventory,
        player: Player,
        category: CategoryDefinition,
        pageIndex: Int
    ) {
        inv.clear()
        fillCategoryItemSlots(inv, player, category, pageIndex)
        populateCategoryNavigation(inv, category, CategoryNavigation.ViewState(category.id, pageIndex))
    }

    private fun fillCategoryItemSlots(
        inv: Inventory,
        player: Player,
        category: CategoryDefinition,
        pageIndex: Int,
    ) {
        val items = categoryNavigation.itemsInCategory(category.id)
        val pageStart = pageIndex * CategoryMenuLayout.ITEMS_PER_PAGE
        val pageEnd = minOf(pageStart + CategoryMenuLayout.ITEMS_PER_PAGE, items.size)
        for (globalIndex in pageStart until pageEnd) {
            val slot = globalIndex - pageStart
            inv.setItem(slot, buildItemDisplay(player, items[globalIndex]))
        }
    }

    /**
     * MagicSpells ItemTagSpell runs on [InventoryOpenEvent] and can replace display icons with
     * full magic items. Re-apply plugin-built icons on the next tick (after MS handlers).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onInventoryOpen(event: InventoryOpenEvent) {
        val holder = event.inventory.holder as? BagInventoryHolder ?: return
        val player = event.player as? Player ?: return
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (!player.isOnline) return@Runnable
            val top = player.openInventory.topInventory
            if (top.holder != holder) return@Runnable
            when (holder) {
                is BagInventoryHolder.CategoryMenu -> {
                    val category = configManager.getCategories()[holder.categoryId] ?: return@Runnable
                    fillCategoryItemSlots(top, player, category, holder.pageIndex)
                }
                is BagInventoryHolder.MainMenu -> populateMainMenu(top, player)
            }
        })
    }

    private fun populateCategoryNavigation(
        inv: Inventory,
        category: CategoryDefinition,
        view: CategoryNavigation.ViewState,
    ) {
        val decorative = configManager.resolveCategoryMenuDecorative(category)
        if (decorative != null) {
            inv.setItem(decorative.slot, decorative.item.clone())
        }

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
        val holder = event.view.topInventory.holder as? BagInventoryHolder ?: return
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player

        // Player inventory: allow normal clicks/drags; only intercept shift-click to deposit.
        if (event.clickedInventory == event.view.bottomInventory) {
            if (event.isShiftClick) {
                event.isCancelled = true
                tryShiftDeposit(event, player, holder)
            }
            return
        }

        if (event.clickedInventory != event.view.topInventory) return

        event.isCancelled = true
        when (holder) {
            is BagInventoryHolder.MainMenu -> handleMainTopClick(event, player, holder)
            is BagInventoryHolder.CategoryMenu -> handleCategoryTopClick(event, player, holder)
        }
    }

    private fun handleMainTopClick(event: InventoryClickEvent, player: Player, holder: BagInventoryHolder.MainMenu) {
        val hubDecoSlot = configManager.getSettings().menuLayout.mainMenuDecorative?.slot
        if (tryCursorDeposit(event, player, holder) { slot ->
            hubDecoSlot == null || slot != hubDecoSlot
        }) return

        val slot = event.rawSlot
        val category = categoryAtMainMenuSlot(slot) ?: return
        configManager.getSettings().audio.navigate?.play(player)
        Bukkit.getScheduler().runTask(plugin, Runnable { openCategoryMenu(player, category.id) })
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
        if (deposited > 0) {
            configManager.getSettings().audio.deposit?.play(player)
            refreshAfterDeposit(player, holder)
        }
        return deposited > 0
    }

    /** Place held stack into the top inventory (any non-navigation slot). */
    private fun tryCursorDeposit(
        event: InventoryClickEvent,
        player: Player,
        holder: BagInventoryHolder,
        allowTopSlot: (Int) -> Boolean = {
            !CategoryMenuLayout.isNavigationSlot(it, CategoryMenuLayout.DEFAULT_DECORATIVE_SLOT)
        },
    ): Boolean {
        val cursor = event.cursor ?: return false
        if (cursor.type.isAir || event.clickedInventory != event.view.topInventory) return false
        if (!allowTopSlot(event.rawSlot)) return false
        val cursorStack = cursor.clone()
        val deposited = bagService.depositFromCursor(player, cursorStack)
        if (deposited <= 0) return false
        configManager.getSettings().audio.deposit?.play(player)
        event.view.setCursor(if (cursorStack.amount > 0) cursorStack else ItemStack(Material.AIR))
        refreshAfterDeposit(player, holder)
        return true
    }

    private fun refreshAfterDeposit(player: Player, holder: BagInventoryHolder) {
        scheduleMenuRefresh(player, holder)
    }

    private fun handleCategoryTopClick(event: InventoryClickEvent, player: Player, holder: BagInventoryHolder.CategoryMenu) {
        val category = configManager.getCategories()[holder.categoryId] ?: return
        val decorativeSlot = configManager.resolveCategoryMenuDecorative(category)?.slot
            ?: CategoryMenuLayout.DEFAULT_DECORATIVE_SLOT

        if (tryCursorDeposit(event, player, holder) { !CategoryMenuLayout.isNavigationSlot(it, decorativeSlot) }) {
            return
        }

        val slot = event.rawSlot

        val view = CategoryNavigation.ViewState(holder.categoryId, holder.pageIndex)

        when (slot) {
            CategoryMenuLayout.PREV_TAB_SLOT -> {
                val target = categoryNavigation.resolvePrevious(view) ?: return
                configManager.getSettings().audio.navigate?.play(player)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    openCategoryMenu(player, target.categoryId, target.pageIndex)
                })
                return
            }
            CategoryMenuLayout.BACK_SLOT -> {
                configManager.getSettings().audio.navigate?.play(player)
                Bukkit.getScheduler().runTask(plugin, Runnable { openMainMenu(player) })
                return
            }
            CategoryMenuLayout.NEXT_TAB_SLOT -> {
                val target = categoryNavigation.resolveNext(view) ?: return
                configManager.getSettings().audio.navigate?.play(player)
                Bukkit.getScheduler().runTask(plugin, Runnable {
                    openCategoryMenu(player, target.categoryId, target.pageIndex)
                })
                return
            }
            decorativeSlot -> return
        }

        if (!CategoryMenuLayout.isItemSlot(slot)) return

        val itemId = itemIdAtSlot(holder.categoryId, holder.pageIndex, slot) ?: return
        val click = event.click
        when {
            click == ClickType.SWAP_OFFHAND || click == ClickType.NUMBER_KEY && event.hotbarButton == 40 -> {
                bagService.toggleAutopickup(player, itemId)
                configManager.getSettings().audio.toggleAutoloot?.play(player)
                scheduleMenuRefresh(player, holder)
            }
            click == ClickType.LEFT -> {
                val withdrawn = bagService.withdraw(player, itemId, 1)
                if (withdrawn > 0) configManager.getSettings().audio.withdraw?.play(player)
                scheduleMenuRefresh(player, holder)
            }
            click == ClickType.SHIFT_LEFT -> {
                val withdrawn = bagService.withdraw(player, itemId, 99)
                if (withdrawn > 0) configManager.getSettings().audio.withdraw?.play(player)
                scheduleMenuRefresh(player, holder)
            }
            click == ClickType.RIGHT -> {
                val deposited = bagService.deposit(player, itemId, 1)
                if (deposited > 0) configManager.getSettings().audio.deposit?.play(player)
                scheduleMenuRefresh(player, holder)
            }
            click == ClickType.SHIFT_RIGHT -> {
                val deposited = bagService.deposit(player, itemId, 99)
                if (deposited > 0) configManager.getSettings().audio.deposit?.play(player)
                scheduleMenuRefresh(player, holder)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.view.topInventory.holder as? BagInventoryHolder ?: return
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player

        val topSize = event.view.topInventory.size
        val topSlots = event.rawSlots.filter { it < topSize }
        if (topSlots.isEmpty()) return

        if (holder is BagInventoryHolder.CategoryMenu) {
            val category = configManager.getCategories()[holder.categoryId]
            val decorativeSlot = category?.let { configManager.resolveCategoryMenuDecorative(it)?.slot }
                ?: CategoryMenuLayout.DEFAULT_DECORATIVE_SLOT
            val onNav = topSlots.any { CategoryMenuLayout.isNavigationSlot(it, decorativeSlot) }
            if (onNav) {
                event.isCancelled = true
                return
            }
        }

        event.isCancelled = true
        val cursor = event.oldCursor.clone()
        if (cursor.type.isAir) return
        val deposited = bagService.depositFromCursor(player, cursor)
        if (deposited > 0) {
            configManager.getSettings().audio.deposit?.play(player)
            event.view.setCursor(if (cursor.amount > 0) cursor else ItemStack(Material.AIR))
            refreshAfterDeposit(player, holder)
        }
    }

    @EventHandler
    fun onInventoryClose(event: InventoryCloseEvent) {
        val player = event.player as? Player ?: return
        if (event.inventory.holder !is BagInventoryHolder) return
        // Defer: replacing one BOH screen with another also closes the old inventory first.
        Bukkit.getScheduler().runTask(plugin, Runnable {
            if (player.openInventory.topInventory.holder !is BagInventoryHolder) {
                openMenus.remove(player.uniqueId)
            }
        })
    }
}
