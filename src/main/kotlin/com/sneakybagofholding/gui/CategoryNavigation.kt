package com.sneakybagofholding.gui

import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.config.CategoryDefinition

/**
 * Resolves prev/next navigation across category pages and adjacent categories.
 */
class CategoryNavigation(private val configManager: ConfigManager) {

    fun browsableCategoryIds(): List<String> =
        configManager.getBrowsableCategories().map { it.id }

    fun categoryIndex(categoryId: String): Int =
        browsableCategoryIds().indexOf(categoryId)

    fun itemsInCategory(categoryId: String) =
        configManager.getItemsInCategory(categoryId)

    fun pageCount(categoryId: String): Int =
        CategoryMenuLayout.pageCount(itemsInCategory(categoryId).size)

    data class ViewState(val categoryId: String, val pageIndex: Int)

    /** Next page in this category, or page 0 of the next browsable category. */
    fun resolveNext(current: ViewState): ViewState? {
        val pages = pageCount(current.categoryId)
        if (current.pageIndex < pages - 1) {
            return ViewState(current.categoryId, current.pageIndex + 1)
        }
        val catIdx = categoryIndex(current.categoryId)
        val ids = browsableCategoryIds()
        if (catIdx < 0 || catIdx >= ids.size - 1) return null
        return ViewState(ids[catIdx + 1], 0)
    }

    /** Previous page in this category, or the last page of the previous browsable category. */
    fun resolvePrevious(current: ViewState): ViewState? {
        if (current.pageIndex > 0) {
            return ViewState(current.categoryId, current.pageIndex - 1)
        }
        val catIdx = categoryIndex(current.categoryId)
        if (catIdx <= 0) return null
        val prevCategoryId = browsableCategoryIds()[catIdx - 1]
        val lastPage = pageCount(prevCategoryId) - 1
        return ViewState(prevCategoryId, lastPage)
    }

    fun prevButtonSubtitle(current: ViewState): String {
        resolvePrevious(current)?.let { target ->
            if (target.categoryId == current.categoryId) {
                return "<gray>Page ${target.pageIndex + 1}/${pageCount(current.categoryId)}"
            }
            val name = stripTags(configManager.getCategories()[target.categoryId]?.menuTitle ?: target.categoryId)
            return "<gray>$name (${target.pageIndex + 1}/${pageCount(target.categoryId)})"
        }
        return "<dark_gray>—"
    }

    fun nextButtonSubtitle(current: ViewState): String {
        resolveNext(current)?.let { target ->
            if (target.categoryId == current.categoryId) {
                return "<gray>Page ${target.pageIndex + 1}/${pageCount(current.categoryId)}"
            }
            val name = stripTags(configManager.getCategories()[target.categoryId]?.menuTitle ?: target.categoryId)
            return "<gray>$name (${target.pageIndex + 1}/${pageCount(target.categoryId)})"
        }
        return "<dark_gray>—"
    }

    fun inventoryTitle(category: CategoryDefinition, pageIndex: Int): String {
        val pages = pageCount(category.id)
        return if (pages > 1) {
            "${category.menuTitle} <gray>(${pageIndex + 1}/$pages)"
        } else {
            category.menuTitle
        }
    }

    private fun stripTags(text: String): String =
        text.replace(Regex("<[^>]+>"), "")
}
