package com.sneakybagofholding.gui

import org.bukkit.NamespacedKey
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

/**
 * Marks category-browser icons so MagicSpells menu/updater logic can skip them.
 *
 * MagicSpells [ItemTagSpell] (e.g. oldItemFixer) re-tags stacks that look like magic items
 * on every [org.bukkit.event.inventory.InventoryOpenEvent], which replaces plugin lore with
 * YAML lore and sets the `magicitem` PDC.
 */
object GuiDisplayMarker {

    private lateinit var guiDisplayKey: NamespacedKey
    private var magicSpellsMenuOptionKey: NamespacedKey? = null
    private var magicSpellsMagicItemKey: NamespacedKey? = null

    fun init(plugin: JavaPlugin) {
        guiDisplayKey = NamespacedKey(plugin, "gui_display")
        val ms = plugin.server.pluginManager.getPlugin("MagicSpells") ?: return
        magicSpellsMenuOptionKey = NamespacedKey(ms, "menuoption")
        magicSpellsMagicItemKey = NamespacedKey(ms, "magicitem")
    }

    fun mark(meta: ItemMeta) {
        meta.persistentDataContainer.set(guiDisplayKey, PersistentDataType.BYTE, 1.toByte())
        magicSpellsMenuOptionKey?.let { key ->
            meta.persistentDataContainer.set(key, PersistentDataType.STRING, "boh-display")
        }
    }

    /** Removes MagicSpells identity tags that trigger in-inventory updates. */
    fun stripMagicSpellsIdentity(meta: ItemMeta) {
        magicSpellsMagicItemKey?.let { meta.persistentDataContainer.remove(it) }
    }

    fun isMarked(meta: ItemMeta): Boolean =
        meta.persistentDataContainer.has(guiDisplayKey, PersistentDataType.BYTE)
}
