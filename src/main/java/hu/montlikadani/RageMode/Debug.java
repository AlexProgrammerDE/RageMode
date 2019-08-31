package hu.montlikadani.ragemode;

import java.util.logging.Level;

import org.bukkit.Bukkit;

public class Debug {

	public static void throwMsg() {
		logConsole(Level.WARNING, "There was an error. Please report it here:\nhttps://github.com/montlikadani/RageMode/issues");
		return;
	}

	/**
	 * Logging to console to write debug messages
	 * <p>Using the <b>Level.INFO</b> level by default
	 * @param msg Error message
	 */
	public static void logConsole(String msg) {
		logConsole(Level.INFO, msg);
	}

	/**
	 * Logging to console to write debug messages
	 * @param lvl Logging level
	 * @param msg Error message
	 */
	public static void logConsole(Level lvl, String msg) {
		if (msg != null && !msg.equals("") && RageMode.getInstance().getConfiguration().getCV().isLogConsole())
			Bukkit.getLogger().log(lvl, "[RageMode] " + msg);
	}

	/**
	 * Sends a message to the console with colors.
	 * @param msg String
	 */
	public static void sendMessage(String msg) {
		Bukkit.getConsoleSender().sendMessage(RageMode.getLang().colors(msg));
	}
}