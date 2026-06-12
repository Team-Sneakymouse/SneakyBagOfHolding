package com.sneakybagofholding.api

/**
 * Why [BagOfHoldingApi.withdrawAsItemStack] did not produce an item stack.
 */
enum class WithdrawFailureReason {
    /** [amount] was zero or negative. */
    INVALID_AMOUNT,

    /** [itemId] is not registered in SneakyBagOfHolding config. */
    UNKNOWN_ITEM,

    /** The player has fewer than the requested amount stored. */
    INSUFFICIENT_STORED,

    /** The item could not be materialized (for example MagicSpells unavailable). */
    ITEM_CREATION_FAILED,
}
