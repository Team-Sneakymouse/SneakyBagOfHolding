package com.sneakybagofholding.listener

import com.sneakybagofholding.SneakyBagOfHolding
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.PluginEnableEvent

/**
 * Retries MagicSpells API hook when MagicSpells enables after this plugin.
 */
class MagicSpellsHookListener(private val plugin: SneakyBagOfHolding) : Listener {

    @EventHandler
    fun onPluginEnable(event: PluginEnableEvent) {
        if (!event.plugin.name.equals("MagicSpells", ignoreCase = true)) return
        if (plugin.magicItemResolver.isAvailable()) return
        if (plugin.magicItemResolver.initialize()) {
            plugin.itemRegistry.reload()
        }
    }
}
