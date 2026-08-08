package com.sneakybagofholding.api

import com.sneakybagofholding.service.BagService
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

internal class BagOfHoldingApiImpl(
    private val bagService: BagService,
) : BagOfHoldingApi {

    override fun withdrawAsItemStack(player: Player, itemId: String, amount: Int): WithdrawResult =
        bagService.withdrawAsItemStack(player, itemId, amount)

    override fun getStoredAmount(player: Player, itemId: String): Int =
        bagService.getStoredAmount(player, itemId)

    override fun resolveItemId(stack: ItemStack): String? =
        bagService.resolveItemIdForApi(stack)

    override fun isAutopickupEnabled(player: Player, itemId: String): Boolean =
        bagService.isAutopickupEnabled(player, itemId)

    override fun getRemainingCapacity(player: Player, itemId: String): Int =
        bagService.getRemainingCapacity(player, itemId)

    override fun deposit(player: Player, itemId: String, amount: Int): Int =
        bagService.depositDirect(player, itemId, amount)
}
