package com.sneakybagofholding.gui

/**
 * Fixed slot layout for category browser menus (6 rows = 54 slots).
 *
 * ```
 * [ items 0 .. 49 ]
 * [ decorative | 51 prev | 52 back | 53 next ]  (decorative defaults to slot 50)
 * ```
 */
object CategoryMenuLayout {
    const val ITEMS_PER_PAGE = 50
    const val LAST_ITEM_SLOT = 49
    const val DEFAULT_DECORATIVE_SLOT = 50
    const val PREV_TAB_SLOT = 51
    const val BACK_SLOT = 52
    const val NEXT_TAB_SLOT = 53

    @Deprecated("Use DEFAULT_DECORATIVE_SLOT", ReplaceWith("DEFAULT_DECORATIVE_SLOT"))
    const val GAP_SLOT = DEFAULT_DECORATIVE_SLOT

    fun isItemSlot(slot: Int): Boolean = slot in 0..LAST_ITEM_SLOT

    fun isNavigationSlot(slot: Int, decorativeSlot: Int = DEFAULT_DECORATIVE_SLOT): Boolean =
        slot == decorativeSlot || slot == PREV_TAB_SLOT || slot == BACK_SLOT || slot == NEXT_TAB_SLOT

    /** Global item index in the category list for a GUI slot on [pageIndex]. */
    fun globalItemIndex(pageIndex: Int, slot: Int): Int? {
        if (!isItemSlot(slot)) return null
        return pageIndex * ITEMS_PER_PAGE + slot
    }

    fun pageCount(itemCount: Int): Int =
        if (itemCount <= 0) 1 else (itemCount + ITEMS_PER_PAGE - 1) / ITEMS_PER_PAGE
}
