package com.sneakybagofholding.api

import org.bukkit.inventory.ItemStack

/**
 * Outcome of withdrawing stored items directly as an [ItemStack] without using player inventory.
 */
sealed class WithdrawResult {

    data class Success(val itemStack: ItemStack) : WithdrawResult()

    data class Failure(val reason: WithdrawFailureReason) : WithdrawResult()

    val isSuccess: Boolean get() = this is Success

    fun itemStackOrNull(): ItemStack? = (this as? Success)?.itemStack
}
