package me.florian.varlight.command.commands;

import me.florian.varlight.VarLightPlugin;
import me.florian.varlight.command.ArgumentIterator;
import me.florian.varlight.command.VarLightCommand;
import me.florian.varlight.command.VarLightSubCommand;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VarLightCommandStepSize extends VarLightSubCommand {

    private final VarLightPlugin plugin;

    public VarLightCommandStepSize(VarLightPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "stepsize";
    }

    @Override
    public boolean execute(CommandSender sender, ArgumentIterator args) {
        VarLightCommand.assertPermission(sender, "varlight.admin");

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players may use this command!");
            return true;
        }

        if (!args.hasNext()) {
            return false;
        }

        final int newStepSize;

        try {
            newStepSize = args.parseNext(Integer::parseInt);
        } catch (NumberFormatException e) {
            return false;
        }

        try {
            plugin.setStepSize((Player) sender, newStepSize);
        } catch (IllegalArgumentException e) {
            sender.sendMessage(ChatColor.RED + VarLightCommand.getPrefixedMessage(e.getMessage()));
            return true;
        }

        VarLightCommand.sendPrefixedMessage(sender, String.format("Set your step size to %d", newStepSize));

        return true;
    }

    @Override
    public String getDescription() {
        return "Edit the Step size when using " + plugin.getLightUpdateItem().name();
    }

    @Override
    public String getSyntax() {
        return " <step size>";
    }
}
