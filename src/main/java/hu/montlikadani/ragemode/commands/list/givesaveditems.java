package hu.montlikadani.ragemode.commands.list;

import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import hu.montlikadani.ragemode.RageMode;
import hu.montlikadani.ragemode.Utils;
import hu.montlikadani.ragemode.commands.ICommand;
import hu.montlikadani.ragemode.config.Configuration;
import hu.montlikadani.ragemode.gameUtils.GameUtils;

import static hu.montlikadani.ragemode.utils.Misc.hasPerm;
import static hu.montlikadani.ragemode.utils.Misc.sendMessage;

public class givesaveditems implements ICommand {

	@Override
	public boolean run(RageMode plugin, CommandSender sender, String[] args) {
		if (!hu.montlikadani.ragemode.config.ConfigValues.isSavePlayerData()) {
			sendMessage(sender, RageMode.getLang().get("commands.givesaveditems.not-enabled"));
			return false;
		}

		if (!hasPerm(sender, "ragemode.admin.givesaveditems")) {
			sendMessage(sender, RageMode.getLang().get("no-permission"));
			return false;
		}

		if (args.length < 2) {
			sendMessage(sender, RageMode.getLang().get("missing-arguments", "%usage%", "/rm givesaveditems <player> [true]"));
			return false;
		}

		Player target = Bukkit.getPlayer(args[1]);
		if (target == null) {
			sendMessage(sender, RageMode.getLang().get("player-non-existent"));
			return false;
		}

		if (GameUtils.isPlayerPlaying(target)) {
			sendMessage(sender, RageMode.getLang().get("commands.givesaveditems.player-is-in-game", "%player%", args[1]));
			return false;
		}

		FileConfiguration datas = plugin.getConfiguration().getDatasCfg();
		if (!datas.contains("datas." + args[1])) {
			sendMessage(sender, RageMode.getLang().get("commands.givesaveditems.player-not-found-in-data-file", "%player%", args[1]));
			return false;
		}

		for (String names : datas.getConfigurationSection("datas").getKeys(false)) {
			if (names.equalsIgnoreCase(args[1])) {
				Utils.clearPlayerInventory(target);

				List<?> contentList = datas.getList("datas." + names + ".contents");
				target.getInventory().setContents(contentList.toArray(new ItemStack[contentList.size()]));

				List<?> armorList = datas.getList("datas." + names + ".armor-contents");
				target.getInventory().setArmorContents(armorList.toArray(new ItemStack[armorList.size()]));

				target.setExp(datas.getInt("datas." + names + ".exp"));
				target.setLevel(datas.getInt("datas." + names + ".level"));
				target.setGameMode(GameMode.valueOf(datas.getString("datas." + names + ".game-mode")));
				break;
			}
		}

		// Confirmation to remove from file
		if (args.length == 3) {
			if (args[2].equalsIgnoreCase("true") || args[2].equalsIgnoreCase("yes")) {
				datas.set("datas." + args[1], null);
				Configuration.saveFile(datas, plugin.getConfiguration().getDatasFile());
			}
		}

		return true;
	}
}
