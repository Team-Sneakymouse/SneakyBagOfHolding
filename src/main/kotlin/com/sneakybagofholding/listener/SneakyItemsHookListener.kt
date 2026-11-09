package com.sneakybagofholding.listener

import com.sneakybagofholding.SneakyBagOfHolding
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent
import org.bukkit.event.server.ServerLoadEvent

/**
 * Keeps the SneakyItems hook and item templates current.
 *
 * Item owners such as MagicSpells register with SneakyItems during their own enable, which can be after
 * this plugin enables. Templates are rebuilt once startup finishes and when a later plugin enable may
 * have registered items that are still missing.
 */
class SneakyItemsHookListener(private val plugin: SneakyBagOfHolding) : Listener {

    private var serverLoaded = false

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (event.plugin === plugin) return
        if (!plugin.magicItemResolver.isAvailable()) {
            if (!event.plugin.name.equals("SneakyItems", ignoreCase = true)) return
            if (plugin.magicItemResolver.initialize()) plugin.itemRegistry.reload()
            return
        }
        // During startup, ServerLoadEvent does a single rebuild after every plugin has enabled.
        if (serverLoaded && plugin.itemRegistry.hasMissingTemplates()) {
            plugin.itemRegistry.reload()
        }
    }

    @EventHandler
    fun onServerLoad(event: ServerLoadEvent) {
        serverLoaded = true
        if (plugin.magicItemResolver.isAvailable() || plugin.magicItemResolver.initialize()) {
            plugin.itemRegistry.reload()
        }
    }
}
