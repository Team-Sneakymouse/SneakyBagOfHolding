package com.sneakybagofholding.gui

import com.sneakybagofholding.capacity.CapacityService
import com.sneakybagofholding.config.CategoryDefinition
import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.config.ItemDefinition
import com.sneakybagofholding.registry.ItemRegistry
import com.sneakybagofholding.registry.MagicItemResolver
import com.sneakybagofholding.service.BagService
import com.sneakybagofholding.storage.PlayerDataStore
import com.sneakybagofholding.util.ItemMetaText
import com.sneakybagofholding.util.ItemStackParser
import com.sneakybagofholding.util.TextUtility
import org.bukkit.Material
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
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

    fun openCategoryMenu(player: Player, categoryId: String) {
        val category = configManager.getCategories()[categoryId] ?: return
        if (!category.isBrowsable) return
        val rows = configManager.getSettings().categoryRows
        val holder = BagInventoryHolder.CategoryMenu(categoryId)
        val inv = Bukkit.createInventory(
            holder,
            rows * 9,
            TextUtility.convertToComponent(category.menuTitle)
        )
        holder.bind(inv)
        populateCategoryMenu(inv, player, category)
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
                populateCategoryMenu(top, player, cat)
            }
        }
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

    private fun populateCategoryMenu(inv: Inventory, player: Player, category: CategoryDefinition) {
        inv.clear()
        val items = configManager.getItemsInCategory(category.id)
        var slot = 0
        for (item in items) {
            if (slot >= inv.size) break
            inv.setItem(slot++, buildItemDisplay(player, item))
        }
    }

    fun buildItemDisplay(player: Player, item: ItemDefinition): ItemStack {
        val display = item.display
        val base = magicItemResolver.createItem(item.id, 1) ?: buildFallbackStack(item, display)
        val meta = base.itemMeta ?: return base

        val data = playerDataStore.get(player)
        val stored = data.getStored(item.id)
        val max = capacityService.effectiveMax(player, item.id)
        val autopickup = data.isAutopickupEnabled(item.id)

        // Optional CMD overlay for autopickup on/off (only when configured)
        val cmdOverride = if (autopickup) {
            display?.autopickupOnCustomModelData
        } else {
            display?.autopickupOffCustomModelData
        }
        if (cmdOverride != null) {
            meta.setCustomModelData(cmdOverride)
        }

        display?.name?.let { ItemMetaText.setDisplayName(meta, it) }

        val loreTemplate = if (display?.lore.isNullOrEmpty()) {
            configManager.getSettings().defaultItemLore
        } else {
            display!!.lore
        }
        val autopickupStatus = if (autopickup) "<green>[On]" else "<red>[Off]"
        ItemMetaText.setLore(
            meta,
            loreTemplate.map { line ->
                line.replace("{stored}", stored.toString())
                    .replace("{max}", max.toString())
                    .replace("{autoloot_status}", autopickupStatus)
                    .replace("{item_id}", item.id)
            }
        )

        base.itemMeta = meta
        return base
    }

    /**
     * Builds a display stack from config when MagicSpells items are unavailable.
     */
    private fun buildFallbackStack(item: ItemDefinition, display: com.sneakybagofholding.config.ItemDisplayDefinition?): ItemStack {
        val materialName = display?.material ?: "PAPER"
        val material = Material.matchMaterial(materialName.uppercase()) ?: Material.PAPER
        val stack = ItemStack(material)
        val cmd = display?.customModelData
        if (cmd != null) {
            ItemStackParser.applyCustomModelData(stack, cmd)
        }
        return stack
    }

    private fun itemIdAtSlot(inv: Inventory, slot: Int, categoryId: String): String? {
        val items = configManager.getItemsInCategory(categoryId)
        return items.getOrNull(slot)?.id
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
        val cursor = event.cursor
        if (cursor != null && !cursor.type.isAir && event.clickedInventory == event.view.topInventory) {
            tryDepositCursor(event, player)
            return
        }
        if (event.clickedInventory == event.view.topInventory) {
            val slot = event.rawSlot
            val categories = configManager.getBrowsableCategories()
            val category = categories.getOrNull(slot) ?: return
            Bukkit.getScheduler().runTask(
                com.sneakybagofholding.SneakyBagOfHolding.instance,
                Runnable { openCategoryMenu(player, category.id) }
            )
            return
        }
        if (event.clickedInventory == event.view.bottomInventory && event.isShiftClick) {
            val stack = event.currentItem ?: return
            val deposited = bagService.depositFromStack(player, stack)
            if (deposited > 0) refreshOpenMenu(player)
        }
    }

    private fun tryDepositCursor(event: InventoryClickEvent, player: Player) {
        val cursor = event.cursor?.clone() ?: return
        val itemId = itemRegistry.resolveItemId(cursor) ?: return
        val deposited = bagService.deposit(player, itemId, cursor.amount)
        if (deposited > 0) {
            cursor.amount -= deposited
            event.view.setCursor(if (cursor.amount > 0) cursor else ItemStack(org.bukkit.Material.AIR))
            refreshOpenMenu(player)
        }
    }

    private fun handleCategoryClick(event: InventoryClickEvent, player: Player, holder: BagInventoryHolder.CategoryMenu) {
        if (event.clickedInventory != event.view.topInventory) return
        val slot = event.rawSlot
        val itemId = itemIdAtSlot(event.inventory, slot, holder.categoryId) ?: return
        val click = event.click
        when {
            click == ClickType.SWAP_OFFHAND || click == ClickType.NUMBER_KEY && event.hotbarButton == 40 -> {
                bagService.toggleAutopickup(player, itemId)
                refreshOpenMenu(player)
            }
            click == ClickType.LEFT -> {
                bagService.withdraw(player, itemId, 1)
                refreshOpenMenu(player)
            }
            click == ClickType.SHIFT_LEFT -> {
                bagService.withdraw(player, itemId, 99)
                refreshOpenMenu(player)
            }
            click == ClickType.RIGHT -> {
                bagService.deposit(player, itemId, 1)
                refreshOpenMenu(player)
            }
            click == ClickType.SHIFT_RIGHT -> {
                bagService.deposit(player, itemId, 99)
                refreshOpenMenu(player)
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onInventoryDrag(event: InventoryDragEvent) {
        val holder = event.inventory.holder as? BagInventoryHolder ?: return
        if (event.whoClicked !is Player) return
        val player = event.whoClicked as Player
        val topSlots = event.rawSlots.filter { it < event.view.topInventory.size }
        if (topSlots.isEmpty()) return
        event.isCancelled = true
        val cursor = event.oldCursor.clone()
        if (cursor.type.isAir) return
        val itemId = itemRegistry.resolveItemId(cursor) ?: return
        val deposited = bagService.deposit(player, itemId, cursor.amount)
        if (deposited > 0) {
            cursor.amount -= deposited
            event.view.setCursor(if (cursor.amount > 0) cursor else ItemStack(org.bukkit.Material.AIR))
            refreshOpenMenu(player)
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
