package com.sneakybagofholding.util

import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable

object ItemStackStorage {

    /** True when the stack can be deposited (non-damageable, or damageable at full durability). */
    fun isStorable(stack: ItemStack): Boolean {
        val damageable = stack.itemMeta as? Damageable ?: return true
        return damageable.damage <= 0
    }
}
