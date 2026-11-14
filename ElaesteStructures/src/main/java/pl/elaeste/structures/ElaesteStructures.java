package pl.elaeste.structures;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class ElaesteStructures extends JavaPlugin implements Listener, CommandExecutor {
    private final List<Placement> placements = new ArrayList<>();
    private File placementsFile;
    private org.bukkit.configuration.file.YamlConfiguration placementsYaml;
    private StructureBuilder builder;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadPlacements();
        builder = new StructureBuilder(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        if (getCommand("elaeste") != null) getCommand("elaeste").setExecutor(this);
        getLogger().info("ElaesteStructures enabled. Loaded " + placements.size() + " saved placements.");
    }

    @Override
    public void onDisable() {
        savePlacements();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        checkPlayerLocation(event.getPlayer(), event.getPlayer().getLocation());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() >> 4 != event.getTo().getBlockX() >> 4
                || event.getFrom().getBlockZ() >> 4 != event.getTo().getBlockZ() >> 4) {
            checkPlayerLocation(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null) checkPlayerLocation(event.getPlayer(), event.getTo());
    }

    private void checkPlayerLocation(Player player, Location location) {
        String worldName = getConfig().getString("world-name", "World");
        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) return;

        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        long seed = location.getWorld().getSeed()
                ^ (((long) chunkX) * 341873128712L)
                ^ (((long) chunkZ) * 132897987541L);
        Random random = new Random(seed);

        boolean inElaeste = isInsideElaeste(location.getBlockX(), location.getBlockZ());
        double chance = getConfig().getDouble(
                inElaeste ? "elaeste.chance-per-chunk" : "outside.chance-per-chunk",
                inElaeste ? 0.025 : 0.02);

        if (!inElaeste && !getConfig().getBoolean("outside.enabled", true)) return;
        if (random.nextDouble() >= chance) return;

        // Keep one deterministic candidate point per chunk so revisiting a chunk
        // cannot repeatedly reroll a new structure.
        int x = (chunkX << 4) + 4 + random.nextInt(8);
        int z = (chunkZ << 4) + 4 + random.nextInt(8);
        if (inElaeste && !isInsideElaeste(x, z)) return;

        String type = inElaeste ? nextElaesteType() : randomOutsideType(random);
        if (type == null) return;
        if (!footprintStaysInItsArea(x, z, type, inElaeste)) return;

        int minDistance = getConfig().getInt(
                inElaeste ? "elaeste.min-distance" : "outside.min-distance", 150);
        if (!farEnough(location.getWorld(), x, z, minDistance)) return;

        // Run on the main server thread; the player movement event already runs there.
        builder.tryBuild(location.getWorld(), x, z, type, random);
    }

    private boolean isInsideElaeste(int x, int z) {
        return x >= getConfig().getInt("elaeste.min-x", -3800)
                && x <= getConfig().getInt("elaeste.max-x", 3800)
                && z >= getConfig().getInt("elaeste.min-z", -3800)
                && z <= getConfig().getInt("elaeste.max-z", 3800);
    }

    private boolean footprintStaysInItsArea(int x, int z, String type, boolean inElaeste) {
        int radius = switch (type) {
            case "castle_large" -> 19;
            case "castle_medium" -> 14;
            case "settlement" -> 13;
            case "ruin" -> 10;
            case "camp" -> 8;
            default -> 7;
        };
        int minX = getConfig().getInt("elaeste.min-x", -3800);
        int maxX = getConfig().getInt("elaeste.max-x", 3800);
        int minZ = getConfig().getInt("elaeste.min-z", -3800);
        int maxZ = getConfig().getInt("elaeste.max-z", 3800);

        if (inElaeste) {
            return x - radius >= minX && x + radius <= maxX
                    && z - radius >= minZ && z + radius <= maxZ;
        }
        // Outside structures must not spill across the Elaeste rectangle.
        return x + radius < minX || x - radius > maxX
                || z + radius < minZ || z - radius > maxZ;
    }

    private String nextElaesteType() {
        Map<String, Integer> quotas = new java.util.LinkedHashMap<>();
        for (String type : List.of("castle_medium", "castle_large", "tower", "ruin", "settlement", "camp")) {
            int target = getConfig().getInt("elaeste.quotas." + type, 0);
            int done = countType(type);
            if (done < target) quotas.put(type, target - done);
        }
        if (quotas.isEmpty()) return null;

        int totalRemaining = quotas.values().stream().mapToInt(Integer::intValue).sum();
        int pick = new Random().nextInt(totalRemaining);
        for (Map.Entry<String, Integer> entry : quotas.entrySet()) {
            pick -= entry.getValue();
            if (pick < 0) return entry.getKey();
        }
        return null;
    }

    private String randomOutsideType(Random random) {
        String[] types = {"castle_medium", "castle_large", "tower", "ruin", "settlement", "camp"};
        return types[random.nextInt(types.length)];
    }

    private int countType(String type) {
        return (int) placements.stream().filter(p -> p.type.equals(type)
                && isInsideElaeste(p.x, p.z)).count();
    }

    private boolean farEnough(World world, int x, int z, int minDistance) {
        long min2 = (long) minDistance * minDistance;
        for (Placement p : placements) {
            if (!p.world.equals(world.getName())) continue;
            long dx = (long) p.x - x;
            long dz = (long) p.z - z;
            if (dx * dx + dz * dz < min2) return false;
        }
        return true;
    }

    void recordPlacement(World world, int x, int y, int z, String type) {
        placements.add(new Placement(world.getName(), x, y, z, type));
        savePlacements();
    }

    public int getPlacementCount() {
        return placements.size();
    }

    public int getElaesteCount() {
        return (int) placements.stream().filter(p -> isInsideElaeste(p.x, p.z)).count();
    }

    public int getOutsideCount() {
        return placements.size() - getElaesteCount();
    }

    private void loadPlacements() {
        placementsFile = new File(getDataFolder(), "placements.yml");
        if (!placementsFile.exists()) {
            try {
                getDataFolder().mkdirs();
                placementsFile.createNewFile();
            } catch (Exception ex) {
                getLogger().severe("Could not create placements.yml: " + ex.getMessage());
            }
        }
        placementsYaml = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(placementsFile);
        for (String key : placementsYaml.getKeys(false)) {
            String world = placementsYaml.getString(key + ".world", "");
            String type = placementsYaml.getString(key + ".type", "unknown");
            int x = placementsYaml.getInt(key + ".x");
            int y = placementsYaml.getInt(key + ".y");
            int z = placementsYaml.getInt(key + ".z");
            if (!world.isBlank()) placements.add(new Placement(world, x, y, z, type));
        }
    }

    private void savePlacements() {
        if (placementsYaml == null || placementsFile == null) return;
        placementsYaml = new org.bukkit.configuration.file.YamlConfiguration();
        for (int i = 0; i < placements.size(); i++) {
            Placement p = placements.get(i);
            String key = "p" + i;
            placementsYaml.set(key + ".world", p.world);
            placementsYaml.set(key + ".x", p.x);
            placementsYaml.set(key + ".y", p.y);
            placementsYaml.set(key + ".z", p.z);
            placementsYaml.set(key + ".type", p.type);
        }
        try {
            placementsYaml.save(placementsFile);
        } catch (Exception ex) {
            getLogger().severe("Could not save placements.yml: " + ex.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("elaeste.admin")) {
            sender.sendMessage(ChatColor.RED + "Nie masz uprawnien.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage(ChatColor.GOLD + "Elaeste Structures");
            sender.sendMessage(ChatColor.YELLOW + "Wszystkie zapisane budowle: " + placements.size());
            sender.sendMessage(ChatColor.YELLOW + "W granicach Elaeste: " + getElaesteCount() + "/46");
            sender.sendMessage(ChatColor.YELLOW + "Poza Elaeste: " + getOutsideCount());
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage(ChatColor.GREEN + "Konfiguracja przeladowana.");
            return true;
        }
        if (args[0].equalsIgnoreCase("generate")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Tej komendy uzyj w grze.");
                return true;
            }
            Location loc = player.getLocation();
            if (!loc.getWorld().getName().equalsIgnoreCase(getConfig().getString("world-name", "World"))) {
                sender.sendMessage(ChatColor.RED + "Jestes w innym swiecie.");
                return true;
            }
            String type = isInsideElaeste(loc.getBlockX(), loc.getBlockZ())
                    ? nextElaesteType() : randomOutsideType(new Random());
            if (type == null) {
                sender.sendMessage(ChatColor.RED + "Limit 46 budowli na Elaeste zostal osiagniety.");
                return true;
            }
            if (builder.tryBuild(loc.getWorld(), loc.getBlockX(), loc.getBlockZ(), type, new Random())) {
                sender.sendMessage(ChatColor.GREEN + "Wygenerowano: " + type);
            } else {
                sender.sendMessage(ChatColor.RED + "Nie mozna wygenerowac budowli w tym miejscu. Sprobuj na rowniejszym terenie.");
            }
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Uzycie: /elaeste <status|reload|generate>");
        return true;
    }

    record Placement(String world, int x, int y, int z, String type) {}
}
