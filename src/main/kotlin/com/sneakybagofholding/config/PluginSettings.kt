package com.sneakybagofholding.config

/**
 * Global plugin settings loaded from config.yml `settings` section.
 */
data class PluginSettings(
    val mainMenuTitle: String,
    val categoryRows: Int,
    val commandAliases: List<String>,
    val suppressAutopickupSound: Boolean,
    val defaultItemLore: List<String>
)
