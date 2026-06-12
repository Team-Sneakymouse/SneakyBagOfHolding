package com.sneakybagofholding.api

import org.bukkit.Bukkit
import org.bukkit.entity.Player

/**
 * Public integration surface for other plugins (for example SneakyGroundItems).
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

    companion object {
        @JvmStatic
        fun get(): BagOfHoldingApi? =
            Bukkit.getServicesManager().getRegistration(BagOfHoldingApi::class.java)?.provider
    }
}
