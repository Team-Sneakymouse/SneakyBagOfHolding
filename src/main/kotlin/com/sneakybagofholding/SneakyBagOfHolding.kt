package com.sneakybagofholding

import org.bukkit.plugin.java.JavaPlugin
import com.sneakybagofholding.commands.*

class SneakyBagOfHolding : JavaPlugin() {

	companion object {
		const val IDENTIFIER = "sneakybagofholding"
		lateinit var instance: SneakyBagOfHolding

		public fun log(message: String) {
			instance.logger.info(message)
		}
	}

	/**
     * Initializes the plugin instance during server load.
     */
    override fun onLoad() {
        instance = this
    }
    
    override fun onEnable() {
        logger.info("SneakyBagOfHolding plugin has been enabled!")

		// Register commands
        //
        
        // Save default config if it doesn't exist
        saveDefaultConfig()
    }
    
    override fun onDisable() {
        logger.info("SneakyBagOfHolding plugin has been disabled!")
    }
    
}
