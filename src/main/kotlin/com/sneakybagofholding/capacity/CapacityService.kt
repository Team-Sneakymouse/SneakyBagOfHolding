package com.sneakybagofholding.capacity

import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.config.ItemDefinition
import com.sneakybagofholding.registry.ItemRegistry
import com.sneakybagofholding.storage.PlayerDataStore
import org.bukkit.entity.Player
import java.util.UUID

/**
 * Computes stacked storage capacity: per-item base plus each category's bonus.
 *
 * `effectiveMax(item) = itemCapacity + sum(categoryCapacity(c) for c in item.categories) + globalCapacity`
 */
class CapacityService(
    private val configManager: ConfigManager,
    private val itemRegistry: ItemRegistry,
    private val playerDataStore: PlayerDataStore
) {

    /**
     * Per-item base capacity: config default or player override.
     */
    fun getItemCapacity(playerId: UUID, item: ItemDefinition): Int {
        val data = playerDataStore.get(playerId)
        return data.itemCapacity[item.id] ?: item.defaultCapacity
    }

    /**
     * Category capacity bonus: config default or player override.
     */
    fun getCategoryCapacity(playerId: UUID, categoryId: String): Int {
        val categories = configManager.getCategories()
        val def = categories[categoryId] ?: return 0
        val data = playerDataStore.get(playerId)
        return data.categoryCapacity[categoryId] ?: def.defaultCapacity
    }

    /**
     * Global per-player capacity bonus: config default or player override.
     */
    fun getGlobalCapacity(playerId: UUID): Int {
        val data = playerDataStore.get(playerId)
        return data.globalCapacity ?: configManager.getSettings().defaultGlobalCapacity
    }

    /**
     * Stacked maximum storage for an item.
     */
    fun effectiveMax(playerId: UUID, itemId: String): Int {
        val item = itemRegistry.getItem(itemId) ?: return 0
        var total = getItemCapacity(playerId, item) + getGlobalCapacity(playerId)
        for (categoryId in item.categories) {
            total += getCategoryCapacity(playerId, categoryId)
        }
        return total.coerceAtLeast(0)
    }

    fun effectiveMax(player: Player, itemId: String): Int = effectiveMax(player.uniqueId, itemId)

    fun getStored(playerId: UUID, itemId: String): Int =
        playerDataStore.get(playerId).getStored(itemId)

    fun remaining(playerId: UUID, itemId: String): Int =
        (effectiveMax(playerId, itemId) - getStored(playerId, itemId)).coerceAtLeast(0)

    fun remaining(player: Player, itemId: String): Int = remaining(player.uniqueId, itemId)

    fun canDeposit(playerId: UUID, itemId: String, amount: Int): Boolean =
        amount > 0 && remaining(playerId, itemId) >= amount
}
