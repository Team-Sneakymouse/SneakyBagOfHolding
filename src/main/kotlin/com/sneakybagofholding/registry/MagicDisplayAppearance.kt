package com.sneakybagofholding.registry

import com.nisovin.magicspells.util.magicitems.CustomModelDataValues
import org.bukkit.Material

/**
 * Display fields taken from MagicSpells [MagicItemData] for GUI icons only.
 * Lore is never read from MagicSpells.
 */
data class MagicDisplayAppearance(
    val material: Material,
    val displayName: String?,
    /** MagicSpells custom model data component, when the item defines it. */
    val customModelDataValues: CustomModelDataValues? = null,
    /** Legacy integer CMD when MagicSpells still stores an [Int] (pre-CustomModelDataValues). */
    val legacyCustomModelData: Int? = null,
)
