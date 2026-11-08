package com.sneakybagofholding.util

import org.bukkit.NamespacedKey
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID

/**
 * Writes the historical `magicspells:soulbound_owner` PDC when withdrawing configured soulbound items.
 * Enforcement is owned by SneakyItems.
 */
object SoulboundTag {

    private val soulboundOwnerKey = NamespacedKey.fromString("magicspells:soulbound_owner")!!

    fun init(@Suppress("UNUSED_PARAMETER") plugin: JavaPlugin) {
        // Keys are fixed historical MagicsSpells namespaces; no plugin lookup needed.
    }

    fun apply(meta: ItemMeta, ownerUuid: UUID) {
        meta.persistentDataContainer.set(soulboundOwnerKey, PersistentDataType.STRING, ownerUuid.toString())
    }
}
