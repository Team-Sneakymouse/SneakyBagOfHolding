package com.sneakybagofholding.util

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable

object ItemStackStorage {

    private val plainText = PlainTextComponentSerializer.plainText()

    /** True when the stack can be deposited (non-damageable, or damageable at full durability). */
    fun isStorable(stack: ItemStack): Boolean {
        if (!hasEmptyBookContent(stack)) return false
        val damageable = stack.itemMeta as? Damageable ?: return true
        return damageable.damage <= 0
    }

    private fun hasEmptyBookContent(stack: ItemStack): Boolean = when (stack.type) {
        Material.WRITABLE_BOOK -> isWritableBookEmpty(stack)
        Material.WRITTEN_BOOK -> isWrittenBookEmpty(stack)
        else -> true
    }

    private fun isWritableBookEmpty(stack: ItemStack): Boolean {
        val content = stack.getData(DataComponentTypes.WRITABLE_BOOK_CONTENT) ?: return true
        return content.pages().none { page ->
            page.raw().isNotBlank() || page.filtered().isNotBlank()
        }
    }

    private fun isWrittenBookEmpty(stack: ItemStack): Boolean {
        val content = stack.getData(DataComponentTypes.WRITTEN_BOOK_CONTENT) ?: return true
        return content.pages().none { page ->
            plainText.serialize(page.raw()).isNotBlank() ||
                plainText.serialize(page.filtered()).isNotBlank()
        }
    }
}
