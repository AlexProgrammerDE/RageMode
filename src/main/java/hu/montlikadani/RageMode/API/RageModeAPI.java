package hu.montlikadani.ragemode.API;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import hu.montlikadani.ragemode.RageMode;

public class RageModeAPI {

	/**
	 * Returns RageMode as a plugin
	 * @return Plugin
	 */
	public static Plugin getPlugin() {
		if (JavaPlugin.getPlugin(RageMode.class) == null) {
			throw new NullPointerException("plugin is null");
		}

		return JavaPlugin.getPlugin(RageMode.class);
	}

	/**
	 * Gets the RageMode instance
	 * @return Instance
	 */
	public static RageMode getRageMode() {
		return RageMode.getInstance();
	}
}
