package com.sneakybagofholding.service

import com.sneakybagofholding.capacity.CapacityService
import com.sneakybagofholding.registry.ItemRegistry
import com.sneakybagofholding.registry.MagicItemResolver
import com.sneakybagofholding.storage.PlayerDataStore
import com.sneakybagofholding.util.PickupSounds
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.util.UUID

/**
 * Deposit, withdraw, autopickup, and admin capacity operations for the bag of holding.
 */
class BagService(
    private val itemRegistry: ItemRegistry,
    private val magicItemResolver: MagicItemResolver,
    private val capacityService: CapacityService,
    private val playerDataStore: PlayerDataStore
) {

    /**
     * Deposits up to [amount] of [itemId] from the player's inventory into storage.
     * @return amount actually deposited
     */
    fun deposit(player: Player, itemId: String, amount: Int): Int {
        if (amount <= 0 || !itemRegistry.isRegistered(itemId)) return 0
        val remaining = capacityService.remaining(player, itemId)
        if (remaining <= 0) return 0
        val toDeposit = amount.coerceAtMost(remaining)
        val removed = removeFromInventory(player, itemId, toDeposit)
        if (removed <= 0) return 0
        val data = playerDataStore.get(player)
        data.setStored(itemId, data.getStored(itemId) + removed)
        playerDataStore.markDirty(player)
        return removed
    }

    /**
     * Withdraws up to [amount] of [itemId] into the player's inventory.
     * @return amount actually withdrawn
     */
    fun withdraw(player: Player, itemId: String, amount: Int): Int {
        if (amount <= 0 || !itemRegistry.isRegistered(itemId)) return 0
        val data = playerDataStore.get(player)
        val stored = data.getStored(itemId)
        if (stored <= 0) return 0
        val toWithdraw = amount.coerceAtMost(stored)
        val given = giveToInventory(player, itemId, toWithdraw)
        if (given <= 0) return 0
        data.setStored(itemId, stored - given)
        playerDataStore.markDirty(player)
        return given
    }

    fun toggleAutopickup(player: Player, itemId: String): Boolean {
        if (!itemRegistry.isRegistered(itemId)) return false
        val data = playerDataStore.get(player)
        val newValue = !data.isAutopickupEnabled(itemId)
        data.setAutopickup(itemId, newValue)
        playerDataStore.markDirty(player)
        return newValue
    }

    /**
     * Adds directly to stored count (admin), clamped to capacity.
     */
    fun adminSetStored(player: Player, itemId: String, amount: Int): Int {
        if (!itemRegistry.isRegistered(itemId)) return 0
        val clamped = amount.coerceIn(0, capacityService.effectiveMax(player, itemId))
        playerDataStore.get(player).setStored(itemId, clamped)
        playerDataStore.markDirty(player)
        return clamped
    }

    fun setItemCapacityOverride(uuid: UUID, itemId: String, value: Int) {
        playerDataStore.get(uuid).itemCapacity[itemId] = value
        playerDataStore.markDirty(uuid)
    }

    fun setCategoryCapacityOverride(uuid: UUID, categoryId: String, value: Int) {
        playerDataStore.get(uuid).categoryCapacity[categoryId] = value
        playerDataStore.markDirty(uuid)
    }

    fun setGlobalCapacityOverride(uuid: UUID, value: Int) {
        playerDataStore.get(uuid).globalCapacity = value
        playerDataStore.markDirty(uuid)
    }

    /**
     * Deposits from a specific inventory stack (e.g. menu drag-drop).
     */
    fun depositFromStack(player: Player, stack: ItemStack): Int {
        val itemId = itemRegistry.resolveItemId(stack) ?: return 0
        val amount = deposit(player, itemId, stack.amount)
        if (amount > 0) {
            stack.amount -= amount
            if (stack.amount <= 0) {
                player.inventory.remove(stack)
            }
        }
        return amount
    }

    /**
     * Auto-pickup: adds [pickupAmount] to storage if enabled and room exists.
     * @return amount absorbed into the bag
     */
    fun absorbPickup(player: Player, itemId: String, pickupAmount: Int, suppressSound: Boolean): Int {
        val data = playerDataStore.get(player)
        if (!data.isAutopickupEnabled(itemId)) return 0
        val remaining = capacityService.remaining(player, itemId)
        if (remaining <= 0) return 0
        val absorbed = pickupAmount.coerceAtMost(remaining)
        data.setStored(itemId, data.getStored(itemId) + absorbed)
        playerDataStore.markDirty(player)
        if (!suppressSound) {
            player.playSound(
                player.location,
                Sound.ENTITY_ITEM_PICKUP,
                0.3f,
                PickupSounds.randomItemPickupPitch(),
            )
        }
        return absorbed
    }

    private fun removeFromInventory(player: Player, itemId: String, maxAmount: Int): Int {
        var remaining = maxAmount
        val contents = player.inventory.contents ?: return 0
        for (i in contents.indices) {
            val stack = contents[i] ?: continue
            if (itemRegistry.resolveItemId(stack) != itemId) continue
            val take = minOf(stack.amount, remaining)
            stack.amount -= take
            if (stack.amount <= 0) contents[i] = null
            remaining -= take
            if (remaining <= 0) break
        }
        player.inventory.contents = contents
        return maxAmount - remaining
    }

    private fun giveToInventory(player: Player, itemId: String, maxAmount: Int): Int {
        var remaining = maxAmount
        while (remaining > 0) {
            val stackSize = minOf(remaining, 64)
            val stack = magicItemResolver.createItem(itemId, stackSize) ?: break
            val leftover = player.inventory.addItem(stack)
            val notAdded = leftover.values.firstOrNull()?.amount ?: 0
            val added = stackSize - notAdded
            remaining -= added
            if (added <= 0) break
        }
        return maxAmount - remaining
    }
}
