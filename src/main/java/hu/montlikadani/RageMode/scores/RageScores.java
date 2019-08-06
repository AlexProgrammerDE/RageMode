package hu.montlikadani.ragemode.scores;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import hu.montlikadani.ragemode.RageMode;

public class RageScores {

	private static HashMap<String, PlayerPoints> playerpoints = new HashMap<>();
	private static int totalPoints = 0;

	public static void addPointsToPlayer(Player killer, Player victim, String killCause) {
		String killerUUID = killer.getUniqueId().toString();
		PlayerPoints killerPoints = null;

		if (!killer.getUniqueId().toString().equals(victim.getUniqueId().toString())) {
			String victimUUID = victim.getUniqueId().toString();
			PlayerPoints victimPoints = null;

			switch (killCause.toLowerCase().trim()) {
			case "ragebow":
				int bowPoints = RageMode.getInstance().getConfiguration().getCfg().getInt("points.bowkill");
				totalPoints = addPoints(killer, bowPoints, true);
				addPoints(victim, 0, false);

				killerPoints = getPlayerPoints(killerUUID);
				int oldDirectArrowKills = killerPoints.getDirectArrowKills();
				int newDirectArrowKills = oldDirectArrowKills + 1;
				killerPoints.setDirectArrowKills(newDirectArrowKills);

				victimPoints = getPlayerPoints(victimUUID);
				int oldDirectArrowDeaths = victimPoints.getDirectArrowDeaths();
				int newDirectArrowDeaths = oldDirectArrowDeaths + 1;
				victimPoints.setDirectArrowDeaths(newDirectArrowDeaths);

				killer.sendMessage(RageMode.getLang().get("game.message.arrow-kill", "%victim%", victim.getName(), "%points%", "+" + Integer.toString(bowPoints)));

				victim.sendMessage(RageMode.getLang().get("game.message.arrow-death", "%killer%", killer.getName(), "%points%", ""));

				killer.sendMessage(RageMode.getLang().get("game.message.current-points", "%points%", Integer.toString(totalPoints)));
				break;
			case "combataxe":
				int axePoints = RageMode.getInstance().getConfiguration().getCfg().getInt("points.axekill");
				int axeMinusPoints = RageMode.getInstance().getConfiguration().getCfg().getInt("points.axedeath");
				totalPoints = addPoints(killer, axePoints, true);
				addPoints(victim, axeMinusPoints, false);

				killerPoints = getPlayerPoints(killerUUID);
				int oldAxeKills = killerPoints.getAxeKills();
				int newAxeKills = oldAxeKills + 1;
				killerPoints.setAxeKills(newAxeKills);

				victimPoints = getPlayerPoints(victimUUID);
				int oldAxeDeaths = victimPoints.getAxeDeaths();
				int newAxeDeaths = oldAxeDeaths + 1;
				victimPoints.setAxeDeaths(newAxeDeaths);

				killer.sendMessage(RageMode.getLang().get("game.message.axe-kill", "%victim%", victim.getName(), "%points%", "+" + Integer.toString(axePoints)));

				victim.sendMessage(RageMode.getLang().get("game.message.axe-death", "%killer%", killer.getName(), "%points%", Integer.toString(axeMinusPoints)));

				killer.sendMessage(RageMode.getLang().get("game.message.current-points", "%points%", Integer.toString(totalPoints)));
				break;
			case "rageknife":
				int knifePoints = RageMode.getInstance().getConfiguration().getCfg().getInt("points.knifekill");
				totalPoints = addPoints(killer, knifePoints, true);
				addPoints(victim, 0, false);

				killerPoints = getPlayerPoints(killerUUID);
				int oldKnifeKills = killerPoints.getKnifeKills();
				int newKnifeKills = oldKnifeKills + 1;
				killerPoints.setKnifeKills(newKnifeKills);

				victimPoints = getPlayerPoints(victimUUID);
				int oldKnifeDeaths = victimPoints.getKnifeDeaths();
				int newKnifeDeaths = oldKnifeDeaths + 1;
				victimPoints.setKnifeDeaths(newKnifeDeaths);

				killer.sendMessage(RageMode.getLang().get("game.message.knife-kill", "%victim%", victim.getName(), "%points%", "+" + Integer.toString(knifePoints)));

				victim.sendMessage(RageMode.getLang().get("game.message.knife-death", "%killer%", killer.getName(), "%points%", ""));

				killer.sendMessage(RageMode.getLang().get("game.message.current-points", "%points%", Integer.toString(totalPoints)));
				break;
			case "explosion":
				int explosionPoints = RageMode.getInstance().getConfiguration().getCfg().getInt("points.explosionkill");
				totalPoints = addPoints(killer, explosionPoints, true);
				addPoints(victim, 0, false);

				killerPoints = getPlayerPoints(killerUUID);
				int oldExplosionKills = killerPoints.getExplosionKills();
				int newExplosionKills = oldExplosionKills + 1;
				killerPoints.setExplosionKills(newExplosionKills);

				victimPoints = getPlayerPoints(victimUUID);
				int oldExplosionDeaths = victimPoints.getExplosionDeaths();
				int newExplosionDeaths = oldExplosionDeaths + 1;
				victimPoints.setExplosionDeaths(newExplosionDeaths);

				killer.sendMessage(RageMode.getLang().get("game.message.explosion-kill", "%victim%", victim.getName(), "%points%",
						"+" + Integer.toString(explosionPoints)));

				victim.sendMessage(RageMode.getLang().get("game.message.explosion-death", "%killer%", killer.getName(), "%points%", ""));

				killer.sendMessage(RageMode.getLang().get("game.message.current-points", "%points%", Integer.toString(totalPoints)));
				break;
			case "grenade":
				int grenadePoints = RageMode.getInstance().getConfiguration().getCfg().getInt("points.grenadekill");
				totalPoints = addPoints(killer, grenadePoints, true);
				addPoints(victim, 0, false);

				killer.sendMessage(RageMode.getLang().get("game.message.grenade-kill", "%victim%", victim.getName(), "%points%",
						"+" + Integer.toString(grenadePoints)));

				victim.sendMessage(RageMode.getLang().get("game.message.grenade-death", "%killer%", killer.getName(), "%points%", ""));

				killer.sendMessage(RageMode.getLang().get("game.message.current-points", "%points%", Integer.toString(totalPoints)));
				break;
			default:
				break;
			}

			// KillStreak
			PlayerPoints currentPoints = getPlayerPoints(killerUUID);
			int currentStreak = currentPoints.getCurrentStreak();
			if (currentStreak == 3 || currentStreak % 5 == 0) {
				currentPoints.setPoints(currentPoints.getPoints() + (currentStreak * 10));

				killer.sendMessage(RageMode.getLang().get("game.message.streak", "%number%", Integer.toString(currentStreak), "%points%",
						"+" + Integer.toString(currentStreak * 10)));
			}
		} else {
			killer.sendMessage(RageMode.getLang().get("game.message.suicide"));

			int pointLoss = RageMode.getInstance().getConfiguration().getCfg().getInt("points.suicide");
			if (playerpoints.containsKey(killerUUID)) {
				PlayerPoints pointsHolder = getPlayerPoints(killerUUID);
				if (pointsHolder.getPoints() < 1) {
					killer.sendMessage(RageMode.getLang().get("game.no-enough-points"));
				} else {
					pointsHolder.addPoints(pointLoss);
				}

				pointsHolder.setDeaths(pointsHolder.getDeaths() + 1);
			}
		}
	}

