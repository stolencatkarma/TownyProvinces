package io.github.townyadvanced.townyprovinces.commands;

import com.palmergames.bukkit.towny.TownyEconomyHandler;
import com.palmergames.bukkit.towny.TownyMessaging;
import com.palmergames.bukkit.towny.object.Coord;
import com.palmergames.bukkit.towny.object.Translatable;
import com.palmergames.bukkit.towny.object.Translation;
import com.palmergames.bukkit.towny.utils.NameUtil;
import com.palmergames.bukkit.util.ChatTools;
import com.palmergames.util.StringMgmt;
import io.github.townyadvanced.townyprovinces.TownyProvinces;
import io.github.townyadvanced.townyprovinces.data.TownyProvincesDataHolder;
import io.github.townyadvanced.townyprovinces.jobs.land_validation.LandValidationJobStatus;
import io.github.townyadvanced.townyprovinces.jobs.land_validation.LandValidationTaskController;
import io.github.townyadvanced.townyprovinces.jobs.map_display.MapDisplayTaskController;
import io.github.townyadvanced.townyprovinces.jobs.province_generation.RegenerateRegionTaskController;
import io.github.townyadvanced.townyprovinces.messaging.Messaging;
import io.github.townyadvanced.townyprovinces.objects.Province;
import io.github.townyadvanced.townyprovinces.objects.ProvinceType;
import io.github.townyadvanced.townyprovinces.objects.Region;
import io.github.townyadvanced.townyprovinces.settings.TownyProvincesPermissionNodes;
import io.github.townyadvanced.townyprovinces.settings.TownyProvincesSettings;
import io.github.townyadvanced.townyprovinces.util.FileUtil;
import io.github.townyadvanced.townyprovinces.util.MoneyUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.io.File;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class TownyProvincesAdminCommand implements TabExecutor {

   private static final List<String> adminTabCompletes = Arrays.asList("province","region","landvalidationjob", "reload", "claimAllPlots");
   private static final List<String> adminTabCompletesProvince = Arrays.asList("settype", "homeblock", "border");
	private static final List<String> adminTabCompletesProvinceSetType = Arrays.asList("civilized","sea","wasteland");
	private static final List<String> adminTabCompletesRegion = Arrays.asList("regenerate", "newtowncostperchunk", "upkeeptowncostperchunk");
	private static final List<String> adminTabCompletesSeaProvincesJob = Arrays.asList("status", "start", "stop", "restart", "pause");

   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

	   switch (args[0].toLowerCase()) {
		   case "province":
			   if (args.length == 2) {
				   return NameUtil.filterByStart(adminTabCompletesProvince, args[1]);
			   } else if (args.length == 3 && args[1].equalsIgnoreCase("settype")) {
				   return NameUtil.filterByStart(adminTabCompletesProvinceSetType, args[2]);
			   }
			   break;
			case "region":
				if (args.length == 2)
					return NameUtil.filterByStart(adminTabCompletesRegion, args[1]);
				if (args.length == 3) {
					//Create data folder if needed
					FileUtil.setupPluginDataFoldersIfRequired();
					//Create region definitions folder and sample files if needed
					FileUtil.createRegionDefinitionsFolderAndSampleFiles();
					//Reload region definitions
					TownyProvincesSettings.loadRegionsDefinitions();
					List<String> regionOptions = new ArrayList<>();
					regionOptions.add("All");
					List<String> regionNames = new ArrayList<>(TownyProvincesSettings.getRegions().keySet());
					Collections.sort(regionNames);
					regionOptions.addAll(regionNames);
					return NameUtil.filterByStart(regionOptions, args[2]);
				}
				break;
			case "landvalidationjob":
				if (args.length == 2)
					return NameUtil.filterByStart(adminTabCompletesSeaProvincesJob, args[1]);
				break;
			default:
				if (args.length == 1)
					return NameUtil.filterByStart(adminTabCompletes, args[0]);
		}
		return Collections.emptyList();
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args) {
		parseAdminCommand(sender, args);
		return true;
	}

	private void parseAdminCommand(CommandSender sender, String[] args) {
		/*
		 * Parse Command.
		 */
		if (args.length > 0) {
			if (sender instanceof Player && !sender.hasPermission(TownyProvincesPermissionNodes.TOWNYPROVINCES_ADMIN.getNode(args[0]))) {
				Messaging.sendErrorMsg(sender, Translatable.of("msg_err_command_disable"));
				return;
			}
		   switch (args[0]) {
			   case "province":
				   parseProvinceCommand(sender, StringMgmt.remFirstArg(args));
				   break;
			   case "region":
				   parseRegionCommand(sender, StringMgmt.remFirstArg(args));
				   break;
			   case "landvalidationjob":
				   parseLandValidationJobCommand(sender, StringMgmt.remFirstArg(args));
				   break;
			   case "reload":
				   parseReloadCommand(sender, StringMgmt.remFirstArg(args));
				   break;
			   case "claimAllPlots":
				   parseClaimAllPlotsCommand(sender);
				   break;
			   default:
				   showHelp(sender);
		   }
	   }
	   }

   /**
	* Claims all unclaimed plots in the province where the player is standing for their own town.
	*/
   private void parseClaimAllPlotsCommand(CommandSender sender) {
	   if (!(sender instanceof Player)) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_command_disable"));
		   return;
	   }
	   Player player = (Player) sender;
	   com.palmergames.bukkit.towny.object.Resident resident = com.palmergames.bukkit.towny.TownyAPI.getInstance().getResident(player);
	   if (resident == null || !resident.hasTown()) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_command_disable"));
		   return;
	   }
	   com.palmergames.bukkit.towny.object.Town town = resident.getTownOrNull();
	   if (town == null) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_command_disable"));
		   return;
	   }
	   org.bukkit.Location loc = player.getLocation();
	   String worldName = loc.getWorld().getName();
	   int blockX = loc.getBlockX();
	   int blockZ = loc.getBlockZ();
	   int x = blockX >> 4;
	   int z = blockZ >> 4;
	   // Re-read province data for the home block
	   File provinceFile = new File(TownyProvinces.getPlugin().getDataFolder().toPath().resolve("data/provinces/province_x" + x + "_z_" + z + ".yml").toString());
	   Province province = null;
	   if (provinceFile.exists()) {
		   io.github.townyadvanced.townyprovinces.data.DataHandlerUtil.loadProvince(provinceFile);
		   province = TownyProvincesDataHolder.getInstance().getProvinceAtCoord(x, z);
	   } else {
		   province = TownyProvincesDataHolder.getInstance().getProvinceAtCoord(x, z);
	   }
	   if (province == null) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_invalid_province_location"));
		   return;
	   }
	   // Require player to stand on the home block
	   io.github.townyadvanced.townyprovinces.objects.TPCoord homeBlock = province.getHomeBlock();
	   // Debug: Print player and home block positions
	   TownyProvinces.info("[claimAllPlots] Player block position: x=" + blockX + ", z=" + blockZ + ", chunk x=" + x + ", chunk z=" + z);
	   TownyProvinces.info("[claimAllPlots] Province home block: chunk x=" + homeBlock.getX() + ", chunk z=" + homeBlock.getZ());
	   if (x != homeBlock.getX() || z != homeBlock.getZ()) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_not_on_homeblock"));
		   return;
	   }
	   // Check if the province's home block is claimed (main world only)
	   com.palmergames.bukkit.towny.object.WorldCoord homeBlockWC = new com.palmergames.bukkit.towny.object.WorldCoord(worldName, homeBlock.getX(), homeBlock.getZ());
	   if (com.palmergames.bukkit.towny.TownyAPI.getInstance().isWilderness(homeBlockWC)) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_no_townblock_in_province"));
		   return;
	   }
	   // Flood fill from the player's location to find all contiguous wilderness plots within the province
	   java.util.Set<String> visited = new java.util.HashSet<>();
	   java.util.Queue<io.github.townyadvanced.townyprovinces.objects.TPCoord> queue = new java.util.LinkedList<>();
	   io.github.townyadvanced.townyprovinces.objects.TPCoord startTPCoord = new io.github.townyadvanced.townyprovinces.objects.TPFinalCoord(x, z);
	   queue.add(startTPCoord);
	   visited.add(x + "," + z);
	   java.util.List<com.palmergames.bukkit.towny.object.WorldCoord> coordsToClaim = new java.util.ArrayList<>();
	   java.util.Set<String> provinceCoords = new java.util.HashSet<>();
	   
	   // Create a set of coordinates within the province for easier lookup
	   for (io.github.townyadvanced.townyprovinces.objects.TPCoord tp : province.getListOfCoordsInProvince()) {
		   provinceCoords.add(tp.getX() + "," + tp.getZ());
	   }
	   
	   // Make sure the starting point is actually in the province
	   if (!provinceCoords.contains(x + "," + z)) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_invalid_province_location"));
		   return;
	   }
	   
	   int[][] directions = { {1,0}, {-1,0}, {0,1}, {0,-1} };
	   while (!queue.isEmpty()) {
		   io.github.townyadvanced.townyprovinces.objects.TPCoord curr = queue.poll();
		   com.palmergames.bukkit.towny.object.WorldCoord currWC = new com.palmergames.bukkit.towny.object.WorldCoord(worldName, curr.getX(), curr.getZ());
		   if (com.palmergames.bukkit.towny.TownyAPI.getInstance().isWilderness(currWC)) {
			   coordsToClaim.add(currWC);
			   // Explore neighbors
			   for (int[] dir : directions) {
				   int nx = curr.getX() + dir[0];
				   int nz = curr.getZ() + dir[1];
				   String key = nx + "," + nz;
				   if (!provinceCoords.contains(key)) continue; // Stay within province
				   if (visited.contains(key)) continue;
				   
				   visited.add(key); // Mark as visited right away
				   
				   com.palmergames.bukkit.towny.object.WorldCoord neighborWC = new com.palmergames.bukkit.towny.object.WorldCoord(worldName, nx, nz);
				   if (com.palmergames.bukkit.towny.TownyAPI.getInstance().isWilderness(neighborWC)) {
					   queue.add(new io.github.townyadvanced.townyprovinces.objects.TPFinalCoord(nx, nz));
				   }
			   }
		   }
	   }
	   // 4. Attempt to claim all found wilderness plots
	   int claimed = 0;
	   
	   // Save original location to return player later
	   org.bukkit.Location originalLocation = player.getLocation();
	   
	   try {
		   for (com.palmergames.bukkit.towny.object.WorldCoord worldCoord : coordsToClaim) {
			   try {
				   org.bukkit.World bukkitWorld = org.bukkit.Bukkit.getWorld(worldCoord.getWorldName());
				   if (bukkitWorld == null) continue;
				   int bx = (worldCoord.getX() << 4) + 8;
				   int bz = (worldCoord.getZ() << 4) + 8;
				   
				   // Find suitable Y position (don't teleport into void or solid blocks)
				   int by = 64;
				   if (bukkitWorld.getHighestBlockYAt(bx, bz) > 0) {
					   by = bukkitWorld.getHighestBlockYAt(bx, bz);
				   }
				   
				   // Verify this plot is still wilderness before teleporting
				   if (!com.palmergames.bukkit.towny.TownyAPI.getInstance().isWilderness(worldCoord)) {
					   continue;
				   }
				   
				   org.bukkit.Location claimLoc = new org.bukkit.Location(bukkitWorld, bx, by, bz);
				   player.teleport(claimLoc);
				   
				   // Brief delay to allow teleport to complete
				   try {
					   Thread.sleep(50);
				   } catch (InterruptedException e) {
					   Thread.currentThread().interrupt();
				   }
				   
				   boolean result = player.performCommand("town claim");
				   if (result) claimed++;
			   } catch (Exception e) {
				   // Ignore already claimed or error
			   }
		   }
	   } finally {
		   // Return the player to their original location
		   player.teleport(originalLocation);
	   }
	   
	   Messaging.sendMsg(sender, Translatable.of("msg_claimed_plots_in_province", String.valueOf(claimed)));
   }

   private void showHelp(CommandSender sender) {
	   TownyMessaging.sendMessage(sender, ChatTools.formatTitle("/townyprovincesadmin"));
	   TownyMessaging.sendMessage(sender, ChatTools.formatCommand("Eg", "/tpra", "province settype [civilized|sea|wasteland] [<x>,<z>]", ""));
	   TownyMessaging.sendMessage(sender, ChatTools.formatCommand("Eg", "/tpra", "province settype [civilized|sea|wasteland] [<x>,<z>] [<x>,<z>]", ""));
	   TownyMessaging.sendMessage(sender, ChatTools.formatCommand("Eg", "/tpra", "region [regenerate] [<Region Name>]", ""));
	   TownyMessaging.sendMessage(sender, ChatTools.formatCommand("Eg", "/tpra", "region [newtowncostperchunk] [<Region Name>] [amount]", ""));
	   TownyMessaging.sendMessage(sender, ChatTools.formatCommand("Eg", "/tpra", "region [upkeeptowncostperchunk] [<Region Name>] [amount]", ""));
	   TownyMessaging.sendMessage(sender, ChatTools.formatCommand("Eg", "/tpra", "landvalidationjob [status|start|stop|restart|pause]", ""));
	   TownyMessaging.sendMessage(sender, ChatTools.formatCommand("Eg", "/tpra", "claimAllPlots", "Claims all unclaimed plots in your province for your town."));
	   TownyMessaging.sendMessage(sender, ChatTools.formatCommand("Eg", "/tpra", "reload", ""));
   }

	private void parseReloadCommand(CommandSender sender, String[] args) {
		TownyProvinces.getPlugin().reloadConfigsAndData();
	}

   private void parseProvinceCommand(CommandSender sender, String[] args) {
	   if (args.length < 1) {
		   showHelp(sender);
		   return;
	   }
	   if (args[0].equalsIgnoreCase("settype")) {
		   if (args.length < 2) {
			   showHelp(sender);
			   return;
		   }
		   parseProvinceSetTypeCommand(sender, args);
	   } else if (args[0].equalsIgnoreCase("homeblock")) {
		   parseProvinceHomeBlockCommand(sender);
	   } else if (args[0].equalsIgnoreCase("border")) {
		   parseProvinceBorderCommand(sender);
	   } else {
		   showHelp(sender);
	   }
   }

   /**
	* Shows the border of the province at the player's location.
	*/
   private void parseProvinceBorderCommand(CommandSender sender) {
	   if (!(sender instanceof Player)) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_command_disable"));
		   return;
	   }
	   Player player = (Player) sender;
	   org.bukkit.Location loc = player.getLocation();
	   int x = loc.getBlockX() >> 4;
	   int z = loc.getBlockZ() >> 4;
	   Province province = TownyProvincesDataHolder.getInstance().getProvinceAtCoord(x, z);
	   if (province == null) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_invalid_province_location"));
		   TownyProvinces.info("[BorderCmd] No province found at player's location: " + x + "," + z);
		   return;
	   }

	   Messaging.sendMsg(sender, "Displaying border for province: " + province.getId());
	   TownyProvinces.info("[BorderCmd] Province found: " + province.getId() + ". It has " + province.getListOfCoordsInProvince().size() + " coords.");

	   //Get all border chunks
	   java.util.List<io.github.townyadvanced.townyprovinces.objects.TPCoord> borderCoords = new java.util.ArrayList<>();
	   for (io.github.townyadvanced.townyprovinces.objects.TPCoord coord : province.getListOfCoordsInProvince()) {
		   boolean isBorder = false;
		   int[][] directions = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
		   for (int[] dir : directions) {
			   Province neighbor = TownyProvincesDataHolder.getInstance().getProvinceAtCoord(coord.getX() + dir[0], coord.getZ() + dir[1]);
			   if (neighbor == null || !neighbor.equals(province)) {
				   isBorder = true;
				   break;
			   }
		   }
		   if (isBorder) {
			   borderCoords.add(coord);
		   }
	   }
	   TownyProvinces.info("[BorderCmd] Found " + borderCoords.size() + " total border chunks.");

	   if (borderCoords.isEmpty()) {
		   Messaging.sendMsg(sender, "Could not find any border chunks for this province.");
		   return;
	   }

	   // Use a scheduler to show particles in an animated way
	   new org.bukkit.scheduler.BukkitRunnable() {
		   int animationTick = 0;
		   final int durationTicks = 200; // 10 seconds
		   final int viewDistanceChunks = 8; //Only show particles within this many chunks of the player
		   final int particlesPerPulse = 30; //How many border chunks to light up at once
		   final org.bukkit.Particle particle = org.bukkit.Particle.FLAME;
		   final org.bukkit.World world = player.getWorld();

		   @Override
		   public void run() {
			   if (animationTick > durationTicks || !player.isOnline()) {
				   this.cancel();
				   return;
			   }

			   org.bukkit.Location playerLoc = player.getLocation();
			   int playerChunkX = playerLoc.getBlockX() >> 4;
			   int playerChunkZ = playerLoc.getBlockZ() >> 4;

			   // Determine which segment of the border to show
			   int currentPulse = animationTick / 10;
			   int startIndex = (currentPulse * particlesPerPulse) % borderCoords.size();

			   for (int i = 0; i < particlesPerPulse; i++) {
				   int currentIndex = (startIndex + i) % borderCoords.size();
				   io.github.townyadvanced.townyprovinces.objects.TPCoord coord = borderCoords.get(currentIndex);
				   int chunkX = coord.getX();
				   int chunkZ = coord.getZ();

				   //Only show particles near the player
				   if (Math.abs(chunkX - playerChunkX) > viewDistanceChunks || Math.abs(chunkZ - playerChunkZ) > viewDistanceChunks) {
					   continue;
				   }

				   //Only show particles in loaded chunks
				   if (!world.isChunkLoaded(chunkX, chunkZ)) {
					   continue;
				   }

				   //Simplified: Particle in the middle of the chunk
				   int bx = (chunkX << 4) + 8;
				   int bz = (chunkZ << 4) + 8;
				   int by = world.getHighestBlockYAt(bx, bz) + 2;
				   org.bukkit.Location particleLoc = new org.bukkit.Location(world, bx, by, bz);
				   world.spawnParticle(particle, particleLoc, 5, 0.5, 0.5, 0.5, 0);
			   }
			   animationTick += 10;
		   }
	   }.runTaskTimer(TownyProvinces.getPlugin(), 0L, 10L); // Run every 10 ticks (half a second)
   }

   /**
	* Shows the home block chunk coordinates and province name for the province at the player's location.
	*/
   private void parseProvinceHomeBlockCommand(CommandSender sender) {
	   if (!(sender instanceof Player)) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_command_disable"));
		   return;
	   }
	   Player player = (Player) sender;
	   org.bukkit.Location loc = player.getLocation();
	   int x = loc.getBlockX() >> 4;
	   int z = loc.getBlockZ() >> 4;
	   Province province = TownyProvincesDataHolder.getInstance().getProvinceAtCoord(x, z);
	   if (province == null) {
		   Messaging.sendMsg(sender, Translatable.of("msg_err_invalid_province_location"));
		   return;
	   }
	   io.github.townyadvanced.townyprovinces.objects.TPCoord homeBlock = province.getHomeBlock();
	   String msg = "Province ID: " + province.getId() + ", Home Block Chunk: x=" + homeBlock.getX() + ", z=" + homeBlock.getZ();
	   Messaging.sendMsg(sender, msg);

	   // Sparkle effect: spawn particles in a ring at the center of the home block chunk
	   org.bukkit.World world = player.getWorld();
	   int chunkX = homeBlock.getX();
	   int chunkZ = homeBlock.getZ();
	   double centerX = (chunkX << 4) + 8.5;
	   double centerZ = (chunkZ << 4) + 8.5;
	   int y = world.getHighestBlockYAt((int)centerX, (int)centerZ) + 1;
	   // Show a ring of particles for 3 seconds (60 ticks)
	   int durationTicks = 60;
	   int points = 24;
	   org.bukkit.Particle particle = org.bukkit.Particle.VILLAGER_HAPPY;
	   for (int t = 0; t < durationTicks; t += 5) { // Fire every 5 ticks
		   int delay = t;
		   new org.bukkit.scheduler.BukkitRunnable() {
			   @Override
			   public void run() {
				   for (int i = 0; i < points; i++) {
					   double angle = 2 * Math.PI * i / points;
					   double dx = 7.5 * Math.cos(angle); // 7.5 block radius
					   double dz = 7.5 * Math.sin(angle);
					   org.bukkit.Location particleLoc = new org.bukkit.Location(world, centerX + dx, y, centerZ + dz);
					   world.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
				   }
			   }
		   }.runTaskLater(TownyProvinces.getPlugin(), delay);
	   }
   }
	
	private void parseRegionCommand(CommandSender sender, String[] args) {
		if (args.length < 2) {
			showHelp(sender);
			return;
		}
		if (args[0].equalsIgnoreCase("regenerate")) {
			parseRegionRegenerateCommand(sender, args);
		} else if (args[0].equalsIgnoreCase("newtowncostperchunk")) {
			TownyProvinces.getPlugin().getScheduler().runAsync(() -> parseRegionSetNewTownCostCommand(sender, args));
		} else if (args[0].equalsIgnoreCase("upkeeptowncostperchunk")) {
			TownyProvinces.getPlugin().getScheduler().runAsync(() -> parseRegionSetTownUpkeepCostCommand(sender, args));
		} else {
			showHelp(sender);
		}
	}

	private void parseLandValidationJobCommand(CommandSender sender, String[] args) {
		if (args.length < 1) {
			showHelp(sender);
			return;
		}
		if (args[0].equalsIgnoreCase("status")) {
			Translatable status = Translatable.of(LandValidationTaskController.getLandValidationJobStatus().getLanguageKey());
			Messaging.sendMsg(sender, Translatable.of("msg_land_validation_job_status").append(status));
			
		} else if (args[0].equalsIgnoreCase("start")) {
			if (LandValidationTaskController.getLandValidationJobStatus().equals(LandValidationJobStatus.STOPPED)
					|| LandValidationTaskController.getLandValidationJobStatus().equals(LandValidationJobStatus.PAUSED)) {
				Messaging.sendMsg(sender, Translatable.of("msg_land_validation_job_starting"));
				LandValidationTaskController.setLandValidationJobStatus(LandValidationJobStatus.START_REQUESTED);
				LandValidationTaskController.startTask();
			} else {
				Messaging.sendMsg(sender, Translatable.of("msg_err_command_not_possible_job_not_stopped_or_paused"));
			}
		
		} else if (args[0].equalsIgnoreCase("stop")) {
			if (LandValidationTaskController.getLandValidationJobStatus().equals(LandValidationJobStatus.STARTED)) {
				Messaging.sendMsg(sender, Translatable.of("msg_land_validation_job_stopping"));
				LandValidationTaskController.setLandValidationJobStatus(LandValidationJobStatus.STOP_REQUESTED);
			} else {
				Messaging.sendMsg(sender, Translatable.of("msg_err_command_not_possible_job_not_started"));
			}
			
		} else if (args[0].equalsIgnoreCase("pause")) {
			if (LandValidationTaskController.getLandValidationJobStatus().equals(LandValidationJobStatus.STARTED)) {
				Messaging.sendMsg(sender, Translatable.of("msg_land_validation_job_pausing"));
				LandValidationTaskController.setLandValidationJobStatus(LandValidationJobStatus.PAUSE_REQUESTED);
			} else {
				Messaging.sendMsg(sender, Translatable.of("msg_err_command_not_possible_job_not_started"));
			}

		} else if (args[0].equalsIgnoreCase("restart")) {
			if (LandValidationTaskController.getLandValidationJobStatus().equals(LandValidationJobStatus.STARTED)) {
				Messaging.sendMsg(sender, Translatable.of("msg_land_validation_job_restarting"));
				LandValidationTaskController.setLandValidationJobStatus(LandValidationJobStatus.RESTART_REQUESTED);
			} else {
				Messaging.sendMsg(sender, Translatable.of("msg_err_command_not_possible_job_not_started"));
			}
		} else {
			showHelp(sender);
		}
	}
	
	private void parseProvinceSetTypeCommand(CommandSender sender, String[] args) {
		try {
			ProvinceType provinceType;
			if(args[1].equalsIgnoreCase("civilized")) {
				provinceType = ProvinceType.CIVILIZED;
			} else if(args[1].equalsIgnoreCase("sea")) {
				provinceType = ProvinceType.SEA;
			} else if(args[1].equalsIgnoreCase("wasteland")) {
				provinceType = ProvinceType.WASTELAND;
			} else {
				showHelp(sender);
				return;
			}
				
			if (args.length == 3) {
				parseProvinceSetTypeCommandByPoint(sender, args, provinceType);
			} else if (args.length == 4){
				parseProvinceSetTypeCommandByArea(sender, args, provinceType);
			} else{
				showHelp(sender);
			}
		} catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
			Messaging.sendMsg(sender, Translatable.of("msg_err_invalid_province_location"));
			showHelp(sender);
		}
	}
	
	private void parseProvinceSetTypeCommandByPoint(CommandSender sender, String[] args, ProvinceType provinceType) throws NumberFormatException, ArrayIndexOutOfBoundsException{
		String[] locationAsArray = args[2].split(",");
		if(locationAsArray.length != 2) {
			Messaging.sendMsg(sender, Translatable.of("msg_err_invalid_province_location"));
			showHelp(sender);
			return;
		}
		int x = Integer.parseInt(locationAsArray[0]);
		int y = Integer.parseInt(locationAsArray[1]);
		Coord coord = Coord.parseCoord(x,y);
		Province province = TownyProvincesDataHolder.getInstance().getProvinceAtCoord(coord.getX(), coord.getZ());
		//Validate action
		if(province == null) {
			Messaging.sendMsg(sender, Translatable.of("msg_err_invalid_province_location"));
			showHelp(sender);
			return;
		}

		//Error if province is already the given type
		String typeTranslated = Translation.of("word_" + provinceType.name().toLowerCase());
		if(province.getType() == provinceType) {
			Messaging.sendMsg(sender, Translatable.of("msg_province_already_given_type", typeTranslated));
			return;
		}
		
		//Set province type
		province.setType(provinceType);
		province.saveData();
		MapDisplayTaskController.requestHomeBlocksRefresh();
		Messaging.sendMsg(sender, Translatable.of("msg_province_type_successfully_set", typeTranslated));
	}

	private void parseProvinceSetTypeCommandByArea(CommandSender sender, String[] args, ProvinceType provinceType) {
		String[] topLeftCornerAsArray = args[2].split(",");
		if(topLeftCornerAsArray.length != 2) {
			Messaging.sendMsg(sender, Translatable.of("msg_err_invalid_province_location"));
			showHelp(sender);
			return;
		}
		String[] bottomRightCornerAsArray = args[3].split(",");
		if(bottomRightCornerAsArray.length != 2) {
			Messaging.sendMsg(sender, Translatable.of("msg_err_invalid_province_location"));
			showHelp(sender);
			return;
		}
		int topLeftX = Integer.parseInt(topLeftCornerAsArray[0]);
		int topLeftZ = Integer.parseInt(topLeftCornerAsArray[1]);
		int bottomRightX = Integer.parseInt(bottomRightCornerAsArray[0]);
		int bottomRightZ = Integer.parseInt(bottomRightCornerAsArray[1]);
		
		Set<Province> provinces = TownyProvincesDataHolder.getInstance().getProvincesInArea(topLeftX, topLeftZ, bottomRightX, bottomRightZ);
		
		for(Province province: provinces) {
			if(province.getType() != provinceType) {
				province.setType(provinceType);
				province.saveData();
			}
		}
		
		MapDisplayTaskController.requestHomeBlocksRefresh();
		String typeTranslated = Translation.of("word_" + provinceType.name().toLowerCase());
		Messaging.sendMsg(sender, Translatable.of("msg_province_types_in_area_successfully_set", typeTranslated));
	}
	
	private void parseRegionRegenerateCommand(CommandSender sender, String[] args) {
		//Create data folder if needed
		FileUtil.setupPluginDataFoldersIfRequired();
		//Create region definitions folder and sample files if needed
		FileUtil.createRegionDefinitionsFolderAndSampleFiles();
		//Reload region definitions
		TownyProvincesSettings.loadRegionsDefinitions();
		//Verify the given region name
		String givenRegionName = args[1];
		String caseCorrectRegionName = TownyProvincesSettings.getCaseSensitiveRegionName(givenRegionName);
		if(givenRegionName.equalsIgnoreCase("all")) {
			RegenerateRegionTaskController.startTask(sender, givenRegionName);
		} else if(TownyProvincesSettings.getRegions().containsKey(caseCorrectRegionName)) {
			RegenerateRegionTaskController.startTask(sender, caseCorrectRegionName);
		} else {
			Messaging.sendMsg(sender, Translatable.of("msg_err_unknown_region_name"));
		}
	}

	private void parseRegionSetNewTownCostCommand(CommandSender sender, String[] args) {
		TownyProvinces.info("Getting synch locks.");
		synchronized (TownyProvinces.MAP_DISPLAY_JOB_LOCK) {
			synchronized (TownyProvinces.REGION_REGENERATION_JOB_LOCK) {
				synchronized (TownyProvinces.PRICE_RECALCULATION_JOB_LOCK) {
					synchronized (TownyProvinces.LAND_VALIDATION_JOB_LOCK) {
						TownyProvinces.info("Synch locks acquired.");
						try {
							String givenRegionName = args[1];
							double townCostPerChunk = Double.parseDouble(args[2]);
							String formattedTownCostPerChunk = TownyEconomyHandler.getFormattedBalance(townCostPerChunk);
							;
							double townCost;
							String caseCorrectRegionName = TownyProvincesSettings.getCaseSensitiveRegionName(givenRegionName);

							if (givenRegionName.equalsIgnoreCase("all")) {
								//Set cost for all provinces, regardless of region
								for (Province province : TownyProvincesDataHolder.getInstance().getProvincesSet()) {
									townCost = townCostPerChunk * province.getListOfCoordsInProvince().size();
									province.setNewTownCost(townCost);
									province.saveData();
								}
								MoneyUtil.recalculateProvincePrices();
								MapDisplayTaskController.requestHomeBlocksRefresh();
								Messaging.sendMsg(sender, Translatable.of("msg_new_town_cost_set_for_all_regions", formattedTownCostPerChunk));

							} else if (TownyProvincesSettings.getRegions().containsKey(caseCorrectRegionName)) {
								//Set cost for just one region
								Region region = TownyProvincesSettings.getRegion(caseCorrectRegionName);
								for (Province province : TownyProvincesDataHolder.getInstance().getProvincesSet()) {
									if (TownyProvincesSettings.isProvinceInRegion(province, region)) {
										townCost = townCostPerChunk * province.getListOfCoordsInProvince().size();
										province.setNewTownCost(townCost);
										province.saveData();
									}
								}
								MoneyUtil.recalculateProvincePrices();
								MapDisplayTaskController.requestHomeBlocksRefresh();
								Messaging.sendMsg(sender, Translatable.of("msg_new_town_cost_set_for_one_region", caseCorrectRegionName, formattedTownCostPerChunk));

							} else {
								Messaging.sendMsg(sender, Translatable.of("msg_err_unknown_region_name"));
							}
						} catch (NumberFormatException nfe) {
							Messaging.sendMsg(sender, Translatable.of("msg_err_value_must_be_and_integer"));
						}
					}
				}
			}
		}
	}
	
	private void parseRegionSetTownUpkeepCostCommand(CommandSender sender, String[] args) {
		TownyProvinces.info("Getting synch locks.");
		synchronized (TownyProvinces.MAP_DISPLAY_JOB_LOCK) {
			synchronized (TownyProvinces.PRICE_RECALCULATION_JOB_LOCK) {
				synchronized (TownyProvinces.MAP_DISPLAY_JOB_LOCK) {
					synchronized (TownyProvinces.LAND_VALIDATION_JOB_LOCK) {
						TownyProvinces.info("Synch locks acquired.");
						try {
							String givenRegionName = args[1];
							double townCostPerChunk = Double.parseDouble(args[2]);
							String formattedTownCostPerChunk = TownyEconomyHandler.getFormattedBalance(townCostPerChunk);
							;
							double townCost;
							String caseCorrectRegionName = TownyProvincesSettings.getCaseSensitiveRegionName(givenRegionName);

							if (givenRegionName.equalsIgnoreCase("all")) {
								//Set cost for all provinces, regardless of region
								for (Province province : TownyProvincesDataHolder.getInstance().getProvincesSet()) {
									townCost = townCostPerChunk * province.getListOfCoordsInProvince().size();
									province.setUpkeepTownCost(townCost);
									province.saveData();
								}
								//Recalculated all prices
								MoneyUtil.recalculateProvincePrices();
								MapDisplayTaskController.requestHomeBlocksRefresh();
								Messaging.sendMsg(sender, Translatable.of("msg_upkeep_town_cost_set_for_all_regions", formattedTownCostPerChunk));

							} else if (TownyProvincesSettings.getRegions().containsKey(caseCorrectRegionName)) {
								//Set cost for just one region
								Region region = TownyProvincesSettings.getRegion(caseCorrectRegionName);
								for (Province province : TownyProvincesDataHolder.getInstance().getProvincesSet()) {
									if (TownyProvincesSettings.isProvinceInRegion(province, region)) {
										townCost = townCostPerChunk * province.getListOfCoordsInProvince().size();
										province.setUpkeepTownCost(townCost);
										province.saveData();
									}
								}
								MoneyUtil.recalculateProvincePrices();
								MapDisplayTaskController.requestHomeBlocksRefresh();
								Messaging.sendMsg(sender, Translatable.of("msg_upkeep_town_cost_set_for_one_region", caseCorrectRegionName, formattedTownCostPerChunk));

							} else {
								Messaging.sendMsg(sender, Translatable.of("msg_err_unknown_region_name"));
							}
						} catch (NumberFormatException nfe) {
							Messaging.sendMsg(sender, Translatable.of("msg_err_value_must_be_and_integer"));
						}
					}
				}
			}
		}
	}
}

