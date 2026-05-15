package com.sneakybagofholding.storage

/**
 * Per-player bag of holding persistence model.
 *
 * @property stored Amount stored per item id.
 * @property autopickup Per-item autopickup enabled flags.
 * @property itemCapacity Overrides for per-item base capacity (additive component).
 * @property categoryCapacity Overrides for per-category capacity bonuses.
 */
data class PlayerData(
    val stored: MutableMap<String, Int> = mutableMapOf(),
    val autopickup: MutableMap<String, Boolean> = mutableMapOf(),
    val itemCapacity: MutableMap<String, Int> = mutableMapOf(),
    val categoryCapacity: MutableMap<String, Int> = mutableMapOf()
) {
    fun getStored(itemId: String): Int = stored[itemId] ?: 0

    fun isAutopickupEnabled(itemId: String): Boolean = autopickup[itemId] == true

    fun setStored(itemId: String, amount: Int) {
        if (amount <= 0) stored.remove(itemId) else stored[itemId] = amount
    }

    fun setAutopickup(itemId: String, enabled: Boolean) {
        if (!enabled) autopickup.remove(itemId) else autopickup[itemId] = true
    }
}
