package com.sneakybagofholding.listener

import com.sneakybagofholding.SneakyBagOfHolding
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent

/**
 * Retries SneakyItems API hook when SneakyItems enables after this plugin.
 */
class SneakyItemsHookListener(private val plugin: SneakyBagOfHolding) : Listener {

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (!event.plugin.name.equals("SneakyItems", ignoreCase = true)) return
        if (plugin.magicItemResolver.isAvailable()) return
        if (plugin.magicItemResolver.initialize()) {
            plugin.itemRegistry.reload()
        }
    }
}
