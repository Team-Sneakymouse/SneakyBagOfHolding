package com.sneakybagofholding.registry

import com.sneakybagofholding.SneakyBagOfHolding
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin
import java.lang.reflect.Method

/**
 * Resolves MagicSpells magic items via reflection on MagicSpells' classloader.
 *
 * Paper plugins cannot directly reference MagicSpells types at runtime unless
 * [join-classpath](https://docs.papermc.io/paper/dev/getting-started/paper-plugins/) is used;
 * loading API classes from the MagicSpells plugin avoids [NoClassDefFoundError].
 */
class MagicItemResolver(private val plugin: SneakyBagOfHolding) {

    private var available = false
    private var magicSpellsPlugin: Plugin? = null

    private var getItemByInternalName: Method? = null
    private var getMagicItemDataFromItemStack: Method? = null
    private var getMagicItemDataByInternalName: Method? = null
    private var magicItemDataMatches: Method? = null

    private var magicItemDataHasAttribute: Method? = null
    private var magicItemDataGetAttribute: Method? = null
    private var attrType: Any? = null
    private var attrName: Any? = null
    private var attrCustomModelData: Any? = null

    /**
     * Binds to MagicSpells when that plugin is present and enabled.
     * @return true if API methods were resolved successfully
     */
    fun initialize(): Boolean {
        available = false
        magicSpellsPlugin = null
        getItemByInternalName = null
        getMagicItemDataFromItemStack = null
        getMagicItemDataByInternalName = null
        magicItemDataMatches = null
        magicItemDataHasAttribute = null
        magicItemDataGetAttribute = null
        attrType = null
        attrName = null
        attrCustomModelData = null

        val ms = Bukkit.getPluginManager().getPlugin("MagicSpells") ?: run {
            plugin.logger.warning("MagicSpells not found — item matching and giving disabled.")
            return false
        }
        if (!ms.isEnabled) {
            plugin.logger.info("MagicSpells not enabled yet — will retry when it loads.")
            return false
        }

        return try {
            val loader = ms.javaClass.classLoader
            val magicItemsClass = Class.forName(
                "com.nisovin.magicspells.util.magicitems.MagicItems",
                true,
                loader
            )
            val magicItemDataClass = Class.forName(
                "com.nisovin.magicspells.util.magicitems.MagicItemData",
                true,
                loader
            )
            val magicItemAttributeClass = Class.forName(
                "com.nisovin.magicspells.util.magicitems.MagicItemData\$MagicItemAttribute",
                true,
                loader
            )

            getItemByInternalName = magicItemsClass.getMethod("getItemByInternalName", String::class.java)
            getMagicItemDataFromItemStack = magicItemsClass.getMethod(
                "getMagicItemDataFromItemStack",
                ItemStack::class.java
            )
            getMagicItemDataByInternalName = magicItemsClass.getMethod(
                "getMagicItemDataByInternalName",
                String::class.java
            )
            magicItemDataMatches = magicItemDataClass.getMethod("matches", magicItemDataClass)
            magicItemDataHasAttribute = magicItemDataClass.getMethod(
                "hasAttribute",
                magicItemAttributeClass
            )
            magicItemDataGetAttribute = magicItemDataClass.getMethod(
                "getAttribute",
                magicItemAttributeClass
            )

            @Suppress("UNCHECKED_CAST")
            val enumConstants = magicItemAttributeClass.enumConstants as Array<Any>
            for (constant in enumConstants) {
                when (constant.toString()) {
                    "type" -> attrType = constant
                    "name" -> attrName = constant
                    "custom-model-data" -> attrCustomModelData = constant
                }
            }

            magicSpellsPlugin = ms
            available = true
            plugin.logger.info("Hooked MagicSpells API for item resolution.")
            true
        } catch (e: ReflectiveOperationException) {
            plugin.logger.severe("Failed to hook MagicSpells API: ${e.message}")
            false
        }
    }

    fun isAvailable(): Boolean = available

    /**
     * Material, display name, and custom model data from MagicSpells template data only (no lore).
     */
    fun getDisplayAppearance(internalName: String): MagicDisplayAppearance? {
        val typeAttr = attrType ?: return null
        if (!available) return null
        val data = getTemplateData(internalName) ?: return null
        val material = getDataAttribute(data, typeAttr) as? Material ?: return null
        val displayName = attrName?.let { attr ->
            getDataAttribute(data, attr) as? Component
        }?.let { componentToConfigString(it) }
        val customModelData = attrCustomModelData?.let { attr ->
            getDataAttribute(data, attr) as? Int
        }
        return MagicDisplayAppearance(material, displayName, customModelData)
    }

    private fun getDataAttribute(data: Any, attribute: Any): Any? {
        if (magicItemDataHasAttribute == null || magicItemDataGetAttribute == null) return null
        return try {
            val has = magicItemDataHasAttribute!!.invoke(data, attribute) as Boolean
            if (!has) return null
            magicItemDataGetAttribute!!.invoke(data, attribute)
        } catch (e: ReflectiveOperationException) {
            null
        }
    }

    private fun componentToConfigString(component: Component): String =
        MiniMessage.miniMessage().serialize(component)

    /**
     * Returns a clone of the MagicSpells item for [internalName], or null if unavailable.
     */
    fun createItem(internalName: String, amount: Int = 1): ItemStack? {
        if (!available) return null
        return try {
            val stack = (getItemByInternalName!!.invoke(null, internalName) as? ItemStack)?.clone()
                ?: return null
            stack.amount = amount.coerceAtLeast(1)
            stack
        } catch (e: ReflectiveOperationException) {
            plugin.logger.warning("createItem failed for $internalName: ${e.message}")
            null
        }
    }

    /**
     * Extracts MagicSpells [MagicItemData] from a stack for matching (opaque [Any]).
     */
    fun getDataFromStack(stack: ItemStack): Any? {
        if (!available) return null
        return try {
            getMagicItemDataFromItemStack!!.invoke(null, stack)
        } catch (e: ReflectiveOperationException) {
            null
        }
    }

    /**
     * Returns template data for a configured internal name (opaque [Any]).
     */
    fun getTemplateData(internalName: String): Any? {
        if (!available) return null
        return try {
            getMagicItemDataByInternalName!!.invoke(null, internalName)
        } catch (e: ReflectiveOperationException) {
            null
        }
    }

    fun matches(stackData: Any, template: Any): Boolean {
        if (!available) return false
        return try {
            magicItemDataMatches!!.invoke(template, stackData) as Boolean
        } catch (e: ReflectiveOperationException) {
            false
        }
    }
}
