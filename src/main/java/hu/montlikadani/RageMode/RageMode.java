package hu.montlikadani.ragemode;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import com.google.common.base.StandardSystemProperty;

import hu.montlikadani.ragemode.MinecraftVersion.Version;
import hu.montlikadani.ragemode.commands.RmCommand;
import hu.montlikadani.ragemode.commands.RmTabCompleter;
import hu.montlikadani.ragemode.config.ConfigValues;
import hu.montlikadani.ragemode.config.Configuration;
import hu.montlikadani.ragemode.config.Language;
import hu.montlikadani.ragemode.database.MySQLConnect;
import hu.montlikadani.ragemode.database.SQLConnect;
import hu.montlikadani.ragemode.events.BungeeListener;
import hu.montlikadani.ragemode.events.EventListener;
import hu.montlikadani.ragemode.events.Listeners_1_8;
import hu.montlikadani.ragemode.events.Listeners_1_9;
import hu.montlikadani.ragemode.gameLogic.Game;
import hu.montlikadani.ragemode.gameLogic.GameSpawn;
import hu.montlikadani.ragemode.gameLogic.GameStatus;
import hu.montlikadani.ragemode.gameUtils.BungeeUtils;
import hu.montlikadani.ragemode.gameUtils.GameUtils;
import hu.montlikadani.ragemode.gameUtils.GetGames;
import hu.montlikadani.ragemode.holder.HoloHolder;
import hu.montlikadani.ragemode.metrics.Metrics;
import hu.montlikadani.ragemode.runtimePP.RuntimePPManager;
import hu.montlikadani.ragemode.scores.PlayerPoints;
import hu.montlikadani.ragemode.signs.SignConfiguration;
import hu.montlikadani.ragemode.signs.SignCreator;
import hu.montlikadani.ragemode.signs.SignScheduler;
import hu.montlikadani.ragemode.statistics.MySQLStats;
import hu.montlikadani.ragemode.statistics.SQLStats;
import hu.montlikadani.ragemode.statistics.YAMLStats;
import net.milkbowl.vault.economy.Economy;

public class RageMode extends JavaPlugin {

	private Configuration conf = null;
	private SignScheduler sign = null;
	private BungeeUtils bungee = null;
	private static Language lang = null;
	private static MySQLConnect mySQLConnect = null;
	private static SQLConnect sqlConnect = null;
	private static MinecraftVersion mcVersion = null;

	private Economy econ = null;

	private static RageMode instance = null;
	private BukkitTask signTask;

	private boolean hologram = false;
	private boolean vault = false;
	private static boolean isSpigot = false;

	private List<Game> games = new ArrayList<>();
	private List<GameSpawn> spawns = new ArrayList<>();

