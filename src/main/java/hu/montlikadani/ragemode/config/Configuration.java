package hu.montlikadani.ragemode.config;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import hu.montlikadani.ragemode.Debug;
import hu.montlikadani.ragemode.RageMode;

public class Configuration {

	private RageMode plugin;
	private FileConfiguration config, arenas, rewards, datas, items;
	private File config_file, arenas_file, rewards_file, datas_file, items_file;

	private double configVersion = 1.8;

	public Configuration(RageMode plugin) {
		this.plugin = plugin;
	}

	private void loadFiles() {
		File folder = plugin.getFolder();
		if (config_file == null) {
			config_file = new File(folder, "config.yml");
		}

		if (arenas_file == null) {
			arenas_file = new File(folder, "arenas.yml");
		}

		if (rewards_file == null) {
			rewards_file = new File(folder, "rewards.yml");
		}

		if (datas_file == null) {
			datas_file = new File(folder, "datas.yml");
		}

		if (items_file == null) {
			items_file = new File(folder, "items.yml");
		}
	}

	public void loadConfig() {
		loadFiles();

		try {
			if (config_file.exists()) {
				config = YamlConfiguration.loadConfiguration(config_file);
			} else {
				config = createFile(config_file, "config.yml", false);
			}

			ConfigValues.loadValues(new FileConfig(config));

			if (!config.isSet("config-version") || !config.get("config-version").equals(configVersion)) {
				Debug.logConsole(Level.WARNING,
						"Found outdated configuration (config.yml)! (Your version: {0} | Newest version: {1})",
						config.getString("config-version"), String.valueOf(configVersion));
			}

			if (arenas_file.exists()) {
				arenas = YamlConfiguration.loadConfiguration(arenas_file);
				loadFile(arenas, arenas_file);
				saveFile(arenas, arenas_file);
			} else {
				arenas = createFile(arenas_file, "arenas.yml", true);
			}

			if (ConfigValues.isRewardEnabled()) {
				if (rewards_file.exists()) {
					rewards = YamlConfiguration.loadConfiguration(rewards_file);
					loadFile(rewards, rewards_file);
				} else {
					rewards = createFile(rewards_file, "rewards.yml", false);
				}
			}

			if (ConfigValues.isSavePlayerData()) {
				if (datas_file.exists()) {
					datas = YamlConfiguration.loadConfiguration(datas_file);
					loadFile(datas, datas_file);
					saveFile(datas, datas_file);
				} else {
					datas = createFile(datas_file, "datas.yml", true);
				}
			}

			if (items_file.exists()) {
				items = YamlConfiguration.loadConfiguration(items_file);
				loadFile(items, items_file);
			} else {
				items = createFile(items_file, "items.yml", false);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	FileConfiguration createFile(File file, String name, boolean newFile) {
		if (newFile) {
			try {
				file.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		} else {
			plugin.saveResource(name, false);
		}

		plugin.getLogger().log(Level.INFO, "{0} file created!", name);
		return YamlConfiguration.loadConfiguration(file);
	}

	public FileConfiguration getCfg() {
		return config;
	}

	public FileConfiguration getArenasCfg() {
		return arenas;
	}

	public FileConfiguration getRewardsCfg() {
		return rewards;
	}

	public FileConfiguration getDatasCfg() {
		return datas;
	}

	public File getCfgFile() {
		return config_file;
	}

	public File getArenasFile() {
		return arenas_file;
	}

	public File getRewardsFile() {
		return rewards_file;
	}

	public File getDatasFile() {
		return datas_file;
	}

	public FileConfiguration getItemsCfg() {
		return items;
	}

	public File getItemsFile() {
		return items_file;
	}

	public double getConfigVersion() {
		return configVersion;
	}

	public static void saveFile(FileConfiguration c, File f) {
		try {
			c.save(f);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void loadFile(FileConfiguration c, File f) {
		try {
			c.load(f);
		} catch (IOException | InvalidConfigurationException e) {
			e.printStackTrace();
		}
	}
}