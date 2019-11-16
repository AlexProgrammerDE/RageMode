package hu.montlikadani.ragemode.gameLogic;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import hu.montlikadani.ragemode.RageMode;

public class Bonus {

	public void addKillBonus(Player player) {
		if (player == null) {
			return;
		}

		if (ThreadLocalRandom.current().nextInt(0, 100) > RageMode.getInstance().getConfiguration().getCV()
				.getKillBonusChance()) {
			return;
		}

		List<String> list = RageMode.getInstance().getConfiguration().getCV().getKillBonuses();
		if (list == null) {
			return;
		}

		for (String b : list) {
			if (!b.startsWith("effect:")) {
				continue;
			}

			b = b.replace("effect:", "");

			String[] split = b.split(":");

			PotionEffectType effect = null;
			if (split.length > 1) {
				effect = PotionEffectType.getByName(split[0].toUpperCase());
			}

			if (effect == null) {
				continue;
			}

			PotionEffect pe = new PotionEffect(effect, (split.length > 2 ? Integer.parseInt(split[1]) : 5) * 20,
					(split.length > 3 ? Integer.parseInt(split[2]) : 10), true);
			player.addPotionEffect(pe);
		}
	}

	public int getPointBonus(Player player) {
		if (player == null) {
			return 0;
		}

		if (ThreadLocalRandom.current().nextInt(0, 100) > RageMode.getInstance().getConfiguration().getCV()
				.getKillBonusChance()) {
			return 0;
		}

		List<String> list = RageMode.getInstance().getConfiguration().getCV().getKillBonuses();
		if (list == null) {
			return 0;
		}

		String[] split = null;
		for (String b : list) {
			if (!b.startsWith("points:")) {
				continue;
			}

			b = b.replace("points:", "");

			split = b.split(":");

			if (split.length == 1) {
				int amount = Integer.parseInt(split[1]);
				return amount;
			}
		}

		return 0;
	}
}