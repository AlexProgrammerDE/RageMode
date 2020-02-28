package hu.montlikadani.ragemode;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.stream.Stream;

import org.bukkit.Material;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import hu.montlikadani.ragemode.ServerVersion.Version;
import hu.montlikadani.ragemode.commands.RmCommand;
import hu.montlikadani.ragemode.commands.RmTabCompleter;
import hu.montlikadani.ragemode.config.ConfigValues;
import hu.montlikadani.ragemode.config.Configuration;
import hu.montlikadani.ragemode.config.Language;
import hu.montlikadani.ragemode.database.DatabaseHandler;
import hu.montlikadani.ragemode.database.MySQLConnect;
import hu.montlikadani.ragemode.database.SQLConnect;
import hu.montlikadani.ragemode.events.BungeeListener;
import hu.montlikadani.ragemode.events.EventListener;
import hu.montlikadani.ragemode.events.GameListener;
import hu.montlikadani.ragemode.events.Listeners_1_8;
import hu.montlikadani.ragemode.events.Listeners_1_9;
import hu.montlikadani.ragemode.gameLogic.Game;
import hu.montlikadani.ragemode.gameLogic.GameSpawn;
import hu.montlikadani.ragemode.gameLogic.GameStatus;
import hu.montlikadani.ragemode.gameUtils.BungeeUtils;
import hu.montlikadani.ragemode.gameUtils.GameUtils;
import hu.montlikadani.ragemode.gameUtils.GetGames;
import hu.montlikadani.ragemode.holder.HoloHolder;
import hu.montlikadani.ragemode.items.ItemHandler;
import hu.montlikadani.ragemode.managers.BossbarManager;
import hu.montlikadani.ragemode.metrics.Metrics;
import hu.montlikadani.ragemode.runtimePP.RuntimePPManager;
import hu.montlikadani.ragemode.scores.PlayerPoints;
import hu.montlikadani.ragemode.signs.SignConfiguration;
import hu.montlikadani.ragemode.signs.SignCreator;
import hu.montlikadani.ragemode.storage.MySQLDB;
import hu.montlikadani.ragemode.storage.SQLDB;
import hu.montlikadani.ragemode.storage.YAMLDB;
import net.milkbowl.vault.economy.Economy;

public class RageMode extends JavaPlugin {

	private Configuration conf = null;
	private BungeeUtils bungee = null;
	private BossbarManager bossManager = null;

	private static RageMode instance = null;
	private static Language lang = null;
	private DatabaseHandler dbHandler = null;
	private static ServerVersion serverVersion = null;

	private Economy econ = null;

	private boolean hologram = false;
	private boolean vault = false;
	private static boolean isSpigot = false;

	private final List<Game> games = new ArrayList<>();
	private final List<GameSpawn> spawns = new ArrayList<>();
	private final ItemHandler[] gameItems = new ItemHandler[5];
	private final ItemHandler[] lobbyItems = new ItemHandler[2];

	@Override
	public void onEnable() {
		instance = this;

		serverVersion = new ServerVersion();

		if (Version.isCurrentLower(Version.v1_8_R1)) {
			getLogger().log(Level.SEVERE,
					"[RageMode] This version is not supported by this plugin! Please use larger 1.8+");
			getManager().disablePlugin(this);
			return;
		}

		try {
			Class.forName("org.spigotmc.SpigotConfig");
			isSpigot = true;
		} catch (ClassNotFoundException c) {
			isSpigot = false;
		}

		if (Version.isCurrentEqualOrLower(Version.v1_8_R3))
			getLogger().log(Level.INFO,
					"[RageMode] This version not fully supported by this plugin, so some options will not work.");

		conf = new Configuration(this);
		conf.loadConfig();

		lang = new Language(this);
		lang.loadLanguage(ConfigValues.getLang());

		loadHooks();

		if (ConfigValues.isBungee()) {
			bungee = new BungeeUtils(this);
			getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
		}

		if (ConfigValues.isCheckForUpdates()) {
			Debug.logConsole(checkVersion("console"));
		}

		registerListeners();
		registerCommands();
		connectDatabase();

		RuntimePPManager.loadPPListFromDatabase();
		loadGames();

		bossManager = new BossbarManager(this);

		if (ConfigValues.isSignsEnable()) {
			SignConfiguration.initSignConfiguration();
			SignCreator.loadSigns();
		}

		Metrics metrics = new Metrics(this, 5076);
		if (metrics.isEnabled()) {
			metrics.addCustomChart(new Metrics.SingleLineChart("amount_of_games", games::size));

			metrics.addCustomChart(new Metrics.SimplePie("total_players", () -> {
				int totalPlayers = 0;
				switch (dbHandler.getDBType()) {
				case MYSQL:
					totalPlayers = MySQLDB.getAllPlayerStatistics().size();
					break;
				case SQLITE:
					totalPlayers = SQLDB.getAllPlayerStatistics().size();
					break;
				case YAML:
					totalPlayers = YAMLDB.getAllPlayerStatistics().size();
					break;
				default:
					break;
				}

				return String.valueOf(totalPlayers);
			}));

			metrics.addCustomChart(new Metrics.SimplePie("statistic_type", dbHandler.getDBType()::name));

			Debug.logConsole("Metrics enabled.");
		}
	}

