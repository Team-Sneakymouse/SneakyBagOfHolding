package com.sneakybagofholding.api

import com.sneakybagofholding.service.BagService
import org.bukkit.entity.Player

internal class BagOfHoldingApiImpl(
    private val bagService: BagService,
) : BagOfHoldingApi {

    override fun withdrawAsItemStack(player: Player, itemId: String, amount: Int): WithdrawResult =
        bagService.withdrawAsItemStack(player, itemId, amount)
}
