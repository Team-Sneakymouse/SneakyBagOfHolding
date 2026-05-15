package com.sneakybagofholding.commands

import com.sneakybagofholding.SneakyBagOfHolding
import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.gui.MenuService
import com.sneakybagofholding.service.BagService
import com.sneakybagofholding.storage.PlayerDataStore
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * Main `/boh` command: open menu, reload, capacity admin, give/take admin.
 */
class BohCommand(
    private val plugin: SneakyBagOfHolding,
    private val configManager: ConfigManager,
    private val menuService: MenuService,
    private val bagService: BagService,
    private val playerDataStore: PlayerDataStore,
    private val onReload: () -> Unit
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.isEmpty()) {
            if (sender !is Player) {
                sender.sendMessage("This command can only be used by players.")
                return true
            }
            if (!sender.hasPermission(PERM_USE)) {
                sender.sendMessage("You do not have permission.")
                return true
            }
            menuService.openMainMenu(sender)
            return true
        }
        when (args[0].lowercase()) {
            "reload" -> {
                if (!sender.hasPermission(PERM_RELOAD)) {
                    sender.sendMessage("You do not have permission.")
                    return true
                }
                onReload()
                sender.sendMessage("SneakyBagOfHolding config reloaded.")
                return true
            }
            "capacity" -> return handleCapacity(sender, args)
            "give" -> return handleGiveTake(sender, args, add = true)
            "take" -> return handleGiveTake(sender, args, add = false)
            else -> {
                sender.sendMessage("Usage: /$label [reload|capacity|give|take]")
                return true
            }
        }
    }

    private fun handleCapacity(sender: CommandSender, args: Array<out String>): Boolean {
        if (!sender.hasPermission(PERM_ADMIN_CAPACITY)) {
            sender.sendMessage("You do not have permission.")
            return true
        }
        if (args.size < 5) {
            sender.sendMessage("Usage: /boh capacity <player> item|category <id> <value>")
            return true
        }
        val target = Bukkit.getPlayer(args[1])
        if (target == null) {
            sender.sendMessage("Player not found.")
            return true
        }
        val type = args[2].lowercase()
        val id = args[3]
        val value = args[4].toIntOrNull()
        if (value == null) {
            sender.sendMessage("Value must be an integer.")
            return true
        }
        when (type) {
            "item" -> {
                if (configManager.getItems()[id] == null) {
                    sender.sendMessage("Unknown item: $id")
                    return true
                }
                bagService.setItemCapacityOverride(target.uniqueId, id, value)
            }
            "category" -> {
                if (configManager.getCategories()[id] == null) {
                    sender.sendMessage("Unknown category: $id")
                    return true
                }
                bagService.setCategoryCapacityOverride(target.uniqueId, id, value)
            }
            else -> {
                sender.sendMessage("Type must be item or category.")
                return true
            }
        }
        sender.sendMessage("Set $type capacity for ${target.name}: $id = $value")
        if (target.isOnline) menuService.refreshOpenMenu(target)
        return true
    }

    private fun handleGiveTake(sender: CommandSender, args: Array<out String>, add: Boolean): Boolean {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("You do not have permission.")
            return true
        }
        if (args.size < 4) {
            sender.sendMessage("Usage: /boh ${if (add) "give" else "take"} <player> <itemId> <amount>")
            return true
        }
        val target = Bukkit.getPlayer(args[1]) ?: run {
            sender.sendMessage("Player not found.")
            return true
        }
        val itemId = args[2]
        val amount = args[3].toIntOrNull() ?: run {
            sender.sendMessage("Amount must be an integer.")
            return true
        }
        if (configManager.getItems()[itemId] == null) {
            sender.sendMessage("Unknown item: $itemId")
            return true
        }
        val current = playerDataStore.get(target).getStored(itemId)
        val newAmount = if (add) current + amount else (current - amount).coerceAtLeast(0)
        val result = bagService.adminSetStored(target, itemId, newAmount)
        sender.sendMessage("${if (add) "Gave" else "Took"} storage; new stored amount: $result")
        menuService.refreshOpenMenu(target)
        return true
    }

    override fun onTabComplete(
        sender: CommandSender,
        command: Command,
        alias: String,
        args: Array<out String>
    ): List<String> {
        if (args.size == 1) {
            return listOf("reload", "capacity", "give", "take").filter { it.startsWith(args[0].lowercase()) }
        }
        if (args.size == 2 && args[0].lowercase() in listOf("capacity", "give", "take")) {
            return Bukkit.getOnlinePlayers().map { it.name }.filter { it.lowercase().startsWith(args[1].lowercase()) }
        }
        if (args.size == 3 && args[0].lowercase() == "capacity") {
            return listOf("item", "category").filter { it.startsWith(args[2].lowercase()) }
        }
        if (args.size == 4 && args[0].lowercase() == "capacity") {
            return when (args[2].lowercase()) {
                "item" -> configManager.getItems().keys.filter { it.lowercase().startsWith(args[3].lowercase()) }
                "category" -> configManager.getCategories().keys.filter { it.lowercase().startsWith(args[3].lowercase()) }
                else -> emptyList()
            }
        }
        if (args.size == 3 && args[0].lowercase() in listOf("give", "take")) {
            return configManager.getItems().keys.filter { it.lowercase().startsWith(args[2].lowercase()) }
        }
        return emptyList()
    }

    companion object {
        const val PERM_USE = "sneakybagofholding.use"
        const val PERM_RELOAD = "sneakybagofholding.reload"
        const val PERM_ADMIN = "sneakybagofholding.admin"
        const val PERM_ADMIN_CAPACITY = "sneakybagofholding.admin.capacity"
    }
}
