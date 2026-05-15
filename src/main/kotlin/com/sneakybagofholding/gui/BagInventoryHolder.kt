package com.sneakybagofholding.gui

import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder

/**
 * Marks plugin-owned inventories for event handling.
 */
sealed class BagInventoryHolder : InventoryHolder {

    abstract val holderId: String

    private lateinit var inventory: Inventory

    override fun getInventory(): Inventory = inventory

    internal fun bind(inventory: Inventory) {
        this.inventory = inventory
    }

    /** Main hub menu with category navigation. */
    class MainMenu : BagInventoryHolder() {
        override val holderId: String = "main"
    }

    /** Category browser listing items in one category. */
    class CategoryMenu(val categoryId: String) : BagInventoryHolder() {
        override val holderId: String = "category:$categoryId"
    }
}
