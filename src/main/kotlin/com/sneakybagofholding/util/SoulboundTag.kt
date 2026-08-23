package com.sneakybagofholding.util

import org.bukkit.NamespacedKey
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * Writes MagicSpells' soulbound owner PDC when withdrawing configured soulbound items.
 *
 * MagicSpells [com.nisovin.magicspells.spells.instant.ConjureSpell] uses
 * `magicspells:soulbound_owner` (STRING = owner UUID); enforcement is handled by MagicSpells.
 */
object SoulboundTag {

    private var soulboundOwnerKey: NamespacedKey? = null

    fun init(plugin: JavaPlugin) {
        val ms = plugin.server.pluginManager.getPlugin("MagicSpells") ?: return
        soulboundOwnerKey = NamespacedKey(ms, "soulbound_owner")
    }

    fun apply(meta: ItemMeta, ownerUuid: UUID) {
        soulboundOwnerKey?.let { key ->
            meta.persistentDataContainer.set(key, PersistentDataType.STRING, ownerUuid.toString())
        }
    }
}
