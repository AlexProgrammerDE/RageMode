package hu.montlikadani.ragemode.gameLogic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;

import hu.montlikadani.ragemode.RageMode;
import hu.montlikadani.ragemode.Utils;
import hu.montlikadani.ragemode.API.event.GameJoinAttemptEvent;
import hu.montlikadani.ragemode.API.event.GameLeaveAttemptEvent;
import hu.montlikadani.ragemode.API.event.SpectatorJoinToGameEvent;
import hu.montlikadani.ragemode.API.event.SpectatorLeaveFromGameEvent;
import hu.montlikadani.ragemode.config.Configuration;
import hu.montlikadani.ragemode.gameUtils.GetGameLobby;
import hu.montlikadani.ragemode.gameUtils.GetGames;
import hu.montlikadani.ragemode.gameUtils.ScoreBoard;
import hu.montlikadani.ragemode.gameUtils.ScoreTeam;
import hu.montlikadani.ragemode.gameUtils.TabTitles;
import hu.montlikadani.ragemode.gameUtils.TableList;

public class PlayerList {

	public static TableList<Player, Location> oldLocations = new TableList<>();
	public static TableList<Player, ItemStack[]> oldInventories = new TableList<>();
	public static TableList<Player, ItemStack[]> oldArmor = new TableList<>();
	public static TableList<Player, Double> oldHealth = new TableList<>();
	public static TableList<Player, Integer> oldHunger = new TableList<>();
	public static TableList<Player, Collection<PotionEffect>> oldEffects = new TableList<>();
	public static TableList<Player, GameMode> oldGameMode = new TableList<>();
	public static TableList<Player, String> oldDisplayName = new TableList<>();
	public static TableList<Player, String> oldListName = new TableList<>();
	public static TableList<Player, Integer> oldFire = new TableList<>();
	public static TableList<Player, Float> oldExp = new TableList<>();
	public static TableList<Player, Integer> oldExpLevel = new TableList<>();
	public static TableList<Player, Entity> oldVehicle = new TableList<>();

	private static Map<UUID, String> specPlayer = new HashMap<>();
	static Location loc;
	static GameMode gMode = GameMode.SURVIVAL;
	static boolean fly = false;
	static boolean allowFly = false;

	// Game name | Player UUID
	private static Map<String, String> players = new HashMap<>();
	private static String[] list = new String[1]; // Store game names & max players
	private static String[] runningGames = new String[1];

	private static LobbyTimer lobbyTimer;

	public PlayerList() {
		int i = 0;
		int imax = GetGames.getConfigGamesCount();
		String[] games = GetGames.getGameNames();
		list = Arrays.copyOf(list, GetGames.getConfigGamesCount() * (GetGames.getOverallMaxPlayers() + 1));
		while (i < imax) {
			list[i * (GetGames.getOverallMaxPlayers() + 1)] = games[i];
			i++;
		}
		runningGames = Arrays.copyOf(runningGames, GetGames.getConfigGamesCount());
	}

	public static Map<String, String> getPlayersInList() {
		return Collections.unmodifiableMap(players);
	}

	public static Map<UUID, String> getSpectatorPlayers() {
		return Collections.unmodifiableMap(specPlayer);
	}

	public static boolean containsPlayerInList(String uuid) {
		if (players != null && players.containsValue(uuid))
			return true;

		return false;
	}

