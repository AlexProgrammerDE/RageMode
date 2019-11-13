package hu.montlikadani.ragemode.gameUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import org.bukkit.entity.Player;

import hu.montlikadani.ragemode.managers.PlayerManager;

public class ScoreTeam implements IObjectives {

	public static HashMap<String, ScoreTeam> allTeams = new HashMap<>();
	private List<Player> players = new ArrayList<>();

	/**
	 * Creates a new instance of Team, which manages the Team for
	 * the team prefixes/suffixes.
	 * @param players The list of {@link PlayerManager}
	 */
	public ScoreTeam(List<PlayerManager> players) {
		players.forEach(pm -> this.players.add(pm.getPlayer()));
	}

	/**
	 * Adds this instance to the global ScoreTeam.
	 * @param gameName the unique game-name for which the ScoreTeam element should be saved for.
	 * @return Whether the ScoreTeam was stored successfully or not.
	 */
	public boolean addToList(String gameName) {
		return addToList(gameName, true);
	}

	/**
	 * Adds this instance to the global ScoreTeam.
	 * @param gameName the unique game-name for which the ScoreTeam element should be saved for.
	 * @param forceReplace force the game put to the list
	 * @return Whether the ScoreTeam was stored successfully or not.
	 */
	@Override
	public boolean addToList(String gameName, boolean forceReplace) {
		if (!allTeams.containsKey(gameName)) {
			allTeams.put(gameName, this);
			return true;
		} else if (forceReplace) {
			allTeams.remove(gameName);
			allTeams.put(gameName, this);
			return true;
		} else
			return false;
	}

	/**
	 * Sends ScoreTeam to all online players that are currently playing in the game.
	 * @param prefix String
	 * @param suffix String
	 */
	public void setTeam(String prefix, String suffix) {
		for (Player player : this.players) {
			setTeam(player, prefix, suffix);
		}
	}

	/**
	 * Sets the current player prefix/suffix.
	 * @param player Player
	 * @param prefix String
	 * @param suffix String
	 */
	public void setTeam(Player player, String prefix, String suffix) {
		player.setPlayerListName(prefix + player.getName() + suffix);
	}

	/**
	 * Removing the team from all online player that are currently playing in a game.
	 */
	@Override
	public void remove() {
		for (Player player : this.players) {
			remove(player);
		}
	}

	/**
	 * Removes the team from player
	 * @param player Player
	 */
	@Override
	public void remove(Player player) {
		player.setPlayerListName(player.getName());

		for (Iterator<Player> it = this.players.iterator(); it.hasNext();) {
			if (it.next().equals(player)) {
				it.remove();
				break;
			}
		}
	}

	/**
	 * Returns the HashMap with all the ScoreTeam elements.
	 * @return {@link #allTeams}
	 */
	public HashMap<String, ScoreTeam> getTeams() {
		return allTeams;
	}

	/**
	 * Returns the stored players who added to the list.
	 * @return An unmodifiable list of players
	 */
	public List<Player> getPlayers() {
		return Collections.unmodifiableList(players);
	}
}
