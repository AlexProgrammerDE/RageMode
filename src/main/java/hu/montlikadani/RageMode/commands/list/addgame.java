package hu.montlikadani.ragemode.commands.list;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import hu.montlikadani.ragemode.RageMode;
import hu.montlikadani.ragemode.API.event.GameCreateEvent;
import hu.montlikadani.ragemode.config.Configuration;
import hu.montlikadani.ragemode.events.BungeeListener;
import hu.montlikadani.ragemode.gameLogic.Game;
import hu.montlikadani.ragemode.gameLogic.GameSpawnGetter;
import hu.montlikadani.ragemode.gameUtils.GameUtils;
import hu.montlikadani.ragemode.utils.ICommand;

import static hu.montlikadani.ragemode.utils.Message.hasPerm;
import static hu.montlikadani.ragemode.utils.Message.sendMessage;

public class addgame extends ICommand {

	@Override
	public boolean run(RageMode plugin, CommandSender sender, Command cmd, String[] args) {
		if (!(sender instanceof Player)) {
			sendMessage(sender, RageMode.getLang().get("in-game-only"));
			return false;
		}

		Player p = (Player) sender;
		if (!hasPerm(p, "ragemode.admin.addgame")) {
			sendMessage(p, RageMode.getLang().get("no-permission"));
			return false;
		}

		if (args.length < 3) {
			sendMessage(p, RageMode.getLang().get("missing-arguments", "%usage%", "/rm addgame <gameName> <maxPlayers>"));
			return false;
		}

		int x;
		try {
			x = Integer.parseInt(args[2]);
		} catch (NumberFormatException e) {
			sendMessage(p, RageMode.getLang().get("not-a-number", "%wrong-number%", args[2]));
			return false;
		}

		String game = args[1];
		if (GameUtils.isGameWithNameExists(game)) {
			sendMessage(p, RageMode.getLang().get("setup.addgame.already-exists", "%game%", game));
			return false;
		}

		if (!GameUtils.checkName(p, game)) {
			return false;
		}

		if (x < 2) {
			sendMessage(p, RageMode.getLang().get("setup.at-least-two"));
			return false;
		}

		if (plugin.getConfiguration().getCV().isBungee())
			plugin.getServer().getPluginManager().registerEvents(new BungeeListener(game), plugin);

		plugin.getGames().add(new Game(game));

		plugin.getSpawns().add(new GameSpawnGetter(game));

		GameCreateEvent event = new GameCreateEvent(game, Integer.parseInt(args[2]));
		Bukkit.getPluginManager().callEvent(event);

		plugin.getConfiguration().getArenasCfg().set("arenas." + game + ".maxplayers", Integer.parseInt(args[2]));
		plugin.getConfiguration().getArenasCfg().set("arenas." + game + ".world", p.getWorld().getName());
		Configuration.saveFile(plugin.getConfiguration().getArenasCfg(), plugin.getConfiguration().getArenasFile());

		sendMessage(p, RageMode.getLang().get("setup.addgame.success-added", "%game%", game));
		return false;
	}
}