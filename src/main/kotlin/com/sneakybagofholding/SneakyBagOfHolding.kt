package com.sneakybagofholding

import com.sneakybagofholding.capacity.CapacityService
import com.sneakybagofholding.commands.BohCommand
import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.gui.MenuService
import com.sneakybagofholding.listener.AutoPickupListener
import com.sneakybagofholding.listener.PlayerDataListener
import com.sneakybagofholding.registry.ItemRegistry
import com.sneakybagofholding.registry.MagicItemResolver
import com.sneakybagofholding.service.BagService
import com.sneakybagofholding.storage.PlayerDataStore
import org.bukkit.command.PluginCommand
import org.bukkit.plugin.java.JavaPlugin

class SneakyBagOfHolding : JavaPlugin() {

    lateinit var configManager: ConfigManager
        private set
    lateinit var playerDataStore: PlayerDataStore
        private set
    lateinit var itemRegistry: ItemRegistry
        private set
    lateinit var magicItemResolver: MagicItemResolver
        private set
    lateinit var capacityService: CapacityService
        private set
    lateinit var bagService: BagService
        private set
    lateinit var menuService: MenuService
        private set

    override fun onLoad() {
        instance = this
    }

    override fun onEnable() {
        saveDefaultConfig()

        configManager = ConfigManager(this)
        playerDataStore = PlayerDataStore(this)
        magicItemResolver = MagicItemResolver(this)
        itemRegistry = ItemRegistry(configManager, magicItemResolver)
        capacityService = CapacityService(configManager, itemRegistry, playerDataStore)
        bagService = BagService(itemRegistry, magicItemResolver, capacityService, playerDataStore)
        menuService = MenuService(
            configManager,
            itemRegistry,
            magicItemResolver,
            capacityService,
            bagService,
            playerDataStore
        )

        reloadAll()

        registerCommands()
        server.pluginManager.registerEvents(menuService, this)
        server.pluginManager.registerEvents(AutoPickupListener(configManager, itemRegistry, bagService), this)
        server.pluginManager.registerEvents(PlayerDataListener(playerDataStore), this)

        playerDataStore.startAutoSave()

        logger.info("SneakyBagOfHolding enabled.")
    }

    override fun onDisable() {
        menuService.closeAllMenus()
        playerDataStore.saveDirty()
        logger.info("SneakyBagOfHolding disabled.")
    }

    fun reloadAll() {
        configManager.reload()
        magicItemResolver.initialize()
        itemRegistry.reload()
        menuService.closeAllMenus()
    }

    private fun registerCommands() {
        val boh = getCommand("boh") ?: run {
            logger.severe("Command 'boh' not defined in paper-plugin.yml")
            return
        }
        val executor = BohCommand(this, configManager, menuService, bagService, playerDataStore) { reloadAll() }
        boh.setExecutor(executor)
        boh.tabCompleter = executor
        for (alias in configManager.getSettings().commandAliases) {
            if (alias.equals("boh", ignoreCase = true)) continue
            val cmd = getCommand(alias)
            cmd?.setExecutor(executor)
            cmd?.tabCompleter = executor
        }
    }

    companion object {
        const val IDENTIFIER = "sneakybagofholding"
        lateinit var instance: SneakyBagOfHolding
            private set

        fun log(message: String) {
            instance.logger.info(message)
        }
    }
}
