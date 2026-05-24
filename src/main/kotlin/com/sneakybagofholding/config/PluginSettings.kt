package com.sneakybagofholding.config

/**
 * Global plugin settings loaded from config.yml `settings` section.
 *
 * @property defaultItemCapacity Global per-item base capacity when an item omits `default-capacity`.
 * @property defaultCategoryCapacity Global category bonus when a category omits `default-capacity`.
 * @property defaultGlobalCapacity Per-player global bonus when the player has no override.
 */
data class SoundConfig(
    val sound: String,
    val pitch: Float = 1.0f,
    val volume: Float = 1.0f
) {
    fun play(player: org.bukkit.entity.Player) {
        player.playSound(player.location, sound, org.bukkit.SoundCategory.PLAYERS, volume, pitch)
    }
}

data class AudioSettings(
    val withdraw: SoundConfig? = null,
    val deposit: SoundConfig? = null,
    val toggleAutoloot: SoundConfig? = null,
    val pickup: SoundConfig? = null
)

data class PluginSettings(
    val mainMenuTitle: String,
    val categoryRows: Int,
    val commandAliases: List<String>,
    val preventThrownPickup: Boolean,
    val defaultItemLore: List<String>,
    val defaultItemCapacity: Int = DEFAULT_ITEM_CAPACITY,
    val defaultCategoryCapacity: Int = DEFAULT_CATEGORY_CAPACITY,
    val defaultGlobalCapacity: Int = DEFAULT_GLOBAL_CAPACITY,
    /** When true, category icons use enchant glint while autopickup is on. */
    val autopickupEnchantGlow: Boolean = true,
    val menuLayout: MenuLayoutSettings = MenuLayoutSettings(),
    val audio: AudioSettings = AudioSettings(),
) {
    companion object {
        const val DEFAULT_ITEM_CAPACITY = 1000
        const val DEFAULT_CATEGORY_CAPACITY = 0
        const val DEFAULT_GLOBAL_CAPACITY = 0
    }
}
