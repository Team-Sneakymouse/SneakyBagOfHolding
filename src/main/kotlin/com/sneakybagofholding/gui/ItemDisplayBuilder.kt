package com.sneakybagofholding.gui

import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.config.ItemDefinition
import com.sneakybagofholding.config.ItemDisplayDefinition
import com.sneakybagofholding.registry.MagicDisplayAppearance
import com.sneakybagofholding.registry.MagicItemResolver
import com.sneakybagofholding.storage.PlayerDataStore
import com.sneakybagofholding.util.CustomModelDataSupport
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

    fun build(player: Player, item: ItemDefinition): ItemStack {
        val display = item.display
        val appearance = magicItemResolver.getDisplayAppearance(item.id)

        val stack = ItemStack(appearance?.material ?: resolveFallbackMaterial(display), 1)
        val autopickupEnabled = playerDataStore.get(player).isAutopickupEnabled(item.id)
        val loreLines = resolvePluginLoreLines(player, item, display)
        val loreComponents = ItemMetaText.toLoreComponents(loreLines)

        stack.editMeta { meta ->
            GuiDisplayMarker.stripMagicSpellsIdentity(meta)
            applyDisplayName(meta, appearance, display)
            applyCustomModelData(meta, appearance, display, autopickupEnabled)
            applyAutopickupGlow(meta, autopickupEnabled)
            meta.lore(loreComponents)
            GuiDisplayMarker.mark(meta)
        }
        ItemMetaText.syncLoreComponent(stack, loreComponents)
        return stack
    }

    private fun resolveFallbackMaterial(display: ItemDisplayDefinition?): Material {
        val name = display?.material ?: "PAPER"
        return Material.matchMaterial(name.uppercase()) ?: Material.PAPER
    }

    private fun applyDisplayName(
        meta: ItemMeta,
        appearance: MagicDisplayAppearance?,
        display: ItemDisplayDefinition?,
    ) {
        when {
            display?.name != null -> ItemMetaText.setDisplayName(meta, display.name)
            !appearance?.displayName.isNullOrBlank() -> ItemMetaText.setDisplayName(meta, appearance!!.displayName!!)
        }
    }

    /**
     * Uses MagicSpells CMD by default; [ItemDisplayDefinition.autopickupOffCustomModelData] overrides when autopickup is off.
     * [ItemDisplayDefinition.autopickupOnCustomModelData] optionally overrides when on.
     */
    private fun applyCustomModelData(
        meta: ItemMeta,
        appearance: MagicDisplayAppearance?,
        display: ItemDisplayDefinition?,
        autopickupEnabled: Boolean,
    ) {
        val overrideCmd = when {
            !autopickupEnabled && display?.autopickupOffCustomModelData != null ->
                display.autopickupOffCustomModelData
            autopickupEnabled && display?.autopickupOnCustomModelData != null ->
                display.autopickupOnCustomModelData
            else -> null
        }
        if (overrideCmd != null) {
            CustomModelDataSupport.applyLegacyInt(meta, overrideCmd)
            return
        }
        appearance?.customModelDataValues?.let { values ->
            magicItemResolver.applyCustomModelData(meta, values)
            return
        }
        appearance?.legacyCustomModelData?.let { legacy ->
            CustomModelDataSupport.applyLegacyInt(meta, legacy)
            return
        }
        display?.customModelData?.let { CustomModelDataSupport.applyLegacyInt(meta, it) }
    }

    private fun applyAutopickupGlow(meta: ItemMeta, autopickupEnabled: Boolean) {
        if (!configManager.getSettings().autopickupEnchantGlow) {
            meta.setEnchantmentGlintOverride(null)
            return
        }
        meta.setEnchantmentGlintOverride(autopickupEnabled)
    }

    /**
     * Plugin lore only. Per-item [ItemDisplayDefinition.lore] may override the global template when non-empty.
     * MagicSpells item lore is never used.
     */
    private fun resolvePluginLoreLines(
        player: Player,
        item: ItemDefinition,
        display: ItemDisplayDefinition?,
    ): List<String> {
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
        return loreTemplate.map { line ->
            line.replace("{stored}", stored.toString())
                .replace("{max}", max.toString())
                .replace("{autoloot_status}", autopickupStatus)
                .replace("{item_id}", item.id)
        }
    }

    fun buildFallbackOnly(item: ItemDefinition): ItemStack {
        val display = item.display
        val material = resolveFallbackMaterial(display)
        val stack = ItemStack(material, 1)
        display?.customModelData?.let { ItemStackParser.applyCustomModelData(stack, it) }
        return stack
    }
}
