package com.sneakybagofholding.listener

import com.sneakybagofholding.storage.PlayerDataStore
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent

/**
 * Loads player data on join and saves on quit.
 */
class PlayerDataListener(private val playerDataStore: PlayerDataStore) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        playerDataStore.get(event.player.uniqueId)
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        playerDataStore.unload(event.player.uniqueId)
    }
}
