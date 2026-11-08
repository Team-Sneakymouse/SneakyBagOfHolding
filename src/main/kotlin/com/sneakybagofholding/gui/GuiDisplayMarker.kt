package com.sneakybagofholding.gui

import org.bukkit.NamespacedKey
import org.bukkit.inventory.meta.ItemMeta
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

/**
 * Marks category-browser icons so magic-item updaters can skip them.
 *
 * MagicsSpells ItemTagSpell (e.g. oldItemFixer) and SneakyItems updaters re-tag stacks that look like magic items
 * on inventory open, which replaces plugin lore with YAML lore and sets the `magicitem` PDC.
 */
object GuiDisplayMarker {

    private lateinit var guiDisplayKey: NamespacedKey
    private val magicItemKey = NamespacedKey.fromString("magicspells:magicitem")!!
    private val menuOptionKey = NamespacedKey.fromString("magicspells:menuoption")!!

    fun init(plugin: JavaPlugin) {
        guiDisplayKey = NamespacedKey(plugin, "gui_display")
    }

    fun mark(meta: ItemMeta) {
        meta.persistentDataContainer.set(guiDisplayKey, PersistentDataType.BYTE, 1.toByte())
        meta.persistentDataContainer.set(menuOptionKey, PersistentDataType.STRING, "boh-display")
    }

    /** Removes magic-item identity tags that trigger in-inventory updates. */
    fun stripMagicSpellsIdentity(meta: ItemMeta) {
        meta.persistentDataContainer.remove(magicItemKey)
    }

    fun isMarked(meta: ItemMeta): Boolean =
        meta.persistentDataContainer.has(guiDisplayKey, PersistentDataType.BYTE)
}