	@Override
	public void onDisable() {
		if (instance == null) return;

		GameUtils.stopAllGames();
		saveDatabase();

		getServer().getScheduler().cancelTasks(this);
		HandlerList.unregisterAll(this);
		instance = null;
	}

	private void loadHooks() {
		if (getManager().isPluginEnabled("HolographicDisplays")) {
			hologram = true;
			HoloHolder.initHoloHolder();
		} else {
			hologram = false;
		}

		if (getManager().isPluginEnabled("Vault") && initEconomy()) {
			vault = true;
		} else {
			vault = false;
		}

		if (getManager().isPluginEnabled("PlaceholderAPI")) {
			new Placeholder().register();
		}
	}

	private void connectDatabase() {
		dbHandler = new DatabaseHandler(this);

		String type = "yaml";
		switch (ConfigValues.getDatabaseType()) {
		case "mysql":
			type = "mysql";
			break;
		case "sql":
		case "sqlite":
			type = "sqlite";
			break;
		case "yml":
		case "yaml":
			type = "yaml";
			break;
		default:
			break;
		}

		dbHandler.setDatabaseType(type);
		dbHandler.connectDatabase();
		dbHandler.loadDatabase(true);
	}

	private void saveDatabase() {
		if (!ConfigValues.isRejoinDelayEnabled() || !ConfigValues.isRememberRejoinDelay()) {
			return;
		}

		switch (dbHandler.getDBType()) {
		case YAML:
			YAMLDB.saveJoinDelay();
			break;
		case SQLITE:
			SQLDB.saveJoinDelay();
			break;
		case MYSQL:
			MySQLDB.saveJoinDelay();
			break;
		default:
			break;
		}
	}

	private boolean initEconomy() {
		RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
		econ = rsp == null ? null : rsp.getProvider();
		return econ != null;
	}

	public synchronized boolean reload() {
		HandlerList.unregisterAll(this);

		for (Game game : games) {
			if (game == null) {
				continue;
			}

			if (game.isGameRunning()) {
				GameUtils.stopGame(game, false);
				GameUtils.broadcastToGame(game, RageMode.getLang().get("game.game-stopped-for-reload"));
			} else if (GameUtils.getStatus(game.getName()).isPresent()
					&& GameUtils.getStatus(game.getName()).get() == GameStatus.WAITING) {
				GameUtils.kickAllPlayers(game);
			}
		}

		games.clear();
		spawns.clear();

		conf.loadConfig();
		lang.loadLanguage(ConfigValues.getLang());

		loadGames();

		if (ConfigValues.isSignsEnable()) {
			SignConfiguration.initSignConfiguration();
			SignCreator.loadSigns();
		}

		registerListeners();
		if (dbHandler != null) {
			dbHandler.loadDatabase(false);
		} else {
			connectDatabase();
		}

		if (hologram)
			HoloHolder.initHoloHolder();

		return true;
	}

	private void registerCommands() {
		org.bukkit.command.PluginCommand cmd = getCommand("ragemode");
		cmd.setExecutor(new RmCommand());
		cmd.setTabCompleter(new RmTabCompleter());
	}

