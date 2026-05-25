package com.sneakybagofholding.commands

import com.sneakybagofholding.capacity.CapacityService
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
    private val capacityService: CapacityService,
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
            sender.sendMessage("Usage: /boh capacity <player> global <value|+delta|-delta>")
            sender.sendMessage("       /boh capacity <player> item|category <id> <value|+delta|-delta>")
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
                val parsed = parseCapacityValue(args[3]) ?: run {
                    sender.sendMessage("Value must be an integer, or +N / -N to adjust.")
                    return
                }
                val current = capacityService.getGlobalCapacity(target.uniqueId)
                val newValue = resolveCapacityValue(current, parsed)
                bagService.setGlobalCapacityOverride(target.uniqueId, newValue)
                sender.sendMessage(capacityFeedback("global", target.name, null, current, newValue, parsed))
            }
            "item", "category" -> {
                if (args.size < 5) {
                    sender.sendMessage("Usage: /boh capacity <player> $type <id> <value|+delta|-delta>")
                    return
                }
                val id = args[3]
                val parsed = parseCapacityValue(args[4]) ?: run {
                    sender.sendMessage("Value must be an integer, or +N / -N to adjust.")
                    return
                }
                val current = when (type) {
                    "item" -> {
                        val item = configManager.getItems()[id] ?: run {
                            sender.sendMessage("Unknown item: $id")
                            return
                        }
                        capacityService.getItemCapacity(target.uniqueId, item)
                    }
                    "category" -> {
                        if (configManager.getCategories()[id] == null) {
                            sender.sendMessage("Unknown category: $id")
                            return
                        }
                        capacityService.getCategoryCapacity(target.uniqueId, id)
                    }
                    else -> return
                }
                val newValue = resolveCapacityValue(current, parsed)
                when (type) {
                    "item" -> bagService.setItemCapacityOverride(target.uniqueId, id, newValue)
                    "category" -> bagService.setCategoryCapacityOverride(target.uniqueId, id, newValue)
                }
                sender.sendMessage(capacityFeedback(type, target.name, id, current, newValue, parsed))
            }
            else -> {
                sender.sendMessage("Type must be item, category, or global.")
                return
            }
        }
        if (target.isOnline) menuService.refreshOpenMenu(target)
    }

    /**
     * Absolute integer sets capacity; leading `+` or `-` adjusts from the player's current value
     * (override if set, otherwise config default).
     */
    private sealed interface CapacityValue {
        data class Absolute(val value: Int) : CapacityValue
        data class Relative(val delta: Int) : CapacityValue
    }

    private fun parseCapacityValue(raw: String): CapacityValue? {
        val trimmed = raw.trim()
        if (trimmed.startsWith("+")) {
            return trimmed.drop(1).trim().toIntOrNull()?.let { CapacityValue.Relative(it) }
        }
        if (trimmed.startsWith("-")) {
            return trimmed.toIntOrNull()?.let { CapacityValue.Relative(it) }
        }
        return trimmed.toIntOrNull()?.let { CapacityValue.Absolute(it) }
    }

    private fun resolveCapacityValue(current: Int, parsed: CapacityValue): Int = when (parsed) {
        is CapacityValue.Absolute -> parsed.value.coerceAtLeast(0)
        is CapacityValue.Relative -> (current + parsed.delta).coerceAtLeast(0)
    }

    private fun capacityFeedback(
        kind: String,
        playerName: String,
        id: String?,
        previous: Int,
        newValue: Int,
        parsed: CapacityValue,
    ): String = when (parsed) {
        is CapacityValue.Absolute -> {
            if (id != null) {
                "Set $kind capacity for $playerName: $id = $newValue (was $previous)"
            } else {
                "Set $kind capacity for $playerName: $newValue (was $previous)"
            }
        }
        is CapacityValue.Relative -> {
            val deltaText = if (parsed.delta >= 0) "+${parsed.delta}" else parsed.delta.toString()
            if (id != null) {
                "Adjusted $kind capacity for $playerName: $id $deltaText → $newValue (was $previous)"
            } else {
                "Adjusted $kind capacity for $playerName: $deltaText → $newValue (was $previous)"
            }
        }
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
