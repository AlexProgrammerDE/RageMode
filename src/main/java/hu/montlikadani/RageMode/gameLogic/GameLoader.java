package hu.montlikadani.ragemode.gameLogic;

import java.util.Map.Entry;
import java.util.Random;
import java.util.Timer;
import java.util.UUID;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import hu.montlikadani.ragemode.Debug;
import hu.montlikadani.ragemode.MinecraftVersion.Version;
import hu.montlikadani.ragemode.RageMode;
import hu.montlikadani.ragemode.API.event.GameStartEvent;
import hu.montlikadani.ragemode.config.Configuration;
import hu.montlikadani.ragemode.gameUtils.BossMessenger;
import hu.montlikadani.ragemode.gameUtils.GameUtils;
import hu.montlikadani.ragemode.gameUtils.GetGames;
import hu.montlikadani.ragemode.signs.SignCreator;

public class GameLoader {

	private String gameName;

	private Configuration conf = RageMode.getInstance().getConfiguration();
	private GameTimer gameTimer;

	public GameLoader(String gameName) {
		this.gameName = gameName;

		checkTeleport();

		PlayerList.removeLobbyTimer();

		GameStartEvent gameStartEvent = new GameStartEvent(gameName, PlayerList.getPlayersFromList());
		Bukkit.getPluginManager().callEvent(gameStartEvent);

		PlayerList.setGameRunning(gameName);
		GameUtils.setStatus(GameStatus.RUNNING);
		setInventories();

		int time = !conf.getArenasCfg().isSet("arenas." + gameName + ".gametime")
				? conf.getCfg().getInt("game.global.defaults.gametime") < 0 ? 5 * 60
						: conf.getCfg().getInt("game.global.defaults.gametime") * 60
				: GetGames.getGameTime(gameName) * 60;

		gameTimer = new GameTimer(gameName, time);
		gameTimer.loadModules();
		Timer t = new Timer();
		t.scheduleAtFixedRate(gameTimer, 0, 60 * 20L);

		GameUtils.runCommandsForAll(gameName, "start");
		SignCreator.updateAllSigns(gameName);

		for (Entry<String, String> players : PlayerList.getPlayers().entrySet()) {
			Player p = Bukkit.getPlayer(UUID.fromString(players.getValue()));

			if (Version.isCurrentEqualOrHigher(Version.v1_9_R1)) {
				String bossMessage = conf.getCfg().getString("bossbar-messages.join.message");

				if (bossMessage != null && !bossMessage.equals("")) {
					bossMessage = bossMessage.replace("%game%", gameName);
					bossMessage = bossMessage.replace("%player%", p.getName());
					bossMessage = RageMode.getLang().colors(bossMessage);

					if (conf.getArenasCfg().isSet("arenas." + gameName + ".bossbar")) {
						if (conf.getArenasCfg().getBoolean("arenas." + gameName + ".bossbar"))
							new BossMessenger(gameName).sendBossBar(bossMessage, p,
									BarStyle.valueOf(conf.getCfg().getString("bossbar-messages.join.style")),
									BarColor.valueOf(conf.getCfg().getString("bossbar-messages.join.color")));
					} else {
						if (conf.getCfg().getBoolean("game.global.defaults.bossbar"))
							new BossMessenger(gameName).sendBossBar(bossMessage, p,
									BarStyle.valueOf(conf.getCfg().getString("bossbar-messages.join.style")),
									BarColor.valueOf(conf.getCfg().getString("bossbar-messages.join.color")));
					}
				}
			} else {
				Debug.logConsole(Level.WARNING, "Your server version does not support for Bossbar. Only 1.9+");
			}

			GameUtils.sendActionBarMessages(p, gameName, "start");
		}
	}

	private void checkTeleport() {
		GameSpawnGetter gameSpawnGetter = GameUtils.getGameSpawnByName(gameName);
		if (gameSpawnGetter.isGameReady()) {
			teleportPlayersToGameSpawns(gameSpawnGetter);
		} else {
			GameUtils.broadcastToGame(gameName, RageMode.getLang().get("game.not-set-up"));
			for (Entry<String, String> uuids : PlayerList.getPlayers().entrySet()) {
				Player p = Bukkit.getPlayer(UUID.fromString(uuids.getValue()));
				PlayerList.removePlayer(p);
			}
		}
	}

	private void teleportPlayersToGameSpawns(GameSpawnGetter spawn) {
		for (Entry<String, String> uuids : PlayerList.getPlayers().entrySet()) {
			Player player = Bukkit.getPlayer(UUID.fromString(uuids.getValue()));

			Random r = new Random();
			if (spawn.getSpawnLocations().size() > 0) {
				int x = r.nextInt(spawn.getSpawnLocations().size());
				Location location = spawn.getSpawnLocations().get(x);
				player.teleport(location);
			}
		}
	}

	private void setInventories() {
		for (Entry<String, String> uuids : PlayerList.getPlayers().entrySet()) {
			Player player = Bukkit.getPlayer(UUID.fromString(uuids.getValue()));
			GameUtils.addGameItems(player, true);
		}
	}

	/*public static List<Entity> getEntities(Player player) {
		List<Entity> entitys = new ArrayList<>();
		for (Entity e : player.getNearbyEntities(5, 5, 5)) {
			if (e instanceof LivingEntity) {
				if (getLookingAt(player, (LivingEntity) e))
					entitys.add(e);
			}
		}

		return entitys;
	}*/

	public static boolean getLookingAt(Player player, LivingEntity livingEntity) {
		Location eye = player.getEyeLocation();
		Vector toEntity = livingEntity.getLocation().toVector().subtract(eye.toVector());
		double dot = toEntity.normalize().dot(eye.getDirection());

		return dot >= 0.99D;
	}
}
