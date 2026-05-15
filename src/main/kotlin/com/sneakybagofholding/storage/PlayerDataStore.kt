package com.sneakybagofholding.storage

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.sneakybagofholding.SneakyBagOfHolding
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Loads and saves per-player bag data as JSON files under `plugins/SneakyBagOfHolding/data/`.
 */
class PlayerDataStore(private val plugin: SneakyBagOfHolding) {

    private val cache = ConcurrentHashMap<UUID, PlayerData>()
    private val dirty = ConcurrentHashMap.newKeySet<UUID>()
    private val dataFolder: File
        get() = File(plugin.dataFolder, "data").also { it.mkdirs() }

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    /**
     * Returns cached player data, loading from disk if needed.
     */
    fun get(uuid: UUID): PlayerData = cache.computeIfAbsent(uuid) { load(uuid) }

    fun get(player: Player): PlayerData = get(player.uniqueId)

    fun markDirty(uuid: UUID) {
        dirty.add(uuid)
    }

    fun markDirty(player: Player) = markDirty(player.uniqueId)

    /**
     * Saves all dirty player files synchronously.
     */
    fun saveDirty() {
        val toSave = dirty.toList()
        for (uuid in toSave) {
            save(uuid)
            dirty.remove(uuid)
        }
    }

    fun save(uuid: UUID) {
        val data = cache[uuid] ?: return
        val file = fileFor(uuid)
        file.writeText(gson.toJson(data))
    }

    fun unload(uuid: UUID) {
        if (dirty.contains(uuid)) save(uuid)
        cache.remove(uuid)
        dirty.remove(uuid)
    }

    private fun load(uuid: UUID): PlayerData {
        val file = fileFor(uuid)
        if (!file.exists()) return PlayerData()
        return try {
            gson.fromJson(file.readText(), PlayerData::class.java) ?: PlayerData()
        } catch (e: Exception) {
            plugin.logger.warning("Failed to load data for $uuid: ${e.message}")
            PlayerData()
        }
    }

    private fun fileFor(uuid: UUID) = File(dataFolder, "$uuid.json")

    fun startAutoSave(intervalTicks: Long = 20L * 60 * 5) {
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, Runnable { saveDirty() }, intervalTicks, intervalTicks)
    }
}
