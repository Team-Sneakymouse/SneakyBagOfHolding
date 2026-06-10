package com.sneakybagofholding.listener

import com.sneakybagofholding.SneakyBagOfHolding
import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.registry.ItemRegistry
import com.sneakybagofholding.service.BagService
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.Particle
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
        val itemEntity = event.item

        val settings = configManager.getSettings()
        if (settings.preventThrownPickup) {
            val thrower = itemEntity.thrower
            if (thrower != null && thrower != player.uniqueId) {
                return
            }
        }

        val item = itemEntity.itemStack
        if (itemRegistry.resolveItemId(item) == null) return
        val amount = item.amount
        val absorbed = bagService.absorbPickup(player, item)
        if (absorbed <= 0) return
        
        settings.audio.pickup?.play(player)
        player.spawnParticle(Particle.WITCH, itemEntity.location.add(0.0, 0.5, 0.0), 5, 0.1, 0.1, 0.1, 0.1)
        
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
