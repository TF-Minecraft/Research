package net.tfminecraft.research.command;

import java.util.Collections;
import java.util.List;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import net.tfminecraft.research.Messages;
import net.tfminecraft.research.Research;

public final class CommandManager implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("reload");

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("research.admin")) {
            sender.sendMessage(Messages.get("command.no_permission"));
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(Messages.get("command.usage"));
            return true;
        }

        if ("reload".equalsIgnoreCase(args[0])) {
            boolean ok = Research.plugin.reloadAll();
            sender.sendMessage(ok ? Messages.get("reload.success") : Messages.get("reload.failed"));
            return true;
        }

        sender.sendMessage(Messages.get("command.unknown_subcommand"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("research.admin")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return SUBCOMMANDS;
        }
        return Collections.emptyList();
    }
}
