package com.sneakybagofholding.service

import com.sneakybagofholding.api.WithdrawFailureReason
import com.sneakybagofholding.api.WithdrawResult
import com.sneakybagofholding.capacity.CapacityService
import com.sneakybagofholding.registry.ItemRegistry
import com.sneakybagofholding.registry.MagicItemResolver
import com.sneakybagofholding.storage.PlayerDataStore
import com.sneakybagofholding.util.ItemStackStorage
import com.sneakybagofholding.util.SoulboundTag
import org.bukkit.Material
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

    /** Max stack size for [itemId], from the MagicSpells item or config fallback material. */
    fun maxStackSize(itemId: String): Int = prototypeStack(itemId)?.maxStackSize ?: 64

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

    /** Read-only stored count for [itemId]; returns `0` when the item is not registered. */
    fun getStoredAmount(player: Player, itemId: String): Int {
        if (!itemRegistry.isRegistered(itemId)) return 0
        return capacityService.getStored(player.uniqueId, itemId)
    }

    /**
     * Resolves [stack] to a configured item id when the stack is storable and registered.
     */
    fun resolveItemIdForApi(stack: ItemStack): String? {
        if (stack.amount <= 0 || !ItemStackStorage.isStorable(stack)) return null
        return itemRegistry.resolveItemId(stack)
    }

    fun isAutopickupEnabled(player: Player, itemId: String): Boolean {
        if (!itemRegistry.isRegistered(itemId)) return false
        return playerDataStore.get(player).isAutopickupEnabled(itemId)
    }

    fun getRemainingCapacity(player: Player, itemId: String): Int {
        if (!itemRegistry.isRegistered(itemId)) return 0
        return capacityService.remaining(player, itemId)
    }

    /**
     * Adds up to [amount] of [itemId] directly into storage without touching inventory.
     * @return amount actually deposited
     */
    fun depositDirect(player: Player, itemId: String, amount: Int): Int {
        if (amount <= 0 || !itemRegistry.isRegistered(itemId)) return 0
        val remaining = capacityService.remaining(player, itemId)
        if (remaining <= 0) return 0
        val toDeposit = amount.coerceAtMost(remaining)
        val data = playerDataStore.get(player)
        data.setStored(itemId, data.getStored(itemId) + toDeposit)
        playerDataStore.markDirty(player)
        return toDeposit
    }

    /**
     * Withdraws exactly [amount] of [itemId] as an [ItemStack] without touching player inventory.
     * All-or-nothing: storage is unchanged unless the full amount can be produced.
     */
    fun withdrawAsItemStack(player: Player, itemId: String, amount: Int): WithdrawResult {
        if (amount <= 0) return WithdrawResult.Failure(WithdrawFailureReason.INVALID_AMOUNT)
        if (!itemRegistry.isRegistered(itemId)) {
            return WithdrawResult.Failure(WithdrawFailureReason.UNKNOWN_ITEM)
        }
        val data = playerDataStore.get(player)
        val stored = data.getStored(itemId)
        if (stored < amount) return WithdrawResult.Failure(WithdrawFailureReason.INSUFFICIENT_STORED)
        val stack = createItemStack(itemId, amount, player)
            ?: return WithdrawResult.Failure(WithdrawFailureReason.ITEM_CREATION_FAILED)
        data.setStored(itemId, stored - amount)
        playerDataStore.markDirty(player)
        return WithdrawResult.Success(stack)
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
     * Deposits from a specific stack (shift-clicked slot). Does not scan the rest of the inventory.
     */
    fun depositFromStack(player: Player, stack: ItemStack): Int {
        if (stack.amount <= 0 || !ItemStackStorage.isStorable(stack)) return 0
        val itemId = itemRegistry.resolveItemId(stack) ?: return 0
        return addToStoredFromStack(player, itemId, stack)
    }

    /**
     * Deposits from the item on the player's cursor (click or drag onto the menu).
     */
    fun depositFromCursor(player: Player, cursor: ItemStack): Int {
        if (cursor.type.isAir || !ItemStackStorage.isStorable(cursor)) return 0
        val itemId = itemRegistry.resolveItemId(cursor) ?: return 0
        return addToStoredFromStack(player, itemId, cursor)
    }

    private fun addToStoredFromStack(player: Player, itemId: String, stack: ItemStack): Int {
        if (!itemRegistry.isRegistered(itemId)) return 0
        val remaining = capacityService.remaining(player, itemId)
        if (remaining <= 0) return 0
        val toDeposit = minOf(stack.amount, remaining)
        stack.amount -= toDeposit
        val data = playerDataStore.get(player)
        data.setStored(itemId, data.getStored(itemId) + toDeposit)
        playerDataStore.markDirty(player)
        return toDeposit
    }

    /**
     * Auto-pickup: adds items from [stack] to storage if enabled and room exists.
     * @return amount absorbed into the bag
     */
    fun absorbPickup(player: Player, stack: ItemStack): Int {
        if (!ItemStackStorage.isStorable(stack)) return 0
        val itemId = itemRegistry.resolveItemId(stack) ?: return 0
        val pickupAmount = stack.amount
        val data = playerDataStore.get(player)
        if (!data.isAutopickupEnabled(itemId)) return 0
        val remaining = capacityService.remaining(player, itemId)
        if (remaining <= 0) return 0
        val absorbed = pickupAmount.coerceAtMost(remaining)
        data.setStored(itemId, data.getStored(itemId) + absorbed)
        playerDataStore.markDirty(player)
        return absorbed
    }

    private fun removeFromInventory(player: Player, itemId: String, maxAmount: Int): Int {
        var remaining = maxAmount
        val contents = player.inventory.contents ?: return 0
        for (i in contents.indices) {
            val stack = contents[i] ?: continue
            if (itemRegistry.resolveItemId(stack) != itemId) continue
            if (!ItemStackStorage.isStorable(stack)) continue
            val take = minOf(stack.amount, remaining)
            stack.amount -= take
            if (stack.amount <= 0) contents[i] = null
            remaining -= take
            if (remaining <= 0) break
        }
        player.inventory.contents = contents
        return maxAmount - remaining
    }

    private fun prototypeStack(itemId: String): ItemStack? = createItemStack(itemId, 1, null)

    private fun createItemStack(itemId: String, amount: Int, owner: Player? = null): ItemStack? {
        val stack = magicItemResolver.createItem(itemId, amount)
            ?: run {
                val display = itemRegistry.getItem(itemId)?.display ?: return null
                val material = Material.matchMaterial((display.material ?: "PAPER").uppercase()) ?: Material.PAPER
                ItemStack(material, amount)
            }
        applySoulboundIfConfigured(stack, itemId, owner)
        return stack
    }

    private fun applySoulboundIfConfigured(stack: ItemStack, itemId: String, owner: Player?) {
        if (owner == null) return
        if (itemRegistry.getItem(itemId)?.soulbound != true) return
        val meta = stack.itemMeta ?: return
        SoulboundTag.apply(meta, owner.uniqueId)
        stack.itemMeta = meta
    }

    private fun giveToInventory(player: Player, itemId: String, maxAmount: Int): Int {
        var remaining = maxAmount
        while (remaining > 0) {
            val maxStack = prototypeStack(itemId)?.maxStackSize ?: break
            val stackSize = minOf(remaining, maxStack)
            val stack = createItemStack(itemId, stackSize, player) ?: break
            val leftover = player.inventory.addItem(stack)
            val notAdded = leftover.values.firstOrNull()?.amount ?: 0
            val added = stackSize - notAdded
            remaining -= added
            if (added <= 0) break
        }
        return maxAmount - remaining
    }
}
