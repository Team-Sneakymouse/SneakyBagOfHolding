package com.sneakybagofholding.storage

/**
 * Per-player bag of holding persistence model.
 *
 * Item ids are MagicSpells item ids (e.g. `item-banana`) in [stored], [autopickup] and [itemCapacity]; renaming an
 * item in MagicSpells-Data orphans its stored amount. Check `tools/migrate_*.py` before changing the format.
 *
 * @property stored Amount stored per item id.
 * @property autopickup Per-item autopickup enabled flags.
 * @property itemCapacity Overrides for per-item base capacity (additive component).
 * @property categoryCapacity Overrides for per-category capacity bonuses.
 * @property globalCapacity Player-wide capacity bonus; null uses [com.sneakybagofholding.config.PluginSettings.defaultGlobalCapacity].
 */
data class PlayerData(
    val stored: MutableMap<String, Int> = mutableMapOf(),
    val autopickup: MutableMap<String, Boolean> = mutableMapOf(),
    val itemCapacity: MutableMap<String, Int> = mutableMapOf(),
    val categoryCapacity: MutableMap<String, Int> = mutableMapOf(),
    var globalCapacity: Int? = null,
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
