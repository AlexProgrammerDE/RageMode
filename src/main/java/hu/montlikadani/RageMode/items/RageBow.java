package hu.montlikadani.ragemode.items;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import hu.montlikadani.ragemode.RageMode;

public class RageBow {

	public static ItemStack getRageBow() {
		ItemStack bow = new ItemStack(Material.BOW);
		ItemMeta meta = bow.getItemMeta();
		meta.setDisplayName(getRageBowName());

		List<String> lore = RageMode.getInstance().getConfiguration().getCfg().getStringList("items.rageBow.lore");
		if (lore != null && !lore.isEmpty())
			meta.setLore(color(lore));

		Enchantment infinity = Enchantment.ARROW_INFINITE;
		meta.addEnchant(infinity, 1, false);
		bow.setItemMeta(meta);
		return bow;
	}

	private static List<String> color(List<String> lore) {
		List<String> clore = new ArrayList<>();
		for (String s : lore) {
			clore.add(RageMode.getLang().colors(s));
		}
		return clore;
	}

	public static String getRageBowName() {
		String iname = RageMode.getInstance().getConfiguration().getCfg().getString("items.rageBow.name");
		return iname != null && !iname.equals("") ? RageMode.getLang().colors(iname) : org.bukkit.ChatColor.GOLD + "RageBow";
	}
}
