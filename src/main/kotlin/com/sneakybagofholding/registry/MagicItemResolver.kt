package com.sneakybagofholding.registry

import com.sneakybagofholding.SneakyBagOfHolding
import com.sneakyitems.api.SneakyItemsAPI
import com.sneakyitems.util.magicitems.CustomModelDataValues
import com.sneakyitems.util.magicitems.MagicItemData
import com.sneakyitems.util.magicitems.MagicItemData.MagicItemAttribute
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Resolves magic items through [SneakyItemsAPI].
 */
class MagicItemResolver(private val plugin: SneakyBagOfHolding) {

    private var available = false

    /**
     * Marks SneakyItems ready when that plugin is present and enabled.
     * @return true if item APIs may be called
     */
    fun initialize(): Boolean {
        available = false
        val items = Bukkit.getPluginManager().getPlugin("SneakyItems") ?: run {
            plugin.logger.warning("SneakyItems not found — item matching and giving disabled.")
            return false
        }
        if (!items.isEnabled) {
            plugin.logger.info("SneakyItems not enabled yet — will retry when it loads.")
            return false
        }
        available = true
        plugin.logger.info("Hooked SneakyItems API for item resolution.")
        return true
    }

    fun isAvailable(): Boolean = available

    /**
     * Material, display name, and custom model data from template data only (no lore).
     */
    fun getDisplayAppearance(internalName: String): MagicDisplayAppearance? {
        if (!available) return null
        val data = getTemplateData(internalName) ?: return null
        val material = data.getAttribute(MagicItemAttribute.TYPE) as? Material ?: return null
        val displayName = if (data.hasAttribute(MagicItemAttribute.NAME)) {
            (data.getAttribute(MagicItemAttribute.NAME) as? Component)?.let { componentToConfigString(it) }
        } else {
            null
        }
        val rawCmd = if (data.hasAttribute(MagicItemAttribute.CUSTOM_MODEL_DATA)) {
            data.getAttribute(MagicItemAttribute.CUSTOM_MODEL_DATA)
        } else {
            null
        }
        val customModelDataValues = resolveCustomModelDataValues(rawCmd)
        val legacyCustomModelData = if (customModelDataValues == null && rawCmd is Int) rawCmd else null
        return MagicDisplayAppearance(material, displayName, customModelDataValues, legacyCustomModelData)
    }

    /**
     * Applies custom model data to a GUI icon.
     */
    fun applyCustomModelData(meta: ItemMeta, values: CustomModelDataValues) {
        if (!available) return
        values.applyTo(meta)
    }

    private fun resolveCustomModelDataValues(raw: Any?): CustomModelDataValues? = when (raw) {
        null -> null
        is CustomModelDataValues -> raw
        is Int -> CustomModelDataValues.ofLegacyInt(raw)
        else -> null
    }

    private fun componentToConfigString(component: Component): String =
        MiniMessage.miniMessage().serialize(component)

    /**
     * Returns a clone of the registered item for [internalName], or null if unavailable.
     * Does not apply magic-item behaviors (soulbound, expiration, etc.).
     */
    fun createItem(internalName: String, amount: Int = 1): ItemStack? {
        if (!available) return null
        return try {
            val stack = SneakyItemsAPI.getItemStack(internalName)?.clone() ?: return null
            stack.amount = amount.coerceAtLeast(1)
            stack
        } catch (e: RuntimeException) {
            plugin.logger.warning("createItem failed for $internalName: ${e.message}")
            null
        } catch (e: LinkageError) {
            plugin.logger.warning("createItem failed for $internalName: ${e.message}")
            null
        }
    }

    /**
     * Creates an item for [player], applying magic-item behaviors (e.g. soulbound).
     */
    fun createItemForPlayer(internalName: String, player: Player, amount: Int): ItemStack? {
        if (!available) return null
        return try {
            SneakyItemsAPI.createForPlayer(internalName, player, amount.coerceAtLeast(1))
                ?: createItem(internalName, amount)
        } catch (e: RuntimeException) {
            plugin.logger.warning("createItemForPlayer failed for $internalName: ${e.message}")
            null
        } catch (e: LinkageError) {
            plugin.logger.warning("createItemForPlayer failed for $internalName: ${e.message}")
            null
        }
    }

    fun getDataFromStack(stack: ItemStack): MagicItemData? {
        if (!available) return null
        return try {
            SneakyItemsAPI.getDataFromStack(stack)
        } catch (_: RuntimeException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    fun getTemplateData(internalName: String): MagicItemData? {
        if (!available) return null
        return try {
            SneakyItemsAPI.getDataFromString(internalName)
        } catch (_: RuntimeException) {
            null
        } catch (_: LinkageError) {
            null
        }
    }

    fun matches(stackData: MagicItemData, template: MagicItemData): Boolean {
        if (!available) return false
        return try {
            template.matches(stackData)
        } catch (_: RuntimeException) {
            false
        } catch (_: LinkageError) {
            false
        }
    }
}