	public static boolean addPlayer(Player player, String game) {
		if (isGameRunning(game)) {
			player.sendMessage(RageMode.getLang().get("game.running"));
			return false;
		}

		GameJoinAttemptEvent event = new GameJoinAttemptEvent(player, game);
		Utils.callEvent(event);
		if (event.isCancelled())
			return false;

		String uuid = player.getUniqueId().toString();

		if (containsPlayerInList(uuid)) {
			player.sendMessage(RageMode.getLang().get("game.player-already-in-game", "%usage%", "/rm leave"));
			return false;
		}

		Configuration conf = RageMode.getInstance().getConfiguration();

		if (!conf.getCfg().getBoolean("save-player-datas-to-file")) {
			// We still need some data saving
			oldLocations.addToBoth(player, player.getLocation());
			oldGameMode.addToBoth(player, player.getGameMode());
			player.setGameMode(GameMode.SURVIVAL);

			hu.montlikadani.ragemode.gameUtils.GameUtils.clearPlayerTools(player);
		}

		int i, n;
		i = 0;
		n = 0;
		int kickposition;
		int imax = GetGames.getConfigGamesCount() * (GetGames.getOverallMaxPlayers() + 1);
		int playersPerGame = GetGames.getOverallMaxPlayers();

		while (i < imax) {
			if (list[i] != null) {
				if (list[i].equalsIgnoreCase(game)) {
					n = i;
					n++; // should increase performance because the game name in the list isn't checked for null

					int time = GetGameLobby.getLobbyTime(game);

					while (n <= (GetGames.getMaxPlayers(game) + i)) {
						players.put(game, uuid);

						player.sendMessage(RageMode.getLang().get("game.you-joined-the-game", "%game%", game));

						if (conf.getCfg().getInt("game.global.lobby.min-players-to-start-lobby-timer") > 1) {
							if (players.size() == conf.getCfg()
									.getInt("game.global.lobby.min-players-to-start-lobby-timer")) {
								if (lobbyTimer == null) { // do not create double class instance
									lobbyTimer = new LobbyTimer(game, time);
									lobbyTimer.loadTimer();
								}
							}
						} else {
							if (players.size() == 2 && lobbyTimer == null) {
								lobbyTimer = new LobbyTimer(game, time);
								lobbyTimer.loadTimer();
							}
						}
						return true;
					}

					if (player.hasPermission("ragemode.vip") && hasRoomForVIP(game)) {
						Random random = new Random();
						boolean isVIP = false;
						Player playerToKick;

						do {
							kickposition = random.nextInt(GetGames.getMaxPlayers(game) - 1);
							kickposition = kickposition + 1 + i;
							n = 0;
							playerToKick = getPlayerByUUID(player.getUniqueId());
							isVIP = playerToKick.hasPermission("ragemode.vip");
						} while (isVIP);

						player.setMetadata("Leaving", new FixedMetadataValue(RageMode.getInstance(), true));

						Utils.clearPlayerInventory(playerToKick);

						if (conf.getCfg().getBoolean("bungee.enable"))
							RageMode.getInstance().getBungeeUtils().connectToHub(playerToKick);
						else if (conf.getCfg().getBoolean("save-player-datas-to-file")) {
							while (n < oldLocations.getFirstLength()) { // Get him back to his old location.
								if (oldLocations.getFromFirstObject(n) == playerToKick) {
									playerToKick.teleport(oldLocations.getFromSecondObject(n));
									oldLocations.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldInventories.getFirstLength()) { // Give him his inventory back.
								if (oldInventories.getFromFirstObject(n) == playerToKick) {
									playerToKick.getInventory().setContents(oldInventories.getFromSecondObject(n));
									oldInventories.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldArmor.getFirstLength()) { // Give him his armor back.
								if (oldArmor.getFromFirstObject(n) == playerToKick) {
									playerToKick.getInventory().setArmorContents(oldArmor.getFromSecondObject(n));
									oldArmor.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldHealth.getFirstLength()) { // Give him his health back.
								if (oldHealth.getFromFirstObject(n) == playerToKick) {
									playerToKick.setHealth(oldHealth.getFromSecondObject(n));
									oldHealth.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldHunger.getFirstLength()) { // Give him his hunger back.
								if (oldHunger.getFromFirstObject(n) == playerToKick) {
									playerToKick.setFoodLevel(oldHunger.getFromSecondObject(n));
									oldHunger.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldEffects.getFirstLength()) { // Give him his potion effects back.
								if (oldEffects.getFromFirstObject(n) == playerToKick) {
									playerToKick.addPotionEffects(oldEffects.getFromSecondObject(n));
									oldEffects.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldGameMode.getFirstLength()) { // Give him his gamemode back.
								if (oldGameMode.getFromFirstObject(n) == playerToKick) {
									playerToKick.setGameMode(oldGameMode.getFromSecondObject(n));
									oldGameMode.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldListName.getFirstLength()) { // Give him his list name back.
								if (oldListName.getFromFirstObject(n) == playerToKick) {
									playerToKick.setPlayerListName(oldListName.getFromSecondObject(n));
									oldListName.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldDisplayName.getFirstLength()) { // Give him his display name back.
								if (oldDisplayName.getFromFirstObject(n) == playerToKick) {
									playerToKick.setDisplayName(oldDisplayName.getFromSecondObject(n));
									oldDisplayName.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldFire.getFirstLength()) { // Give him his fire back.
								if (oldFire.getFromFirstObject(n) == playerToKick) {
									playerToKick.setFireTicks(oldFire.getFromSecondObject(n));
									oldFire.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldExp.getFirstLength()) { // Give him his exp back.
								if (oldExp.getFromFirstObject(n) == playerToKick) {
									playerToKick.setExp(oldExp.getFromSecondObject(n));
									oldExp.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldExpLevel.getFirstLength()) { // Give him his exp level back.
								if (oldExpLevel.getFromFirstObject(n) == playerToKick) {
									playerToKick.setLevel(oldExpLevel.getFromSecondObject(n));
									oldExpLevel.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldVehicle.getFirstLength()) { // Give him his vehicle back.
								if (oldVehicle.getFromFirstObject(n) == playerToKick) {
									oldVehicle.getFromSecondObject(n).getVehicle().teleport(playerToKick);
									oldVehicle.removeFromBoth(n);
								}
								n++;
							}

							conf.getDatasCfg().set("datas." + playerToKick.getName(), null);
							Configuration.saveFile(conf.getDatasCfg(), conf.getDatasFile());
						} else {
							while (n < oldLocations.getFirstLength()) { // Get him back to his old location.
								if (oldLocations.getFromFirstObject(n) == playerToKick) {
									playerToKick.teleport(oldLocations.getFromSecondObject(n));
									oldLocations.removeFromBoth(n);
								}
								n++;
							}

							n = 0;

							while (n < oldGameMode.getFirstLength()) { // Give him his gamemode back.
								if (oldGameMode.getFromFirstObject(n) == playerToKick) {
									playerToKick.setGameMode(oldGameMode.getFromSecondObject(n));
									oldGameMode.removeFromBoth(n);
								}
								n++;
							}
						}

						playerToKick.sendMessage(RageMode.getLang().get("game.player-kicked-for-vip"));
						players.remove(game);

						if (conf.getCfg().getInt("game.global.lobby.min-players-to-start-lobby-timer") > 1) {
							if (players.size() == conf.getCfg()
									.getInt("game.global.lobby.min-players-to-start-lobby-timer")) {
								if (lobbyTimer == null) {
									lobbyTimer = new LobbyTimer(game, time);
									lobbyTimer.loadTimer();
								}
							} else {
								lobbyTimer = null;
								return false;
							}
						} else {
							if (players.size() == 2) {
								if (lobbyTimer == null) {
									lobbyTimer = new LobbyTimer(game, time);
									lobbyTimer.loadTimer();
								}
							} else {
								lobbyTimer = null;
								return false;
							}
						}

						player.sendMessage(RageMode.getLang().get("game.you-joined-the-game", "%game%", game));
						return true;
					}

					player.sendMessage(RageMode.getLang().get("game.full"));
					return false;
				}
			}
			i = i + playersPerGame + 1;
		}

		player.sendMessage(RageMode.getLang().get("game.does-not-exist"));
		return false;
	}

	public static boolean addSpectatorPlayer(Player player, String game) {
		if (!RageMode.getInstance().getConfiguration().getCfg().getBoolean("bungee.enable")) {
			loc = player.getLocation();
			gMode = player.getGameMode();
			fly = player.isFlying();
			allowFly = player.getAllowFlight();
		}

		SpectatorJoinToGameEvent spec = new SpectatorJoinToGameEvent(game, player);
		Utils.callEvent(spec);

		specPlayer.put(player.getUniqueId(), game);
		return specPlayer.containsKey(player.getUniqueId());
	}

	public static boolean removeSpectatorPlayer(Player player) {
		if (!RageMode.getInstance().getConfiguration().getCfg().getBoolean("spectator.enable"))
			return false;

		if (specPlayer.containsKey(player.getUniqueId())) {
			if (!RageMode.getInstance().getConfiguration().getCfg().getBoolean("bungee.enable")) {
				player.teleport(loc);
				player.setGameMode(gMode);
				player.setFlying(fly);
				player.setAllowFlight(allowFly);
			}

			SpectatorLeaveFromGameEvent spec = new SpectatorLeaveFromGameEvent(specPlayer.get(player.getUniqueId()), player);
			Utils.callEvent(spec);

			specPlayer.remove(player.getUniqueId());
			return true;
		}
		return false;
	}

	public static boolean removePlayer(Player player) {
		String game = getPlayersGame(player);
		if (game == null) {
			return false;
		}

		GameLeaveAttemptEvent gameLeaveEvent = new GameLeaveAttemptEvent(player, game);
		Utils.callEvent(gameLeaveEvent);
		if (gameLeaveEvent.isCancelled())
			return false;


		if (!player.hasMetadata("leavingRageMode")) {
			player.setMetadata("leavingRageMode", new FixedMetadataValue(RageMode.getInstance(), true));

			int n = 0;
			if (containsPlayerInList(player.getUniqueId().toString())) {
				removePlayerSynced(player);

				Utils.clearPlayerInventory(player);
				player.sendMessage(RageMode.getLang().get("game.player-left"));

				player.setMetadata("Leaving", new FixedMetadataValue(RageMode.getInstance(), true));

				if (RageMode.getInstance().getConfiguration().getCfg().getBoolean("bungee.enable"))
					RageMode.getInstance().getBungeeUtils().connectToHub(player);
				else if (RageMode.getInstance().getConfiguration().getCfg().getBoolean("save-player-datas-to-file")) {
					while (n < oldLocations.getFirstLength()) { // Bring him back to his old location
						if (oldLocations.getFromFirstObject(n) == player) {
							player.teleport(oldLocations.getFromSecondObject(n));
							oldLocations.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldInventories.getFirstLength()) { // Give him his inventory back
						if (oldInventories.getFromFirstObject(n) == player) {
							player.getInventory().setContents(oldInventories.getFromSecondObject(n));
							oldInventories.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldArmor.getFirstLength()) {
						if (oldArmor.getFromFirstObject(n) == player) { // Give him his armor back
							player.getInventory().setArmorContents(oldArmor.getFromSecondObject(n));
							oldArmor.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldHealth.getFirstLength()) { // Give him his health back.
						if (oldHealth.getFromFirstObject(n) == player) {
							player.setHealth(oldHealth.getFromSecondObject(n));
							oldHealth.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldHunger.getFirstLength()) { // Give him his hunger back.
						if (oldHunger.getFromFirstObject(n) == player) {
							player.setFoodLevel(oldHunger.getFromSecondObject(n));
							oldHunger.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldEffects.getFirstLength()) { // Give him his potion effects back.
						if (oldEffects.getFromFirstObject(n) == player) {
							player.addPotionEffects(oldEffects.getFromSecondObject(n));
							oldEffects.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldGameMode.getFirstLength()) { // Give him his gamemode back.
						if (oldGameMode.getFromFirstObject(n) == player) {
							player.setGameMode(oldGameMode.getFromSecondObject(n));
							oldGameMode.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldListName.getFirstLength()) { // Give him his list name back.
						if (oldListName.getFromFirstObject(n) == player) {
							player.setPlayerListName(oldListName.getFromSecondObject(n));
							oldListName.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldDisplayName.getFirstLength()) { // Give him his display name back.
						if (oldDisplayName.getFromFirstObject(n) == player) {
							player.setDisplayName(oldDisplayName.getFromSecondObject(n));
							oldDisplayName.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldFire.getFirstLength()) { // Give him his fire back.
						if (oldFire.getFromFirstObject(n) == player) {
							player.setFireTicks(oldFire.getFromSecondObject(n));
							oldFire.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldExp.getFirstLength()) { // Give him his exp back.
						if (oldExp.getFromFirstObject(n) == player) {
							player.setExp(oldExp.getFromSecondObject(n));
							oldExp.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldExpLevel.getFirstLength()) { // Give him his exp level back.
						if (oldExpLevel.getFromFirstObject(n) == player) {
							player.setLevel(oldExpLevel.getFromSecondObject(n));
							oldExpLevel.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldVehicle.getFirstLength()) { // Give him his vehicle back.
						if (oldVehicle.getFromFirstObject(n) == player) {
							oldVehicle.getFromSecondObject(n).teleport(player);
							oldVehicle.removeFromBoth(n);
						}
						n++;
					}

					RageMode.getInstance().getConfiguration().getDatasCfg().set("datas." + player.getName(), null);
					Configuration.saveFile(RageMode.getInstance().getConfiguration().getDatasCfg(),
							RageMode.getInstance().getConfiguration().getDatasFile());
				} else {
					while (n < oldLocations.getFirstLength()) { // Bring him back to his old location
						if (oldLocations.getFromFirstObject(n) == player) {
							player.teleport(oldLocations.getFromSecondObject(n));
							oldLocations.removeFromBoth(n);
						}
						n++;
					}

					n = 0;

					while (n < oldGameMode.getFirstLength()) { // Give him his gamemode back.
						if (oldGameMode.getFromFirstObject(n) == player) {
							player.setGameMode(oldGameMode.getFromSecondObject(n));
							oldGameMode.removeFromBoth(n);
						}
						n++;
					}
				}

				players.remove(game);
				if (players.size() < RageMode.getInstance().getConfiguration().getCfg()
						.getInt("game.global.lobby.min-players-to-start-lobby-timer")) {
					lobbyTimer = null; // Remove the lobby timer instance when not enough players to start
				}

				player.removeMetadata("leavingRageMode", RageMode.getInstance());
				return true;
			}
			player.sendMessage(RageMode.getLang().get("game.player-not-ingame"));
			return false;
		}
		player.removeMetadata("leavingRageMode", RageMode.getInstance());
		return false;
	}

	public static void removePlayerSynced(Player player) {
		String game = getPlayersGame(player);
		// Just a null check if the player not find in the list
		if (game == null) {
			return;
		}

		if (ScoreBoard.allScoreBoards.containsKey(game))
			ScoreBoard.allScoreBoards.get(game).removeScoreBoard(player, true);

		if (TabTitles.allTabLists.containsKey(game))
			TabTitles.allTabLists.get(game).removeTabList(player);

		if (ScoreTeam.allTeams.containsKey(game))
			ScoreTeam.allTeams.get(game).removeTeam(player);
	}

	/**
	 * Checks whatever the game is running or not.
	 * @param game Game
	 * @return true if game is running.
	 */
	public static boolean isGameRunning(String game) {
		int i = 0;
		int imax = runningGames.length;
		while (i < imax) {
			if (runningGames[i] != null) {
				if (runningGames[i].equalsIgnoreCase(game))
					return true;
			}
			i++;
		}
		return false;
	}

	/**
	 * Gets all games added to list.
	 * @return list
	 */
	public static String[] getAllRunningGames() {
		return runningGames;
	}

	/**
	 * Sets the game running.
	 * @param game Game
	 * @return true if game exist
	 */
	public static boolean setGameRunning(String game) {
		if (!GetGames.isGameExistent(game))
			return false;

		int i = 0;
		int imax = runningGames.length;
		while (i < imax) {
			if (runningGames[i] != null) {
				if (runningGames[i].equalsIgnoreCase(game))
					return false;
			}
			i++;
		}
		i = 0;
		while (i < imax) {
			if (runningGames[i] == null) {
				runningGames[i] = game;
				return true;
			}
			i++;
		}
		return false;
	}

	/**
	 * Sets the game not running
	 * @param game Game
	 * @return true if game exist
	 */
	public static boolean setGameNotRunning(String game) {
		if (!GetGames.isGameExistent(game))
			return false;

		int i = 0;
		int imax = runningGames.length;

		while (i < imax) {
			if (runningGames[i] != null) {
				if (runningGames[i].equalsIgnoreCase(game)) {
					runningGames[i] = null;
					return true;
				}
			}
			i++;
		}
		return false;
	}

	/**
	 * Check if the specified player is currently in game.
	 * @param playerUUID Player UUID
	 * @return true if player is in list and playing
	 */
	public static boolean isPlayerPlaying(String playerUUID) {
		if (playerUUID == null) {
			throw new NullPointerException("Player UUID can not be null!");
		}

		if (containsPlayerInList(playerUUID))
			return true;

		return false;
	}

	/**
	 * Check whatever has free room for VIP players.
	 * @param game Game
	 * @return true if the players size not equal to vips size
	 */
	public static boolean hasRoomForVIP(String game) {
		if (players != null) {
			return false;
		}

		int vipsInGame = 0;

		for (Entry<String, String> playerUUIDs : players.entrySet()) {
			if (getPlayersGame(playerUUIDs.getValue()).equals(game)) {
				Player p = Bukkit.getPlayer(UUID.fromString(playerUUIDs.getValue()));

				if (p.hasPermission("ragemode.vip"))
					vipsInGame++;
			}
		}

		if (vipsInGame == players.size())
			return false;

		return true;
	}

	public static void addGameToList(String game, int maxPlayers) {
		if (GetGames.getOverallMaxPlayers() < maxPlayers) {
			String[] oldList = list;
			list = Arrays.copyOf(list, (GetGames.getConfigGamesCount() + 1) * (maxPlayers + 1));
			int i = 0;
			int imax = oldList.length;
			int n = 0;
			int nmax = (GetGames.getOverallMaxPlayers() + 1);

			while (i < imax) {
				while (n < nmax + i) {
					list[n + i] = oldList[n + i];
					n++;
				}
				i = i + maxPlayers + 1;
				n = i;
			}

			list[i] = game;
		} else {
			String[] oldList = list;
			list = Arrays.copyOf(list, (GetGames.getConfigGamesCount() + 1) * (GetGames.getOverallMaxPlayers() + 1));
			int i = 0;
			int imax = oldList.length;

			while (i < imax) {
				list[i] = oldList[i];
				i++;
			}

			list[i] = game;
		}

		String[] oldRunningGames = runningGames;
		runningGames = Arrays.copyOf(runningGames, (runningGames.length + 1));
		int i = 0;
		int imax = runningGames.length - 1;

		while (i < imax) {
			runningGames[i] = oldRunningGames[i];
			i++;
		}
	}

	public static void deleteGameFromList(String game) {
		if (isGameRunning(game))
			hu.montlikadani.ragemode.commands.StopGame.stopGame(game);

		int i = 0;
		int imax = list.length;
		int gamePos = imax;
		int nextGamePos = imax;

		while (i < imax) {
			if (list[i] != null) {
				if (list[i].equalsIgnoreCase(game)) {
					gamePos = i;
					int n = 0;
					int nmax = GetGames.getOverallMaxPlayers() + 1;

					while (n < nmax) {
						list[n + i] = null;
						n++;
					}
					nextGamePos = i + nmax;
				}
			}
			i++;
		}
		i = nextGamePos;

		while (i < imax) {
			list[gamePos] = list[i];
			list[i] = null;
			i++;
			gamePos++;
		}
		String[] oldList = new String[(GetGames.getConfigGamesCount() - 1) * (GetGames.getOverallMaxPlayers() + 1)];
		int g = 0;
		int gmax = oldList.length;

		while (g < gmax) {
			oldList[g] = list[g];
			g++;
		}

		list = Arrays.copyOf(list, oldList.length);

		g = 0;

		while (g < gmax) {
			list[g] = oldList[g];
			g++;
		}
	}

	/**
	 * Gets the specified player game from list.
	 * @param player Player
	 * @return game if player playing
	 */
	public static String getPlayersGame(Player player) {
		String game = null;

		if (players != null) {
			int i = 0;
			int imax = list.length;
			int playersPerGame = GetGames.getOverallMaxPlayers();

			while (i < imax) {
				if (list[i] != null) {
					if ((i % (playersPerGame + 1)) == 0)
						game = list[i];

					if (containsPlayerInList(player.getUniqueId().toString()))
						return game;
				}
				i++;
			}
		}

		return null;
	}

	/**
	 * Gets the game by player uuid from list.
	 * @param uuid Player UUID
	 * @return game if player playing
	 */
	public static String getPlayersGame(String uuid) {
		return getPlayersGame(Bukkit.getPlayer(UUID.fromString(uuid)));
	}

	/**
	 * Get the players uuid who added to the list.
	 * @return Players UUID
	 */
	public static Map<String, String> getPlayers() {
		return players;
	}

	/**
	 * Gets the players converted to list.
	 * @return list Players
	 */
	public static List<String> getPlayersFromList() {
		List<String> list = new ArrayList<>();

		if (players != null) {
			for (Entry<String, String> entries : players.entrySet()) {
				list.add(entries.getValue());
			}
		}

		return list;
	}

	/**
	 * Get the player who playing in game.
	 * @param game Game
	 * @return Player who in game currently.
	 */
	public static Player getPlayerInGame(String game) {
		return players != null ? Bukkit.getPlayer(UUID.fromString(players.get(game))) : null;
	}

	/**
	 * Get the player by uuid from list.
	 * @param uuid Player UUID
	 * @return Player
	 */
	public static Player getPlayerByUUID(UUID uuid) {
		return getPlayerByUUID(uuid.toString());
	}

	/**
	 * Get the player by uuid from list.
	 * @param uuid Player UUID
	 * @return Player
	 */
	public static Player getPlayerByUUID(String uuid) {
		if (players != null) {
			for (Entry<String, String> list : players.entrySet()) {
				if (list.getValue().equals(uuid)) {
					return Bukkit.getPlayer(list.getValue());
				}
			}
		}

		return null;
	}

	public static LobbyTimer getLobbyTimer() {
		return lobbyTimer;
	}

	/**
	 * Cancels the lobby timer and nulls the class to create new instance
	 * for this class. This prevents that bug when the player re-join to the
	 * game, then lobby timer does not start.
	 */
	public static void removeLobbyTimer() {
		if (lobbyTimer != null) {
			lobbyTimer.cancel();
			lobbyTimer = null;
		}
	}
}