	@Override
	public void onEnable() {
		instance = this;

		try {
			if (!checkJavaVersion()) {
				getManager().disablePlugin(this);
				return;
			}

			try {
				Class.forName("org.spigotmc.SpigotConfig");
				isSpigot = true;
			} catch (ClassNotFoundException c) {
				isSpigot = false;
			}

			mcVersion = new MinecraftVersion();

			if (Version.isCurrentLower(Version.v1_8_R1)) {
				getLogger().log(Level.SEVERE, "[RageMode] This version is not supported by this plugin! Please use larger 1.8+");
				getManager().disablePlugin(this);
				return;
			}

			if (Version.isCurrentEqualOrLower(Version.v1_8_R3))
				getLogger().log(Level.INFO, "[RageMode] This version not fully supported by this plugin, so some options will not work.");

			conf = new Configuration(this);
			conf.loadConfig();

			lang = new Language(this);
			lang.loadLanguage(ConfigValues.getLang());

			if (getManager().isPluginEnabled("HolographicDisplays")) {
				hologram = true;
				HoloHolder.initHoloHolder();
			} else
				hologram = false;

			if (getManager().isPluginEnabled("Vault")) {
				vault = true;
				initEconomy();
			} else
				vault = false;

			if (getManager().isPluginEnabled("PlaceholderAPI"))
				new Placeholder().register();

			if (ConfigValues.isBungee()) {
				bungee = new BungeeUtils(this);
				getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
			}

			if (ConfigValues.isCheckForUpdates()) {
				Debug.logConsole(checkVersion("console"));
			}

			registerListeners();
			registerCommands();

			switch (ConfigValues.getDatabaseType()) {
			case "mysql":
				connectMySQL();
				break;
			case "sql":
			case "sqlite":
				connectSQL();
				break;
			default:
				initYamlStatistics();
				break;
			}

			RuntimePPManager.loadPPListFromDatabase();

			if (ConfigValues.isSignsEnable()) {
				sign = new SignScheduler(this);
				getManager().registerEvents(sign, this);

				SignConfiguration.initSignConfiguration();
				SignCreator.loadSigns();

				signTask = getServer().getScheduler().runTaskLater(this, sign, 40L);
			}

			if (conf.getArenasCfg().contains("arenas")) {
				for (String game : GetGames.getGameNames()) {
					if (game != null) {
						Game g = new Game(game);
						games.add(g);

						if (ConfigValues.isBungee())
							getManager().registerEvents(new BungeeListener(game), this);

						spawns.add(new GameSpawn(g));

						if (ConfigValues.isSignsEnable())
							SignCreator.updateAllSigns(game);

						// Loads the game locker
						if (conf.getArenasCfg().getBoolean("arenas." + game + ".lock", false)) {
							GameUtils.setStatus(game, GameStatus.NOTREADY);
						} else
							GameUtils.setStatus(game, GameStatus.READY);

						Debug.logConsole("Loaded {0} game!", game);
					}
				}
			}

			// Metrics has changed the JsonObject and causing the break, so disable under 1.8.5
			if (Version.isCurrentEqualOrHigher(Version.v1_8_R3)) {
				Metrics metrics = new Metrics(this);
				if (metrics.isEnabled()) {
					metrics.addCustomChart(
							new Metrics.SimplePie("games_amount", () -> String.valueOf(GetGames.getConfigGamesCount())));

					metrics.addCustomChart(new Metrics.SimplePie("total_players", () -> {
						int totalPlayers = 0;
						switch (ConfigValues.getDatabaseType()) {
						case "mysql":
							totalPlayers = MySQLStats.getAllPlayerStatistics().size();
							break;
						case "sql":
						case "sqlite":
							totalPlayers = SQLStats.getAllPlayerStatistics().size();
							break;
						case "yaml":
							totalPlayers = YAMLStats.getAllPlayerStatistics().size();
							break;
						default:
							break;
						}

						return String.valueOf(totalPlayers);
					}));

					metrics.addCustomChart(new Metrics.SimplePie("statistic_type", ConfigValues::getDatabaseType));

					Debug.logConsole("Metrics enabled.");
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
			Debug.throwMsg();
		}
	}

	@Override
	public void onDisable() {
		if (instance == null) return;

		GameUtils.stopAllGames();

		getServer().getScheduler().cancelTasks(instance);
		HandlerList.unregisterAll(this);
		sign = null;
		instance = null;
	}

	private void initYamlStatistics() {
		YAMLStats.initS();
		YAMLStats.loadPlayerStatistics();
	}

	private void connectMySQL() {
		try {
			Class.forName("com.mysql.jdbc.Driver");
		} catch (ClassNotFoundException c) {
			c.printStackTrace();
			Debug.logConsole(Level.WARNING, "Could not connect to the MySQL database. No MySql found.");
			return;
		}

		String host = ConfigValues.getHost();
		String port = ConfigValues.getPort();
		String database = ConfigValues.getDatabase();
		String username = ConfigValues.getUsername();
		String password = ConfigValues.getPassword();
		String characterEnc = ConfigValues.getEncoding().isEmpty() ? "UTF-8" : ConfigValues.getEncoding();
		String prefix = ConfigValues.getTablePrefix().isEmpty() ? "rm_" : ConfigValues.getTablePrefix();
		boolean serverCertificate = ConfigValues.isCertificate();
		boolean useUnicode = ConfigValues.isUnicode();
		boolean autoReconnect = ConfigValues.isAutoReconnect();
		boolean useSSL = ConfigValues.isUseSSL();

		mySQLConnect = new MySQLConnect(host, port, database, username, password, serverCertificate, useUnicode,
				characterEnc, autoReconnect, useSSL, prefix);
		if (mySQLConnect == null) {
			return;
		}

		if (!mySQLConnect.isValid()) {
			return;
		}

		MySQLStats.loadPlayerStatistics(mySQLConnect);

		if (mySQLConnect.isConnected()) {
			Debug.logConsole("Successfully connected to MySQL!");
		}
	}

	private void connectSQL() {
		try {
			Class.forName("org.sqlite.JDBC");
		} catch (ClassNotFoundException c) {
			c.printStackTrace();
			Debug.logConsole(Level.WARNING, "Could not connect to the SQL database. No Sql found.");
			return;
		}

		File sqlFile = new File(getFolder(), ConfigValues.getSqlFileName() + ".db");
		if (!sqlFile.exists()) {
			try {
				sqlFile.createNewFile();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		sqlConnect = new SQLConnect(sqlFile, ConfigValues.getSqlTablePrefix());
		if (sqlConnect == null) {
			return;
		}

		if (!sqlConnect.isValid()) {
			return;
		}

		SQLStats.loadPlayerStatistics(sqlConnect);

		if (sqlConnect.isConnected()) {
			Debug.logConsole("Successfully connected to SQL!");
		}
	}

	private void loadDatabases() {
		switch (ConfigValues.getDatabaseType()) {
		case "mysql":
			if (mySQLConnect == null || !mySQLConnect.isConnected()) {
				connectMySQL();
			} else {
				MySQLStats.loadPlayerStatistics(mySQLConnect);
			}

			break;
		case "sql":
		case "sqlite":
			if (sqlConnect == null || !sqlConnect.isConnected()) {
				connectSQL();
			} else {
				SQLStats.loadPlayerStatistics(sqlConnect);
			}

			break;
		default:
			YAMLStats.initS();
			YAMLStats.loadPlayerStatistics();
			break;
		}

		RuntimePPManager.loadPPListFromDatabase();
	}

	private void initEconomy() {
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		if (rsp == null)
			return;

		econ = rsp.getProvider();
		if (econ == null)
			return;
	}

	public synchronized boolean reload() {
		HandlerList.unregisterAll(this);

		for (String game : GetGames.getGameNames()) {
			if (game != null && GameUtils.getGame(game) != null && GameUtils.getGame(game).isGameRunning()) {
				GameUtils.stopGame(GameUtils.getGame(game), false);
				GameUtils.broadcastToGame(game, RageMode.getLang().get("game.game-stopped-for-reload"));
			}
		}

		if (signTask != null) {
			signTask.cancel();
			signTask = null;
		}

		games.clear();
		spawns.clear();

		conf.loadConfig();
		lang.loadLanguage(ConfigValues.getLang());

		if (conf.getArenasCfg().contains("arenas")) {
			for (String game : GetGames.getGameNames()) {
				if (game != null) {
					Game g = new Game(game);
					games.add(g);

					if (ConfigValues.isBungee())
						getManager().registerEvents(new BungeeListener(game), this);

					spawns.add(new GameSpawn(g));

					SignCreator.updateAllSigns(game);
				}
			}
		}

		if (ConfigValues.isSignsEnable()) {
			getManager().registerEvents(sign, this);
			SignConfiguration.initSignConfiguration();

			signTask = getServer().getScheduler().runTaskLater(this, sign, 40L);
		}

		registerListeners();
		loadDatabases();
		SignCreator.loadSigns();

		if (hologram)
			HoloHolder.initHoloHolder();

		return true;
	}

	private void registerCommands() {
		getCommand("ragemode").setExecutor(new RmCommand());
		getCommand("ragemode").setTabCompleter(new RmTabCompleter());
	}

	private void registerListeners() {
		getManager().registerEvents(new EventListener(this), this);
		if (Version.isCurrentEqualOrLower(Version.v1_8_R3))
			getManager().registerEvents(new Listeners_1_8(), this);
		else
			getManager().registerEvents(new Listeners_1_9(), this);
	}

	public File getFolder() {
		File dataFolder = getDataFolder();
		if (!dataFolder.exists())
			dataFolder.mkdir();

		return dataFolder;
	}

	/**
	 * Gets the given player statistics from database.
	 * @param uuid Player uuid
	 * @return {@link PlayerPoints}
	 */
	public static PlayerPoints getPPFromDatabase(UUID uuid) {
		switch (ConfigValues.getDatabaseType()) {
		case "sql":
		case "sqlite":
			return SQLStats.getPlayerStatsFromData(uuid);
		case "mysql":
			return MySQLStats.getPlayerStatsFromData(uuid);
		default:
			return YAMLStats.getPlayerStatsFromData(uuid);
		}
	}

	/**
	 * Removes a game from the list.
	 * @param game Game
	 */
	public void removeGame(Game game) {
		removeGame(game.getName());
	}

	/**
	 * Removes a game from the list by name.
	 * @param name Game name
	 */
	public void removeGame(String name) {
		for (Iterator<Game> gt = games.iterator(); gt.hasNext();) {
			if (gt.next().getName().equalsIgnoreCase(name)) {
				gt.remove();
				break;
			}
		}
	}

	/**
	 * Removes the given game all spawns.
	 * @param game Game
	 */
	public void removeSpawn(Game game) {
		removeSpawn(game.getName());
	}

	/**
	 * Removes the given game name all spawns.
	 * @param name Game name
	 */
	public void removeSpawn(String name) {
		for (Iterator<GameSpawn> it = spawns.iterator(); it.hasNext();) {
			if (it.next().getGame().getName().equalsIgnoreCase(name)) {
				it.remove();
			}
		}
	}

	/**
	 * Gets the plugin instance
	 * @return RageMode instance
	 */
	public static RageMode getInstance() {
		return instance;
	}

	/**
	 * Gets the {@link MySQLConnect} class
	 * @return mySQLConnect class
	 */
	public static MySQLConnect getMySQL() {
		return mySQLConnect;
	}

	/**
	 * Gets the {@link SQLConnect} class
	 * @return SQLConnect class
	 */
	public static SQLConnect getSQL() {
		return sqlConnect;
	}

	/**
	 * Gets the {@link Language} class
	 * @return Language class
	 */
	public static Language getLang() {
		return lang;
	}

	/**
	 * Gets the {@link MinecraftVersion} class
	 * @return MinecraftVersion class
	 */
	public static MinecraftVersion getMCVersion() {
		return mcVersion;
	}

	public static boolean isSpigot() {
		return isSpigot;
	}

	public boolean isHologramEnabled() {
		return hologram;
	}

	public boolean isVaultEnabled() {
		return vault;
	}

	public Configuration getConfiguration() {
		return conf;
	}

	public BungeeUtils getBungeeUtils() {
		return bungee;
	}

	public SignScheduler getSignScheduler() {
		return sign;
	}

	public List<Game> getGames() {
		return games;
	}

	public List<GameSpawn> getSpawns() {
		return spawns;
	}

	private PluginManager getManager() {
		return getServer().getPluginManager();
	}

	public Economy getEconomy() {
		return econ;
	}

	private boolean checkJavaVersion() {
		try {
			if (Float.parseFloat(StandardSystemProperty.JAVA_CLASS_VERSION.value()) < 52.0) {
				Debug.logConsole(Level.WARNING, "You are using an older Java that is not supported. Please use 1.8 or higher versions!");
				return false;
			}
		} catch (NumberFormatException e) {
			Debug.logConsole(Level.WARNING, "Failed to detect Java version.");
			return false;
		}
		return true;
	}

	public String checkVersion(String sender) {
		String msg = "";
		String[] nVersion;
		String[] cVersion;
		String lineWithVersion;
		try {
			URL githubUrl = new URL("https://raw.githubusercontent.com/montlikadani/RageMode/master/src/main/resources/plugin.yml");
			lineWithVersion = "";
			BufferedReader br = new BufferedReader(new InputStreamReader(githubUrl.openStream()));
			String s;
			while ((s = br.readLine()) != null) {
				String line = s;
				if (line.toLowerCase().contains("version")) {
					lineWithVersion = line;
					break;
				}
			}

			String versionString = lineWithVersion.split(": ")[1];
			nVersion = versionString.split("\\.");
			double newestVersionNumber = Double.parseDouble(nVersion[0] + "." + nVersion[1]);
			cVersion = getDescription().getVersion().split("\\.");
			double currentVersionNumber = Double.parseDouble(cVersion[0] + "." + cVersion[1]);
			if (newestVersionNumber > currentVersionNumber) {
				if (sender.equals("player")) {
					msg = "&8&m&l--------------------------------------------------\n"
							+ "&a A new update is available for RageMode!&4 Version:&7 " + versionString
							+ "\n&6Download:&c &nhttps://www.spigotmc.org/resources/69169/"
							+ "\n&8&m&l--------------------------------------------------";
				} else if (sender.equals("console")) {
					msg = "New version (" + versionString + ") is available at https://www.spigotmc.org/resources/69169/";
				}
			} else if (sender.equals("console")) {
				msg = "You're running the latest version.";
			}
		} catch (Throwable e) {
			e.printStackTrace();
			Debug.logConsole(Level.WARNING,
					"Failed to compare versions. Please report it here:\nhttps://github.com/montlikadani/RageMode/issues");
		}

		return msg;
	}
}
