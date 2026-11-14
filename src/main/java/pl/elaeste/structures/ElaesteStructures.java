package pl.elaeste.structures;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.util.*;

public final class ElaesteStructures extends JavaPlugin implements Listener, CommandExecutor {
    static final List<String> TYPES = List.of("castle","mushroom_loft","fantasy_tower","magical_keep",
            "dragon_fortress","sky_kingdom","elven_city","cursed_manor","dwarven_mine","desert_temple");
    private final List<Placement> placements = new ArrayList<>();
    private File placementsFile;
    private org.bukkit.configuration.file.YamlConfiguration data;
    private StructureBuilder builder;

    @Override public void onEnable() {
        saveDefaultConfig(); loadPlacements(); builder = new StructureBuilder(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("elaeste")).setExecutor(this);
        getLogger().info("Elaeste Structures 2.0 enabled. Placements: " + placements.size());
    }
    @Override public void onDisable() { savePlacements(); }

    @EventHandler public void onJoin(PlayerJoinEvent e) { check(e.getPlayer(), e.getPlayer().getLocation()); }
    @EventHandler public void onMove(PlayerMoveEvent e) {
        if (e.getTo()==null) return;
        if ((e.getFrom().getBlockX()>>4)!=(e.getTo().getBlockX()>>4) ||
            (e.getFrom().getBlockZ()>>4)!=(e.getTo().getBlockZ()>>4)) check(e.getPlayer(),e.getTo());
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent e) {
        if(e.getTo()!=null) check(e.getPlayer(),e.getTo());
    }

