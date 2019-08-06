package hu.montlikadani.ragemode.commands;

import java.io.IOException;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import hu.montlikadani.ragemode.RageMode;
import hu.montlikadani.ragemode.gameLogic.PlayerList;
import hu.montlikadani.ragemode.runtimeRPP.RuntimeRPPManager;
import hu.montlikadani.ragemode.scores.RetPlayerPoints;
import hu.montlikadani.ragemode.statistics.MySQLStats;
import hu.montlikadani.ragemode.statistics.YAMLStats;

public class Points extends RmCommand {

	private enum Actions {
		Set, Add, Take;
	}

	@Override
	public boolean run(CommandSender sender, String[] args) {
		if (sender instanceof Player && !hasPerm(sender, "ragemode.admin.points")) {
			sendMessage(sender, RageMode.getLang().get("no-permission"));
			return false;
		}

		if (args.length < 4) {
			sendMessage(sender, RageMode.getLang().get("missing-arguments", "%usage%", "/rm points set/add/take <player> <amount>"));
			return false;
		}

		Actions action = Actions.Add;

		switch (args[1].toLowerCase()) {
		case "set":
			action = Actions.Set;
			break;
		case "add":
			action = Actions.Add;
			break;
		case "take":
			action = Actions.Take;
			break;
		default:
			break;
		}

		Player target = Bukkit.getPlayer(args[2]);
		if (target == null) {
			sendMessage(sender, RageMode.getLang().get("commands.points.player-not-found"));
			return false;
		}

		if (PlayerList.isPlayerPlaying(target.getUniqueId().toString())) {
			sendMessage(sender, RageMode.getLang().get("commands.points.player-is-in-game", "%player%", args[2]));
			return false;
		}

		RetPlayerPoints rpp = RuntimeRPPManager.getRPPForPlayer(target.getUniqueId().toString());
		if (rpp == null) {
			sendMessage(sender, RageMode.getLang().get("not-played-yet", "%player%", args[2]));
			return false;
		}

		int amount = 0;
		try {
			amount = Integer.parseInt(args[3]);
		} catch (NumberFormatException e) {
			sendMessage(sender, RageMode.getLang().get("not-a-number", "%number%", args[3]));
			return false;
		}

		if (amount <= 0) {
			sendMessage(sender, RageMode.getLang().get("commands.points.amount-not-less"));
			return false;
		}

		switch (action) {
		case Set:
			rpp.setPoints(amount);
			break;
		case Add:
			rpp.addPoints(amount);
			break;
		case Take:
			rpp.takePoints(amount);
			break;
		default:
			break;
		}

		if (YAMLStats.getFile() != null && YAMLStats.getFile().exists()) {
			YAMLStats.getConf().set("data." + target.getUniqueId() + ".score", rpp.getPoints());
			try {
				YAMLStats.getConf().save(YAMLStats.getFile());
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else
			MySQLStats.addPlayerStatistics(rpp, RageMode.getMySQL());

		sendMessage(sender, RageMode.getLang().get("commands.points.changed", "%amount%", amount, "%new%", rpp.getPoints()));
		return false;
	}
}