	public static void removePointsForPlayer(String playerUUID) {
		if (playerpoints.containsKey(playerUUID))
			playerpoints.remove(playerUUID);
	}

	public static void removePointsForPlayers(List<String> playerUUIDs) {
		for (String playerUUID : playerUUIDs) {
			if (playerpoints.containsKey(playerUUID))
				playerpoints.remove(playerUUID);
		}
	}

	/**
	 * Gets the specified player points
	 * 
	 * @param playerUUID UUID of player
	 * @return playerPoints Player points
	 */
	public static PlayerPoints getPlayerPoints(String playerUUID) {
		if (playerUUID == null) {
			throw new IllegalArgumentException("player uuid is null");
		}

		return playerpoints.get(playerUUID);
	}

	/**
	 * Gets the {@link #playerpoints} map
	 * @return playerpoints
	 */
	public static HashMap<String, PlayerPoints> getPlayerPointsMap() {
		return playerpoints;
	}

	private static int addPoints(Player player, int points, boolean killer) {
		// returns total points
		String playerUUID = player.getUniqueId().toString();
		if (playerpoints.containsKey(playerUUID)) {
			PlayerPoints pointsHolder = getPlayerPoints(playerUUID);
			int oldPoints = pointsHolder.getPoints();
			int oldKills = pointsHolder.getKills();
			int oldDeaths = pointsHolder.getDeaths();
			int totalPoints = oldPoints + points;
			int totalKills = oldKills;
			int totalDeaths = oldDeaths;
			int currentStreak = 0;
			int longestStreak = 0;
			if (killer) {
				totalKills++;
				currentStreak = pointsHolder.getCurrentStreak() + 1;
			} else {
				totalDeaths++;
				currentStreak = 0;
			}
			longestStreak = (currentStreak > pointsHolder.getLongestStreak()) ? currentStreak : pointsHolder.getLongestStreak();

			pointsHolder.addPoints(totalPoints);
			pointsHolder.setKills(totalKills);
			pointsHolder.setDeaths(totalDeaths);
			pointsHolder.setCurrentStreak(currentStreak);
			pointsHolder.setLongestStreak(longestStreak);
			return totalPoints;
		}

		int totalKills = 0;
		int totalDeaths = 0;
		int currentStreak = 0;
		int longestStreak = 0;
		if (killer) {
			totalKills = 1;
			currentStreak = 1;
			longestStreak = 1;
		} else {
			totalDeaths = 1;
			currentStreak = 0;
		}
		PlayerPoints pointsHolder = new PlayerPoints(playerUUID);
		pointsHolder.setPoints(points);
		pointsHolder.setKills(totalKills);
		pointsHolder.setDeaths(totalDeaths);
		pointsHolder.setCurrentStreak(currentStreak);
		pointsHolder.setLongestStreak(longestStreak);
		playerpoints.put(playerUUID, pointsHolder);
		return points;
	}

	public static String calculateWinner(String game, List<String> uuids) {
		String highest = UUID.randomUUID().toString();
		String resultPlayer = null;
		String goy = highest;
		int highestPoints = 0;
		for (String uuid : uuids) {
			if (playerpoints.containsKey(uuid)) {
				if (getPlayerPoints(uuid).getPoints() > highestPoints) {
					highest = uuid;
					highestPoints = getPlayerPoints(uuid).getPoints();
					resultPlayer = uuid;
				}
			}
		}

		if (goy == highest) {
			Bukkit.getPlayer(UUID.fromString(resultPlayer)).sendMessage(
					RageMode.getLang().get("game.message.player-won", "%player%", "Herobrine", "%game%", game));
			return null;
		}

		getPlayerPoints(highest).setWinner(true);

		if (resultPlayer.equals(highest))
			Bukkit.getPlayer(UUID.fromString(highest))
					.sendMessage(RageMode.getLang().get("game.message.you-won", "%game%", game));
		else
			Bukkit.getPlayer(UUID.fromString(resultPlayer)).sendMessage(RageMode.getLang().get("game.message.player-won",
					"%player%", Bukkit.getPlayer(UUID.fromString(highest)).getName(), "%game%", game));
		return highest;
	}
}