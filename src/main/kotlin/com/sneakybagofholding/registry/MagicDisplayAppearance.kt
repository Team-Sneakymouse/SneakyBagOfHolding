package com.sneakybagofholding.registry

import org.bukkit.Material

/**
 * Display fields taken from MagicSpells [MagicItemData] for GUI icons only.
 * Lore is never read from MagicSpells.
 */
data class MagicDisplayAppearance(
    val material: Material,
    val displayName: String?,
    val customModelData: Int?,
)