    // Intercept the vanilla-looking syntax before the server's built-in /locate command.
    // This makes /locate structure search this plugin's saved generated structures.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLocatePreprocess(PlayerCommandPreprocessEvent e) {
        String[] parts = e.getMessage().trim().split("\\s+");
        if (parts.length >= 2 && parts[0].equalsIgnoreCase("/locate")
                && parts[1].equalsIgnoreCase("structure")) {
            e.setCancelled(true);
            String[] args = Arrays.copyOfRange(parts, 2, parts.length);
            locateStructure(e.getPlayer(), args);
        }
    }

    private void check(Player player, Location loc) {
        if(loc.getWorld()==null || !loc.getWorld().getName().equalsIgnoreCase(getConfig().getString("world-name","World"))) return;
        int cx=loc.getBlockX()>>4, cz=loc.getBlockZ()>>4;
        long seed=loc.getWorld().getSeed() ^ ((long)cx*341873128712L) ^ ((long)cz*132897987541L);
        Random r=new Random(seed);
        boolean inside=isInside(loc.getBlockX(),loc.getBlockZ());
        if(!inside && !getConfig().getBoolean("outside.enabled",true)) return;
        double chance=getConfig().getDouble(inside?"elaeste.chance-per-chunk":"outside.chance-per-chunk",inside?0.025:0.012);
        if(r.nextDouble()>=chance) return;
        int x=(cx<<4)+4+r.nextInt(8), z=(cz<<4)+4+r.nextInt(8);
        if(inside && !isInside(x,z)) return;
        String type=inside?nextQuotaType():randomType(r);
        if(type==null || !footprintFits(x,z,type,inside)) return;
        if(!farEnough(loc.getWorld(),x,z,getConfig().getInt(inside?"elaeste.min-distance":"outside.min-distance",180))) return;
        builder.tryBuild(loc.getWorld(),x,z,type,r);
    }
    private boolean isInside(int x,int z) {
        return x>=getConfig().getInt("elaeste.min-x",-3800)&&x<=getConfig().getInt("elaeste.max-x",3800)
            &&z>=getConfig().getInt("elaeste.min-z",-3800)&&z<=getConfig().getInt("elaeste.max-z",3800);
    }
    private int radius(String t) {
        return switch(t) {
            case "castle","magical_keep","dragon_fortress","sky_kingdom","elven_city","dwarven_mine" -> 30;
            case "mushroom_loft","cursed_manor","desert_temple" -> 24;
            default -> 18;
        };
    }
    private boolean footprintFits(int x,int z,String type,boolean inside) {
        int r=radius(type), minX=getConfig().getInt("elaeste.min-x",-3800),maxX=getConfig().getInt("elaeste.max-x",3800),
            minZ=getConfig().getInt("elaeste.min-z",-3800),maxZ=getConfig().getInt("elaeste.max-z",3800);
        if(inside) return x-r>=minX&&x+r<=maxX&&z-r>=minZ&&z+r<=maxZ;
        return x+r<minX||x-r>maxX||z+r<minZ||z-r>maxZ;
    }
    private String nextQuotaType() {
        Map<String,Integer> remaining=new LinkedHashMap<>();
        for(String t:TYPES) { int target=getConfig().getInt("elaeste.quotas."+t, t.equals("castle")?6:4);
            int count=(int)placements.stream().filter(p->p.type.equals(t)&&isInside(p.x,p.z)).count();
            if(count<target) remaining.put(t,target-count);
        }
        int total=remaining.values().stream().mapToInt(i->i).sum(); if(total==0)return null;
        int pick=new Random().nextInt(total);
        for(var e:remaining.entrySet()){pick-=e.getValue();if(pick<0)return e.getKey();}
        return null;
    }
    private String randomType(Random r) {
        // Sky Kingdom and Dragon Fortress are deliberately rarer outside the map.
        int n=r.nextInt(100);
        if(n<7)return "sky_kingdom"; if(n<16)return "dragon_fortress";
        return TYPES.get(r.nextInt(8));
    }
    private boolean farEnough(World w,int x,int z,int d) {
        long d2=(long)d*d;
        for(Placement p:placements) if(p.world.equals(w.getName())) {
            long dx=(long)p.x-x,dz=(long)p.z-z;if(dx*dx+dz*dz<d2)return false;
        } return true;
    }
    void record(World w,int x,int y,int z,String type) {
        placements.add(new Placement(w.getName(),x,y,z,type)); savePlacements();
    }
    private void loadPlacements() {
        placementsFile=new File(getDataFolder(),"placements.yml");
        if(!placementsFile.exists()){getDataFolder().mkdirs();try{placementsFile.createNewFile();}catch(Exception ignored){}}
        data=org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(placementsFile);
        for(String k:data.getKeys(false)) {
            String world=data.getString(k+".world",""),type=data.getString(k+".type","");
            if(!world.isBlank()&&!type.isBlank()) placements.add(new Placement(world,data.getInt(k+".x"),data.getInt(k+".y"),data.getInt(k+".z"),type));
        }
    }
    private void savePlacements() {
        if(data==null||placementsFile==null)return;
        data=new org.bukkit.configuration.file.YamlConfiguration();
        for(int i=0;i<placements.size();i++){Placement p=placements.get(i);String k="p"+i;
            data.set(k+".world",p.world);data.set(k+".x",p.x);data.set(k+".y",p.y);data.set(k+".z",p.z);data.set(k+".type",p.type);}
        try{data.save(placementsFile);}catch(Exception ex){getLogger().warning("Could not save placements: "+ex.getMessage());}
    }
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a) {
        if (c.getName().equalsIgnoreCase("locate")) {
            if (a.length > 0 && a[0].equalsIgnoreCase("structure")) {
                locateStructure(s, Arrays.copyOfRange(a, 1, a.length));
            } else {
                s.sendMessage(ChatColor.YELLOW + "Uzycie: /locate structure [typ]");
            }
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("locate")) {
            if (a.length > 1 && a[1].equalsIgnoreCase("structure")) {
                locateStructure(s, Arrays.copyOfRange(a, 2, a.length));
            } else {
                locateStructure(s, Arrays.copyOfRange(a, 1, a.length));
            }
            return true;
        }
        if(!s.hasPermission("elaeste.admin")){s.sendMessage(ChatColor.RED+"Brak uprawnien.");return true;}
        if(a.length==0||a[0].equalsIgnoreCase("status")) {
            s.sendMessage(ChatColor.GOLD+"Elaeste Structures 2.0");
            s.sendMessage(ChatColor.YELLOW+"Zapisane struktury: "+placements.size()+" | Elaeste: "+countInside()+" | Poza: "+(placements.size()-countInside()));
            s.sendMessage(ChatColor.GRAY+"Nowy zestaw: 10 typow. Elytra nie wystepuje w skrzyniach."); return true;
        }
        if(a[0].equalsIgnoreCase("reload")){reloadConfig();s.sendMessage(ChatColor.GREEN+"Config przeladowany.");return true;}
        if(a[0].equalsIgnoreCase("generate")) {
            if(!(s instanceof Player p)){s.sendMessage("Tej komendy uzyj w grze.");return true;}
            Location l=p.getLocation();String t=a.length>1?normalise(a[1]):randomType(new Random());
            if(t==null){p.sendMessage(ChatColor.RED+"Nieznany typ. Typy: "+String.join(", ",TYPES));return true;}
            if(builder.tryBuild(l.getWorld(),l.getBlockX(),l.getBlockZ(),t,new Random()))p.sendMessage(ChatColor.GREEN+"Wygenerowano "+t);
            else p.sendMessage(ChatColor.RED+"Nie udalo sie: teren jest zbyt nierowny lub nie ma miejsca.");
            return true;
        }
        if(a[0].equalsIgnoreCase("locate")) {
            if(!(s instanceof Player p)){s.sendMessage("Tej komendy uzyj w grze.");return true;}
            String wanted=a.length>1?normalise(a[1]):null; Placement best=null;double bestD=Double.MAX_VALUE;
            for(Placement pl:placements) if(pl.world.equals(p.getWorld().getName())&&(wanted==null||pl.type.equals(wanted))) {
                double d=p.getLocation().distanceSquared(new Location(p.getWorld(),pl.x,pl.y,pl.z));
                if(d<bestD){bestD=d;best=pl;}
            }
            if(best==null)p.sendMessage(ChatColor.RED+"Nie znaleziono zapisanej struktury tego typu.");
            else p.sendMessage(ChatColor.GREEN+"Najblizsza: "+best.type+" X:"+best.x+" Y:"+best.y+" Z:"+best.z+" ("+(int)Math.sqrt(bestD)+" blokow)");
            return true;
        }
        s.sendMessage(ChatColor.YELLOW+"Uzycie: /elaeste <status|reload|generate [typ]|locate structure [typ]>");
        return true;
    }
    private void locateStructure(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Tej komendy uzyj jako gracz.");
            return;
        }
        String wanted = null;
        if (args.length > 0) {
            wanted = normalise(args[0]);
            if (wanted == null) {
                player.sendMessage(ChatColor.RED + "Nieznany typ. Dostepne: " + String.join(", ", TYPES));
                return;
            }
        }
        Placement best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Placement placement : placements) {
            if (!placement.world.equals(player.getWorld().getName())) continue;
            if (wanted != null && !placement.type.equals(wanted)) continue;
            double distance = player.getLocation().distanceSquared(
                    new Location(player.getWorld(), placement.x, placement.y, placement.z));
            if (distance < bestDistance) {
                bestDistance = distance;
                best = placement;
            }
        }
        if (best == null) {
            player.sendMessage(ChatColor.RED + "Nie znaleziono zapisanej struktury"
                    + (wanted == null ? "." : " typu " + wanted + "."));
        } else {
            player.sendMessage(ChatColor.GREEN + "Najblizsza struktura: " + best.type
                    + " | X: " + best.x + " Y: " + best.y + " Z: " + best.z
                    + " | Odleglosc: " + (int) Math.sqrt(bestDistance) + " blokow");
        }
    }

    private String normalise(String in) {
        String q=in.toLowerCase(Locale.ROOT).replace('-','_');
        return TYPES.contains(q)?q:null;
    }
    private long countInside(){return placements.stream().filter(p->isInside(p.x,p.z)).count();}
    record Placement(String world,int x,int y,int z,String type) {}
}
