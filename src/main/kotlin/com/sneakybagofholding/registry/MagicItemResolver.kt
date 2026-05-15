package com.sneakybagofholding.registry

import com.nisovin.magicspells.MagicSpells
import com.nisovin.magicspells.util.magicitems.MagicItemData
import com.nisovin.magicspells.util.magicitems.MagicItems
import com.sneakybagofholding.SneakyBagOfHolding
import org.bukkit.Bukkit
import org.bukkit.inventory.ItemStack

/**
 * Resolves MagicSpells magic items from [ItemStack]s and creates item stacks by internal name.
 */
class MagicItemResolver(private val plugin: SneakyBagOfHolding) {

    private var available: Boolean = false

    fun initialize() {
        available = Bukkit.getPluginManager().getPlugin("MagicSpells") != null
        if (!available) {
            plugin.logger.warning("MagicSpells not found — item matching and giving will be limited.")
        }
    }

    fun isAvailable(): Boolean = available

    /**
     * Returns a clone of the MagicSpells item for [internalName], or null if unavailable.
     */
    fun createItem(internalName: String, amount: Int = 1): ItemStack? {
        if (!available) return null
        val stack = MagicItems.getItemByInternalName(internalName) ?: return null
        stack.amount = amount.coerceAtLeast(1)
        return stack
    }

    /**
     * Extracts [MagicItemData] from a stack for matching.
     */
    fun getDataFromStack(stack: ItemStack): MagicItemData? {
        if (!available) return null
        return MagicItems.getMagicItemDataFromItemStack(stack)
    }

    /**
     * Returns cached template data for a configured internal name.
     */
    fun getTemplateData(internalName: String): MagicItemData? {
        if (!available) return null
        return MagicItems.getMagicItemDataByInternalName(internalName)
    }

    fun matches(stackData: MagicItemData, template: MagicItemData): Boolean = template.matches(stackData)
}
