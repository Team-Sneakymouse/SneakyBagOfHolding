package com.sneakybagofholding.gui

import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.config.ItemDefinition
import com.sneakybagofholding.config.ItemDisplayDefinition
import com.sneakybagofholding.registry.MagicDisplayAppearance
import com.sneakybagofholding.registry.MagicItemResolver
import com.sneakybagofholding.storage.PlayerDataStore
import com.sneakybagofholding.util.CustomModelDataSupport
import com.sneakybagofholding.util.ItemModelSupport
import com.sneakyutils.util.ItemMetaText
import com.sneakybagofholding.util.ItemStackParser
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.ItemMeta

/**
 * Builds category browser item stacks using magic-item appearance (material, name, item model, CMD)
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
     * Uses the MagicSpells appearance by default; [ItemDisplayDefinition.autopickupOffItemModel] overrides when autopickup is off.
     * [ItemDisplayDefinition.autopickupOnItemModel] optionally overrides when on (legacy integer keys are a fallback).
     */
    private fun applyCustomModelData(
        meta: ItemMeta,
        appearance: MagicDisplayAppearance?,
        display: ItemDisplayDefinition?,
        autopickupEnabled: Boolean,
    ) {
        if (display != null) {
            val overrideModel = if (autopickupEnabled) display.autopickupOnItemModel else display.autopickupOffItemModel
            val overrideCmd = if (autopickupEnabled) {
                display.autopickupOnCustomModelData
            } else {
                display.autopickupOffCustomModelData
            }
            if (ItemModelSupport.apply(meta, overrideModel, overrideCmd)) return
        }
        if (appearance != null && applyMagicAppearanceModel(meta, appearance)) return
        display?.let { ItemModelSupport.apply(meta, it.itemModel, it.customModelData) }
    }

    /**
     * Copies the magic item's model components: `item_model` and custom model data may coexist
     * (an item model can select on CMD), so both are applied. Returns true if anything was applied.
     */
    private fun applyMagicAppearanceModel(meta: ItemMeta, appearance: MagicDisplayAppearance): Boolean {
        var applied = false
        appearance.itemModel?.let {
            meta.itemModel = it
            applied = true
        }
        val values = appearance.customModelDataValues
        val legacy = appearance.legacyCustomModelData
        if (values != null) {
            magicItemResolver.applyCustomModelData(meta, values)
            applied = true
        } else if (legacy != null) {
            CustomModelDataSupport.applyLegacyInt(meta, legacy)
            applied = true
        }
        return applied
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
        display?.let { ItemStackParser.applyItemModel(stack, it.itemModel, it.customModelData) }
        return stack
    }
}
