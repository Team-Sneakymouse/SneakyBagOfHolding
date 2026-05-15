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

    /**
     * Category browser with paginated item rows (50 per page).
     *
     * @property pageIndex Zero-based page within [categoryId].
     */
    class CategoryMenu(val categoryId: String, val pageIndex: Int = 0) : BagInventoryHolder() {
        override val holderId: String = "category:$categoryId:$pageIndex"
    }
}
