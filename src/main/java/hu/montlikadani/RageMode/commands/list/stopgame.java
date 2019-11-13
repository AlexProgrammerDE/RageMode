package hu.montlikadani.ragemode.commands.list;

import java.util.Iterator;
import java.util.Map.Entry;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import hu.montlikadani.ragemode.RageMode;
import hu.montlikadani.ragemode.gameLogic.Game;
import hu.montlikadani.ragemode.gameUtils.GameUtils;
import hu.montlikadani.ragemode.managers.PlayerManager;
import hu.montlikadani.ragemode.scores.RageScores;
import hu.montlikadani.ragemode.signs.SignCreator;

import static hu.montlikadani.ragemode.utils.Misc.hasPerm;
import static hu.montlikadani.ragemode.utils.Misc.sendMessage;

public class stopgame {

	public boolean run(CommandSender sender, String[] args) {
		if (!hasPerm(sender, "ragemode.admin.stopgame")) {
			sendMessage(sender, RageMode.getLang().get("no-permission"));
			return false;
		}

		if (args.length >= 2) {
			String game = args[1];
			Game g = GameUtils.getGame(game);

			if (!g.isGameRunning()) {
				sendMessage(sender, RageMode.getLang().get("game.not-running"));
				return false;
			}

			RageScores.calculateWinner(game, g.getPlayersFromList());

			for (Iterator<PlayerManager> it = g.getPlayersFromList().iterator(); it.hasNext();) {
				Player player = it.next().getPlayer();

				player.removeMetadata("killedWith", RageMode.getInstance());
				sendMessage(player, RageMode.getLang().get("game.stopped", "%game%", game));
				g.removePlayer(player);
			}

			for (Iterator<Entry<Player, PlayerManager>> it = g.getSpectatorPlayers().entrySet().iterator(); it
					.hasNext();) {
				Player pl = it.next().getKey();
				g.removeSpectatorPlayer(pl);
			}

			g.setGameNotRunning();
			GameUtils.setStatus(game, null);
			SignCreator.updateAllSigns(game);
		} else {
			sendMessage(sender,
					RageMode.getLang().get("missing-arguments", "%usage%", "/rm " + args[0] + " <gameName>"));
			return false;
		}

		return true;
	}
}
