package com.sneakybagofholding.gui

import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.config.ItemDefinition
import com.sneakybagofholding.config.ItemDisplayDefinition
import com.sneakybagofholding.registry.MagicItemResolver
import com.sneakybagofholding.storage.PlayerDataStore
import com.sneakybagofholding.util.ItemMetaText
import com.sneakybagofholding.util.ItemStackParser
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Builds category browser item stacks using MagicSpells appearance (material, name, CMD)
 * and plugin-controlled lore only.
 */
class ItemDisplayBuilder(
    private val configManager: ConfigManager,
    private val magicItemResolver: MagicItemResolver,
    private val playerDataStore: PlayerDataStore,
    private val effectiveMax: (Player, String) -> Int
) {

    /**
     * Material, display name, and custom model data taken from a MagicSpells item stack.
     */
    private data class MagicItemAppearance(
        val material: Material,
        val customModelData: Int?,
        val displayName: net.kyori.adventure.text.Component?
    )

    fun build(player: Player, item: ItemDefinition): ItemStack {
        val display = item.display
        val magicStack = magicItemResolver.createItem(item.id, 1)
        val appearance = magicStack?.let { extractAppearance(it) }

        val stack = ItemStack(appearance?.material ?: resolveFallbackMaterial(display), 1)
        val meta = stack.itemMeta ?: return stack

        applyDisplayName(meta, appearance, display)
        applyCustomModelData(meta, appearance, display, playerDataStore.get(player).isAutopickupEnabled(item.id))
        applyPluginLore(meta, player, item, display)

        stack.itemMeta = meta
        return stack
    }

    private fun extractAppearance(magicStack: ItemStack): MagicItemAppearance {
        val meta = magicStack.itemMeta
        val cmd = if (meta != null && meta.hasCustomModelData()) meta.customModelData else null
        return MagicItemAppearance(
            material = magicStack.type,
            customModelData = cmd,
            displayName = meta?.displayName()
        )
    }

    private fun resolveFallbackMaterial(display: ItemDisplayDefinition?): Material {
        val name = display?.material ?: "PAPER"
        return Material.matchMaterial(name.uppercase()) ?: Material.PAPER
    }

    private fun applyDisplayName(
        meta: ItemMeta,
        appearance: MagicItemAppearance?,
        display: ItemDisplayDefinition?
    ) {
        when {
            display?.name != null -> ItemMetaText.setDisplayName(meta, display.name)
            appearance?.displayName != null -> meta.displayName(appearance.displayName)
        }
    }

    /**
     * Uses MagicSpells CMD by default; [ItemDisplayDefinition.autopickupOffCustomModelData] overrides when autopickup is off.
     * [ItemDisplayDefinition.autopickupOnCustomModelData] optionally overrides when on.
     */
    private fun applyCustomModelData(
        meta: ItemMeta,
        appearance: MagicItemAppearance?,
        display: ItemDisplayDefinition?,
        autopickupEnabled: Boolean
    ) {
        val baseCmd = appearance?.customModelData ?: display?.customModelData
        val cmd = when {
            !autopickupEnabled && display?.autopickupOffCustomModelData != null ->
                display.autopickupOffCustomModelData
            autopickupEnabled && display?.autopickupOnCustomModelData != null ->
                display.autopickupOnCustomModelData
            else -> baseCmd
        }
        if (cmd != null) {
            meta.setCustomModelData(cmd)
        }
    }

    /** Lore comes only from plugin templates, never from the MagicSpells item. */
    private fun applyPluginLore(
        meta: ItemMeta,
        player: Player,
        item: ItemDefinition,
        display: ItemDisplayDefinition?
    ) {
        val data = playerDataStore.get(player)
        val stored = data.getStored(item.id)
        val max = effectiveMax(player, item.id)
        val autopickup = data.isAutopickupEnabled(item.id)

        val loreTemplate = if (display?.lore.isNullOrEmpty()) {
            configManager.getSettings().defaultItemLore
        } else {
            display!!.lore
        }
        val autopickupStatus = if (autopickup) "<green>[On]" else "<red>[Off]"
        ItemMetaText.setLore(
            meta,
            loreTemplate.map { line ->
                line.replace("{stored}", stored.toString())
                    .replace("{max}", max.toString())
                    .replace("{autoloot_status}", autopickupStatus)
                    .replace("{item_id}", item.id)
            }
        )
    }

    fun buildFallbackOnly(item: ItemDefinition): ItemStack {
        val display = item.display
        val material = resolveFallbackMaterial(display)
        val stack = ItemStack(material, 1)
        display?.customModelData?.let { ItemStackParser.applyCustomModelData(stack, it) }
        return stack
    }
}
