package com.sneakybagofholding.listener

import com.sneakybagofholding.SneakyBagOfHolding
import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.registry.ItemRegistry
import com.sneakybagofholding.service.BagService
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityPickupItemEvent
import org.bukkit.inventory.ItemStack

/**
 * Converts world item pickups into bag storage when per-item autopickup is enabled.
 */
class AutoPickupListener(
    private val configManager: ConfigManager,
    private val itemRegistry: ItemRegistry,
    private val bagService: BagService
) : Listener {

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    fun onEntityPickupItem(event: EntityPickupItemEvent) {
        val player = event.entity as? Player ?: return
        val item = event.item.itemStack
        val itemId = itemRegistry.resolveItemId(item) ?: return
        val suppressSound = configManager.getSettings().suppressAutopickupSound
        val amount = item.amount
        val absorbed = bagService.absorbPickup(player, itemId, amount, suppressSound)
        if (absorbed <= 0) return
        val remaining = amount - absorbed
        if (remaining <= 0) {
            event.item.remove()
            event.isCancelled = true
        } else {
            event.item.itemStack = ItemStack(item.type, remaining).apply {
                itemMeta = item.itemMeta
            }
        }
    }
}
