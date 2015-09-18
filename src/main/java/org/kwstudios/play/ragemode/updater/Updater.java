package org.kwstudios.play.ragemode.updater;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.kwstudios.play.ragemode.loader.PluginLoader;
import org.kwstudios.play.ragemode.toolbox.ConstantHolder;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.google.common.io.Files;

public class Updater {

	private URL url;
	private JavaPlugin plugin;
	private String pluginurl;
	private String version = "";
	private String downloadURL = "";
	private String changeLOG = "";
	private boolean out = true;

	/**
	 * Create a new SpigotPluginUpdate to check and update your plugin
	 * 
	 * @param plugin
	 *            - your plugin
	 * @param pluginurl
	 *            - the url to your plugin.html on your webserver
	 */
	public Updater(JavaPlugin plugin, String pluginurl) {
		try {
			url = new URL(pluginurl);
		} catch (MalformedURLException e) {
			Bukkit.getConsoleSender().sendMessage(ConstantHolder.RAGEMODE_PREFIX + "Error in checking update for: '"
					+ pluginurl + "' (invalid URL?)");
			e.printStackTrace();
		}
		this.plugin = plugin;
		this.pluginurl = pluginurl;
		Thread thread = new Thread(new UpdateRunnable());
		thread.start();
	}

	/**
	 * Enable a console output if new Version is availible
	 */
	public void enableOut() {
		out = true;
	}

	/**
	 * Disable a console output if new Version is availible
	 */
	public void disableOut() {
		out = false;
	}

	/**
	 * Check for a new Update
	 * 
	 * @return if new update is availible
	 */
	private boolean needsUpdate() {

		try {
			URLConnection con = url.openConnection();
			InputStream _in = con.getInputStream();
			Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(_in);

			Node nod = doc.getElementsByTagName("item").item(0);
			NodeList children = nod.getChildNodes();

			version = children.item(1).getTextContent();
			downloadURL = children.item(3).getTextContent();
			changeLOG = children.item(5).getTextContent();
			if (newVersion(plugin.getDescription().getVersion(), version.replaceAll("[a-zA-z ]", ""))) {
				if (out) {
					Bukkit.getConsoleSender().sendMessage(ConstantHolder.RAGEMODE_PREFIX + "New Version found: "
							+ version.replaceAll("[a-zA-z ]", ""));
					Bukkit.getConsoleSender().sendMessage(
							ConstantHolder.RAGEMODE_PREFIX + "Download it here: " + downloadURL.toString());
					Bukkit.getConsoleSender().sendMessage(ConstantHolder.RAGEMODE_PREFIX + "Changelog: " + changeLOG);
				}

				return true;
			}

		} catch (IOException | SAXException | ParserConfigurationException e) {
			Bukkit.getConsoleSender().sendMessage(ConstantHolder.RAGEMODE_PREFIX + "Error in checking update for: '"
					+ pluginurl + "' (invalid URL?) !");
			e.printStackTrace();
		}

		return false;
	}

	/**
	 * Checks if the newversion is really newer then the other one
	 * 
	 * @param oldv
	 * @param newv
	 * @return if it is newer
	 */
	private boolean newVersion(String oldv, String newv) {
		// System.out.println("Check " + oldv + " - " + newv);
		if (oldv != null && newv != null) {
			oldv = oldv.replaceAll("[a-zA-z ]", "");
			oldv = oldv.replaceAll(" ", "");
			oldv = oldv.replaceAll("-", "");
			newv = newv.replaceAll("[a-zA-z ]", "");
			newv = newv.replaceAll(" ", "");
			newv = newv.replaceAll("-", "");
			oldv = oldv.replace('.', '_');
			newv = newv.replace('.', '_');
			// System.out.println("length: " + oldv.split("_").length +" || " +
			// newv.split("_").length);
			if (oldv.split("_").length != 0 && oldv.split("_").length != 1 && newv.split("_").length != 0
					&& newv.split("_").length != 1) {

				int vnum = Integer.valueOf(oldv.split("_")[0]);
				int vsec = Integer.valueOf(oldv.split("_")[1]);

				int newvnum = Integer.valueOf(newv.split("_")[0]);
				int newvsec = Integer.valueOf(newv.split("_")[1]);
				// System.out.println("Check2: " + vnum + " ? " + newvnum + " ||
				// " + vsec + " ? " + newvsec);
				if (newvnum > vnum)
					return true;

				if (newvnum == vnum) {
					if (newvsec > vsec) {
						return true;
					}
				}
			}
		}
		return false;
	}

	/**
	 * Executes the Update and tries to install it.
	 * 
	 */
	private void externalUpdate() {
		try {
			URL download = new URL(downloadURL);

			BufferedInputStream in = null;
			FileOutputStream fout = null;
			File newFile = null;
			try {
				if (out) {
					plugin.getLogger().info("Trying to download from: " + downloadURL);
				}
				in = new BufferedInputStream(download.openStream());
				String newFileName = PluginLoader.getInstance().getName().toLowerCase() + "-" + version.toLowerCase()
						+ ".jar";
				newFile = new File(PluginLoader.getInstance().getDataFolder(), newFileName);
				fout = new FileOutputStream(newFile);

				final byte data[] = new byte[1024];
				int count;
				while ((count = in.read(data, 0, 1024)) != -1) {
					fout.write(data, 0, count);
				}
			} finally {
				if (in != null) {
					in.close();
				}
				if (fout != null) {
					fout.close();
				}
			}

			if (newFile.exists()) {
				URL oldFileURL = Bukkit.getPluginManager().getPlugin(PluginLoader.getInstance().getName()).getClass()
						.getProtectionDomain().getCodeSource().getLocation();
				String[] splittedPath = oldFileURL.getPath().split("/");
				String oldFileName = splittedPath[splittedPath.length - 1];
				File oldFile = new File(PluginLoader.getInstance().getDataFolder().getParentFile(), oldFileName);

				if (oldFile.exists()) {
					oldFile.delete();
				}

				// File newPluginFile = new
				// File(PluginLoader.getInstance().getDataFolder().getParentFile(),
				// newFile.getName());
				File newPluginFile = new File(PluginLoader.getInstance().getServer().getUpdateFolderFile(),
						oldFile.getName());
				if (!newPluginFile.getParentFile().exists()) {
					newPluginFile.getParentFile().mkdirs();
				}
				Files.copy(newFile, newPluginFile);
				newFile.delete();
			}

			if (out) {
				plugin.getLogger().info("Succesfully downloaded file: " + downloadURL);
				plugin.getLogger().info("To have the Features, you should restart your Server!");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private class UpdateRunnable implements Runnable {

		@Override
		public void run() {
			Updater.this.enableOut();
			if (Updater.this.needsUpdate()) {
				Updater.this.externalUpdate();
			}

		}
	}
}