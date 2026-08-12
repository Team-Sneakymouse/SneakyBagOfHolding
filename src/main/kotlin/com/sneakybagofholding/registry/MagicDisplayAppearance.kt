package com.sneakybagofholding.registry

import org.bukkit.Material

/**
 * Display fields taken from MagicSpells [MagicItemData] for GUI icons only.
 * Lore is never read from MagicSpells.
 */
data class MagicDisplayAppearance(
    val material: Material,
    val displayName: String?,
    /** Opaque MagicSpells [CustomModelDataValues], when the item defines custom model data. */
    val customModelDataValues: Any? = null,
    /** Legacy integer CMD when MagicSpells still stores an [Int] (pre-CustomModelDataValues). */
    val legacyCustomModelData: Int? = null,
)
