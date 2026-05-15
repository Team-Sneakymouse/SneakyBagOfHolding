package com.sneakybagofholding.commands

import com.sneakybagofholding.SneakyBagOfHolding
import org.bukkit.command.Command

/**
 * Base class for all plugin commands.
 * Provides common setup and permission handling.
 *
 * @property name The name of the command
 */
abstract class CommandBase(name: String) : Command(name) {

    init {
        this.permission = "${SneakyBagOfHolding.IDENTIFIER}.command.$name"
    }

}