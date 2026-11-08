package com.sneakybagofholding.registry

import com.sneakyitems.util.magicitems.CustomModelDataValues
import org.bukkit.Material

/**
 * Display fields taken from SneakyItems [com.sneakyitems.util.magicitems.MagicItemData] for GUI icons only.
 * Lore is never read from the magic-item definition.
 */
data class MagicDisplayAppearance(
    val material: Material,
    val displayName: String?,
    /** Custom model data component, when the item defines it. */
    val customModelDataValues: CustomModelDataValues? = null,
    /** Legacy integer CMD when the definition still stores an [Int]. */
    val legacyCustomModelData: Int? = null,
)