	private void registerListeners() {
		Stream.of(new EventListener(this), new GameListener(this))
				.forEach(l -> getServer().getPluginManager().registerEvents(l, this));

		if (ConfigValues.isBungee()) {
			getManager().registerEvents(new BungeeListener(), this);
		}

		if (Version.isCurrentEqualOrLower(Version.v1_8_R3))
			getManager().registerEvents(new Listeners_1_8(), this);
		else
			getManager().registerEvents(new Listeners_1_9(), this);
	}

	private void loadGames() {
		if (conf.getArenasCfg().contains("arenas")) {
			for (String game : GetGames.getGameNames()) {
				if (game == null) {
					continue;
				}

				Game g = new Game(game);
				games.add(g);

				spawns.add(new GameSpawn(g));

				// Loads the game locker
				GameUtils.setStatus(game,
						conf.getArenasCfg().getBoolean("arenas." + game + ".lock", false) ? GameStatus.NOTREADY
								: GameStatus.READY);

				Debug.logConsole("Loaded {0} game!", game);
			}
		}

		loadItems();
	}

	private void loadItems() {
		org.bukkit.configuration.file.FileConfiguration c = conf.getCfg();
		if (!c.contains("items")) {
			return;
		}

		if (c.contains("items.combatAxe")) {
			ItemHandler itemHandler = new ItemHandler();
			itemHandler.setItem(Material.IRON_AXE)
					.setDisplayName(Utils.colors(c.getString("items.combatAxe.name", "&6CombatAxe")))
					.setLore(Utils.colorList(c.getStringList("items.combatAxe.lore")))
					.setSlot(c.getInt("items.combatAxe.slot", 2)).build();
			gameItems[0] = itemHandler;
		}

		if (c.contains("items.grenade")) {
			ItemHandler itemHandler = new ItemHandler();
			itemHandler.setItem(Material.EGG)
					.setDisplayName(Utils.colors(c.getString("items.grenade.name", "&8Grenade")))
					.setCustomName(Utils.colors(c.getString("items.grenade.custom-name", "")))
					.setLore(Utils.colorList(c.getStringList("items.grenade.lore")))
					.setSlot(c.getInt("items.grenade.slot", 6)).build();
			gameItems[1] = itemHandler;
		}

		if (c.contains("items.rageArrow")) {
			ItemHandler itemHandler = new ItemHandler();
			itemHandler.setItem(Material.ARROW)
					.setDisplayName(Utils.colors(c.getString("items.rageArrow.name", "&6RageArrow")))
					.setLore(Utils.colorList(c.getStringList("items.rageArrow.lore")))
					.setSlot(c.getInt("items.rageArrow.slot", 9)).build();
			gameItems[2] = itemHandler;
		}

		if (c.contains("items.rageBow")) {
			ItemHandler itemHandler = new ItemHandler();
			itemHandler.setItem(Material.BOW)
					.setDisplayName(Utils.colors(c.getString("items.rageBow.name", "&6RageBow")))
					.setLore(Utils.colorList(c.getStringList("items.rageBow.lore")))
					.setSlot(c.getInt("items.rageBow.slot", 0))
					.setEnchant(org.bukkit.enchantments.Enchantment.ARROW_INFINITE).build();
			gameItems[3] = itemHandler;
		}

		if (c.contains("items.rageKnife")) {
			ItemHandler itemHandler = new ItemHandler();
			itemHandler.setItem(Material.SHEARS)
					.setDisplayName(Utils.colors(c.getString("items.rageKnife.name", "&6RageKnife")))
					.setLore(Utils.colorList(c.getStringList("items.rageKnife.lore")))
					.setSlot(c.getInt("items.rageKnife.slot", 1)).build();
			gameItems[4] = itemHandler;
		}

		// Lobby items
		if (c.contains("items.force-start")) {
			ItemHandler itemHandler = new ItemHandler();
			itemHandler.setItem(Material.valueOf(c.getString("items.force-start.item")))
					.setDisplayName(Utils.colors(c.getString("items.force-start.name", "&2Force the game start")))
					.setLore(Utils.colorList(c.getStringList("items.force-start.lore")))
					.setSlot(c.getInt("items.force-start.slot", 3)).build();
			lobbyItems[0] = itemHandler;
		}

		if (c.contains("items.leavegameitem")) {
			ItemHandler itemHandler = new ItemHandler();
			itemHandler.setItem(Material.valueOf(c.getString("items.leavegameitem.item")))
					.setDisplayName(Utils.colors(c.getString("items.leavegameitem.name", "&cExit")))
					.setLore(Utils.colorList(c.getStringList("items.leavegameitem.lore")))
					.setSlot(c.getInt("items.leavegameitem.slot", 5)).build();
			lobbyItems[1] = itemHandler;
		}
	}

