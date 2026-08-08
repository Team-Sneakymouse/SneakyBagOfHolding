package com.sneakybagofholding.api

import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * Public integration surface for other plugins (for example SneakyGroundItems, MagicSpells).
 *
 * Obtain via [get] or Bukkit's [org.bukkit.plugin.ServicesManager].
 */
interface BagOfHoldingApi {

    /**
     * Removes exactly [amount] of [itemId] from [player]'s bag storage and returns a fresh
     * [org.bukkit.inventory.ItemStack] suitable for world placement.
     *
     * Succeeds only when the player has at least [amount] stored and the item can be created.
     * On failure, storage is unchanged.
     */
    fun withdrawAsItemStack(player: Player, itemId: String, amount: Int): WithdrawResult

    /**
     * Returns how many of [itemId] [player] currently has stored in their bag.
     *
     * Returns `0` when [itemId] is not registered in SneakyBagOfHolding config.
     * Read-only: does not modify bag storage or player inventory.
     */
    fun getStoredAmount(player: Player, itemId: String): Int

    /**
     * Resolves [stack] to a configured bag item id, or `null` when unregistered or not storable.
     */
    fun resolveItemId(stack: ItemStack): String?

    /**
     * Whether [player] has autopickup enabled for [itemId].
     * Returns `false` when the item is unknown or autopickup is off.
     */
    fun isAutopickupEnabled(player: Player, itemId: String): Boolean

    /**
     * How many more of [itemId] [player] can store before hitting capacity.
     * Returns `0` when the item is not registered.
     */
    fun getRemainingCapacity(player: Player, itemId: String): Int

    /**
     * Adds up to [amount] of [itemId] directly into bag storage (does not remove from inventory).
     * Clamped to remaining capacity. Returns the amount actually deposited.
     */
    fun deposit(player: Player, itemId: String, amount: Int): Int

    companion object {
        @JvmStatic
        fun get(): BagOfHoldingApi? =
            Bukkit.getServicesManager().getRegistration(BagOfHoldingApi::class.java)?.provider
    }
}
