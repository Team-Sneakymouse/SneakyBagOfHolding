package com.sneakybagofholding

import com.sneakybagofholding.api.BagOfHoldingApi
import com.sneakybagofholding.api.BagOfHoldingApiImpl
import com.sneakybagofholding.capacity.CapacityService
import com.sneakybagofholding.commands.BohCommand
import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.gui.GuiDisplayMarker
import com.sneakybagofholding.gui.MenuService
import com.sneakybagofholding.listener.AutoPickupListener
import com.sneakybagofholding.listener.MagicSpellsHookListener
import com.sneakybagofholding.listener.PlayerDataListener
import org.bukkit.Bukkit
import com.sneakybagofholding.registry.ItemRegistry
import com.sneakybagofholding.registry.MagicItemResolver
import com.sneakybagofholding.service.BagService
import com.sneakybagofholding.storage.PlayerDataStore
import org.bukkit.plugin.ServicePriority
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
        Bukkit.getServicesManager().register(
            BagOfHoldingApi::class.java,
            BagOfHoldingApiImpl(bagService),
            this,
            ServicePriority.Normal,
        )
        menuService = MenuService(
            configManager,
            itemRegistry,
            magicItemResolver,
            capacityService,
            bagService,
            playerDataStore
        )

        configManager.reload()
        GuiDisplayMarker.init(this)
        registerCommands()
        server.pluginManager.registerEvents(menuService, this)
        server.pluginManager.registerEvents(AutoPickupListener(configManager, itemRegistry, bagService), this)
        server.pluginManager.registerEvents(PlayerDataListener(playerDataStore), this)
        server.pluginManager.registerEvents(MagicSpellsHookListener(this), this)

        playerDataStore.startAutoSave()

        // MagicSpells may enable after us; hook now or when PluginEnableEvent fires
        if (!magicItemResolver.initialize()) {
            Bukkit.getScheduler().runTaskLater(this, Runnable { retryMagicSpellsHook() }, 1L)
        } else {
            itemRegistry.reload()
        }

        logger.info("SneakyBagOfHolding enabled.")
    }

    override fun onDisable() {
        Bukkit.getServicesManager().unregisterAll(this)
        menuService.closeAllMenus()
        playerDataStore.saveDirty()
        logger.info("SneakyBagOfHolding disabled.")
    }

    fun reloadAll() {
        configManager.reload()
        GuiDisplayMarker.init(this)
        magicItemResolver.initialize()
        itemRegistry.reload()
        menuService.closeAllMenus()
    }

    private fun retryMagicSpellsHook() {
        if (magicItemResolver.isAvailable()) return
        if (magicItemResolver.initialize()) {
            itemRegistry.reload()
        }
    }

    private fun registerCommands() {
        val handler = BohCommand(configManager, menuService, bagService, capacityService, playerDataStore) { reloadAll() }
        val aliases = configManager.getSettings().commandAliases
            .filter { !it.equals("boh", ignoreCase = true) }
        registerCommand(
            "boh",
            "Open the Bag of Holding or run admin subcommands",
            aliases,
            handler
        )
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
