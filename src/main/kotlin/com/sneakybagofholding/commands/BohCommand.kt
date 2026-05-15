package com.sneakybagofholding.commands

import com.sneakybagofholding.config.ConfigManager
import com.sneakybagofholding.gui.MenuService
import com.sneakybagofholding.service.BagService
import com.sneakybagofholding.storage.PlayerDataStore
import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Main `/boh` command: open menu, reload, capacity admin, give/take admin.
 *
 * Registered via [org.bukkit.plugin.java.JavaPlugin.registerCommand] (Paper plugins).
 */
class BohCommand(
    private val configManager: ConfigManager,
    private val menuService: MenuService,
    private val bagService: BagService,
    private val playerDataStore: PlayerDataStore,
    private val onReload: () -> Unit
) : BasicCommand {

    override fun permission(): String = PERM_USE

    override fun execute(stack: CommandSourceStack, args: Array<out String>) {
        val sender = stack.sender
        if (args.isEmpty()) {
            val player = sender as? Player ?: run {
                sender.sendMessage("This command can only be used by players.")
                return
            }
            if (!sender.hasPermission(PERM_USE)) {
                sender.sendMessage("You do not have permission.")
                return
            }
            menuService.openMainMenu(player)
            return
        }
        when (args[0].lowercase()) {
            "reload" -> {
                if (!sender.hasPermission(PERM_RELOAD)) {
                    sender.sendMessage("You do not have permission.")
                    return
                }
                onReload()
                sender.sendMessage("SneakyBagOfHolding config reloaded.")
            }
            "capacity" -> handleCapacity(sender, args)
            "give" -> handleGiveTake(sender, args, add = true)
            "take" -> handleGiveTake(sender, args, add = false)
            else -> sender.sendMessage("Usage: /boh [reload|capacity|give|take]")
        }
    }

    override fun suggest(stack: CommandSourceStack, args: Array<out String>): Collection<String> {
        val sender = stack.sender
        return when {
            args.size == 1 -> listOf("reload", "capacity", "give", "take")
                .filter { it.startsWith(args[0].lowercase()) }
            args.size == 2 && args[0].lowercase() in listOf("capacity", "give", "take") ->
                Bukkit.getOnlinePlayers().map { it.name }
                    .filter { it.lowercase().startsWith(args[1].lowercase()) }
            args.size == 3 && args[0].lowercase() == "capacity" ->
                listOf("item", "category", "global").filter { it.startsWith(args[2].lowercase()) }
            args.size == 4 && args[0].lowercase() == "capacity" ->
                when (args[2].lowercase()) {
                    "item" -> configManager.getItems().keys
                        .filter { it.lowercase().startsWith(args[3].lowercase()) }
                    "category" -> configManager.getCategories().keys
                        .filter { it.lowercase().startsWith(args[3].lowercase()) }
                    else -> emptyList()
                }
            args.size == 3 && args[0].lowercase() in listOf("give", "take") ->
                configManager.getItems().keys.filter { it.lowercase().startsWith(args[2].lowercase()) }
            else -> emptyList()
        }
    }

    private fun handleCapacity(sender: CommandSender, args: Array<out String>) {
        if (!sender.hasPermission(PERM_ADMIN_CAPACITY)) {
            sender.sendMessage("You do not have permission.")
            return
        }
        if (args.size < 4) {
            sender.sendMessage("Usage: /boh capacity <player> global <value>")
            sender.sendMessage("       /boh capacity <player> item|category <id> <value>")
            return
        }
        val target = Bukkit.getPlayer(args[1])
        if (target == null) {
            sender.sendMessage("Player not found.")
            return
        }
        val type = args[2].lowercase()
        when (type) {
            "global" -> {
                if (args.size < 4) {
                    sender.sendMessage("Usage: /boh capacity <player> global <value>")
                    return
                }
                val value = args[3].toIntOrNull() ?: run {
                    sender.sendMessage("Value must be an integer.")
                    return
                }
                bagService.setGlobalCapacityOverride(target.uniqueId, value)
                sender.sendMessage("Set global capacity for ${target.name}: $value")
            }
            "item", "category" -> {
                if (args.size < 5) {
                    sender.sendMessage("Usage: /boh capacity <player> $type <id> <value>")
                    return
                }
                val id = args[3]
                val value = args[4].toIntOrNull() ?: run {
                    sender.sendMessage("Value must be an integer.")
                    return
                }
                when (type) {
                    "item" -> {
                        if (configManager.getItems()[id] == null) {
                            sender.sendMessage("Unknown item: $id")
                            return
                        }
                        bagService.setItemCapacityOverride(target.uniqueId, id, value)
                    }
                    "category" -> {
                        if (configManager.getCategories()[id] == null) {
                            sender.sendMessage("Unknown category: $id")
                            return
                        }
                        bagService.setCategoryCapacityOverride(target.uniqueId, id, value)
                    }
                }
                sender.sendMessage("Set $type capacity for ${target.name}: $id = $value")
            }
            else -> {
                sender.sendMessage("Type must be item, category, or global.")
                return
            }
        }
        if (target.isOnline) menuService.refreshOpenMenu(target)
    }

    private fun handleGiveTake(sender: CommandSender, args: Array<out String>, add: Boolean) {
        if (!sender.hasPermission(PERM_ADMIN)) {
            sender.sendMessage("You do not have permission.")
            return
        }
        if (args.size < 4) {
            sender.sendMessage("Usage: /boh ${if (add) "give" else "take"} <player> <itemId> <amount>")
            return
        }
        val target = Bukkit.getPlayer(args[1]) ?: run {
            sender.sendMessage("Player not found.")
            return
        }
        val itemId = args[2]
        val amount = args[3].toIntOrNull() ?: run {
            sender.sendMessage("Amount must be an integer.")
            return
        }
        if (configManager.getItems()[itemId] == null) {
            sender.sendMessage("Unknown item: $itemId")
            return
        }
        val current = playerDataStore.get(target).getStored(itemId)
        val newAmount = if (add) current + amount else (current - amount).coerceAtLeast(0)
        val result = bagService.adminSetStored(target, itemId, newAmount)
        sender.sendMessage("${if (add) "Gave" else "Took"} storage; new stored amount: $result")
        menuService.refreshOpenMenu(target)
    }

    companion object {
        const val PERM_USE = "sneakybagofholding.use"
        const val PERM_RELOAD = "sneakybagofholding.reload"
        const val PERM_ADMIN = "sneakybagofholding.admin"
        const val PERM_ADMIN_CAPACITY = "sneakybagofholding.admin.capacity"
    }
}