	public File getFolder() {
		File dataFolder = getDataFolder();
		if (!dataFolder.exists())
			dataFolder.mkdir();

		return dataFolder;
	}

	/**
	 * Gets the database handler which handles the database connection.
	 * @return {@link DatabaseHandler}
	 */
	public DatabaseHandler getDatabaseHandler() {
		return dbHandler;
	}

	/**
	 * Gets the given player statistics from database.
	 * @param uuid Player uuid
	 * @return {@link PlayerPoints}
	 */
	public static PlayerPoints getPPFromDatabase(UUID uuid) {
		switch (instance.getDatabaseHandler().getDBType()) {
		case SQLITE:
			return SQLDB.getPlayerStatsFromData(uuid);
		case MYSQL:
			return MySQLDB.getPlayerStatsFromData(uuid);
		case YAML:
			return YAMLDB.getPlayerStatsFromData(uuid);
		default:
			return null;
		}
	}

	/**
	 * Removes a game from the list.
	 * @see #removeGame(String)
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
	 * @see #removeSpawn(String)
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
	 * @deprecated Use {@link #getDatabaseHandler()}
	 * @return mySQLConnect class
	 */
	@Deprecated
	public static MySQLConnect getMySQL() {
		return DatabaseHandler.getMySQL();
	}

	/**
	 * Gets the {@link SQLConnect} class
	 * @deprecated Use {@link #getDatabaseHandler()}
	 * @return SQLConnect class
	 */
	@Deprecated
	public static SQLConnect getSQL() {
		return DatabaseHandler.getSQL();
	}

	/**
	 * Gets the {@link Language} class
	 * @return Language class
	 */
	public static Language getLang() {
		return lang;
	}

	/**
	 * Gets the {@link ServerVersion} class
	 * @return ServerVersion class
	 */
	public static ServerVersion getServerVersion() {
		return serverVersion;
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

	public BossbarManager getBossbarManager() {
		return bossManager;
	}

	public List<Game> getGames() {
		return games;
	}

	public List<GameSpawn> getSpawns() {
		return spawns;
	}

	public ItemHandler[] getGameItems() {
		return gameItems;
	}

	public ItemHandler[] getLobbyItems() {
		return lobbyItems;
	}

	private PluginManager getManager() {
		return getServer().getPluginManager();
	}

	public Economy getEconomy() {
		return econ;
	}

	public String checkVersion(String sender) {
		String msg = "";
		String[] nVersion;
		String[] cVersion;
		String lineWithVersion = "";
		try {
			URL githubUrl = new URL(
					"https://raw.githubusercontent.com/montlikadani/RageMode/master/src/main/resources/plugin.yml");
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
			nVersion = versionString.replaceAll("[^0-9.]", "").split("\\.");
			double newestVersionNumber = Double.parseDouble(nVersion[0] + "." + nVersion[1]);

			cVersion = getDescription().getVersion().replaceAll("[^0-9.]", "").split("\\.");
			double currentVersionNumber = Double.parseDouble(cVersion[0] + "." + cVersion[1]);

			if (newestVersionNumber > currentVersionNumber) {
				if (sender.equals("player")) {
					msg = "&8&m&l--------------------------------------------------\n"
							+ "&a A new update is available for RageMode!&4 Version:&7 " + versionString
							+ "\n&6Download:&c &nhttps://www.spigotmc.org/resources/69169/"
							+ "\n&8&m&l--------------------------------------------------";
				} else if (sender.equals("console")) {
					msg = "New version (" + versionString
							+ ") is available at https://www.spigotmc.org/resources/69169/";
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
