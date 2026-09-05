package com.sneakybagofholding.listener

import com.sneakybagofholding.SneakyBagOfHolding
import com.sneakybagofholding.registry.ItemRegistry
import com.sneakybagofholding.service.BagService
import com.sneakybagofholding.util.ItemStackStorage
import org.bukkit.Bukkit
import org.bukkit.GameMode
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent

/**
 * Refills the placing hand from bag storage after a configured magic item block is placed.
 *
 * Requires Autoloot enabled for the item, at least [MIN_HAND_AMOUNT] in hand before place,
 * and proof that the placing hand lost exactly one of that magic item stack (filters
 * MagicSpells spell placements that fire [BlockPlaceEvent] without consuming the held item).
 *
 * Consumption is checked on the next tick because Paper decrements the hand only after
 * [BlockPlaceEvent] handlers finish.
 */
class BlockPlaceAutoRefillListener(
    private val itemRegistry: ItemRegistry,
    private val bagService: BagService,
) : Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onBlockPlace(event: BlockPlaceEvent) {
        val player = event.player
        if (player.gameMode == GameMode.CREATIVE || player.gameMode == GameMode.SPECTATOR) return

        val itemInHand = event.itemInHand
        if (!ItemStackStorage.isStorable(itemInHand)) return

        val itemId = itemRegistry.resolveItemId(itemInHand) ?: return
        val hand = event.hand
        val handStack = player.inventory.getItem(hand)
        val amountBefore = if (handStack.type.isAir) itemInHand.amount else handStack.amount
        if (amountBefore < MIN_HAND_AMOUNT) return
        if (!bagService.isAutopickupEnabled(player, itemId)) return
        if (bagService.getStoredAmount(player, itemId) <= 0) return

        val uuid = player.uniqueId
        Bukkit.getScheduler().runTask(SneakyBagOfHolding.instance, Runnable {
            val online = Bukkit.getPlayer(uuid) ?: return@Runnable
            if (online.gameMode == GameMode.CREATIVE || online.gameMode == GameMode.SPECTATOR) return@Runnable
            if (!bagService.isAutopickupEnabled(online, itemId)) return@Runnable
            if (bagService.getStoredAmount(online, itemId) <= 0) return@Runnable

            val currentStack = online.inventory.getItem(hand)
            val amountAfter = if (currentStack.type.isAir) 0 else currentStack.amount
            // Vanilla/Paper consume after BlockPlaceEvent; next tick should show -1.
            if (amountBefore - amountAfter != 1) return@Runnable

            bagService.withdrawToHand(online, itemId, hand)
        })
    }

    companion object {
        /** Minimum stack size in the placing hand before placement for auto-refill to apply. */
        const val MIN_HAND_AMOUNT = 50
    }
}
