package pl.elaeste.structures;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import java.util.*;

final class StructureBuilder {
    private final ElaesteStructures plugin;
    StructureBuilder(ElaesteStructures plugin){this.plugin=plugin;}

    boolean tryBuild(World w,int x,int z,String type,Random r) {
        int radius=switch(type){
            case "castle","magical_keep","dragon_fortress","sky_kingdom","elven_city","dwarven_mine"->30;
            case "mushroom_loft","cursed_manor","desert_temple"->24;default->18;};
        int y=w.getHighestBlockYAt(x,z);
        if(y<w.getMinHeight()+8||y>w.getMaxHeight()-65||!terrain(w,x,z,radius))return false;
        switch(type){
            case "castle"->castle(w,x,y,z,r);
            case "mushroom_loft"->mushroom(w,x,y,z,r);
            case "fantasy_tower"->tower(w,x,y,z,r);
            case "magical_keep"->keep(w,x,y,z,r);
            case "dragon_fortress"->dragon(w,x,y,z,r);
            case "sky_kingdom"->sky(w,x,y,z,r);
            case "elven_city"->elven(w,x,y,z,r);
            case "cursed_manor"->manor(w,x,y,z,r);
            case "dwarven_mine"->dwarven(w,x,y,z,r);
            case "desert_temple"->temple(w,x,y,z,r);
            default->{return false;}
        }
        plugin.record(w,x,y,z,type);return true;
    }
    private boolean terrain(World w,int x,int z,int rad){
        int cy=w.getHighestBlockYAt(x,z),diff=plugin.getConfig().getInt("generation.max-height-difference",7);
        for(int dx=-rad;dx<=rad;dx+=Math.max(3,rad/5))for(int dz=-rad;dz<=rad;dz+=Math.max(3,rad/5)){
            int yy=w.getHighestBlockYAt(x+dx,z+dz);
            if(Math.abs(yy-cy)>diff)return false;
            Material m=w.getBlockAt(x+dx,yy-1,z+dz).getType();
            if(plugin.getConfig().getBoolean("generation.avoid-water",true)&&(m==Material.WATER||m==Material.LAVA))return false;
        }return true;
    }
    private void b(World w,int x,int y,int z,Material m){w.getBlockAt(x,y,z).setType(m,false);}
    private void box(World w,int x1,int y1,int z1,int x2,int y2,int z2,Material m){
        for(int x=Math.min(x1,x2);x<=Math.max(x1,x2);x++)for(int y=Math.min(y1,y2);y<=Math.max(y1,y2);y++)for(int z=Math.min(z1,z2);z<=Math.max(z1,z2);z++)b(w,x,y,z,m);
    }
    private void shell(World w,int x,int y,int z,int rx,int rz,int h,Material m){
        for(int dx=-rx;dx<=rx;dx++)for(int dz=-rz;dz<=rz;dz++)if(Math.abs(dx)==rx||Math.abs(dz)==rz)
            for(int dy=0;dy<h;dy++)b(w,x+dx,y+dy,z+dz,m);
    }
    private void room(World w,int x,int y,int z,int rx,int rz,int h,Material wall,Material floor){
        box(w,x-rx,y-1,z-rz,x+rx,y-1,z+rz,floor);shell(w,x,y,z,rx,rz,h,wall);
        box(w,x-rx+1,y,z-rz+1,x+rx-1,y+h-1,z+rz-1,Material.AIR);
        for(int dx=-rx+2;dx<rx;dx+=4){b(w,x+dx,y+h,z-rz,Material.CUT_COPPER);b(w,x+dx,y+h,z+rz,Material.CUT_COPPER);}
        b(w,x,y+1,z,Material.LANTERN);
    }
    private void roofSpire(World w,int x,int y,int z,int r,int h,Material m){
        for(int i=0;i<h;i++){int rr=Math.max(0,r-(i*r/Math.max(1,h)));for(int dx=-rr;dx<=rr;dx++)for(int dz=-rr;dz<=rr;dz++)
            if(Math.abs(dx)==rr||Math.abs(dz)==rr)b(w,x+dx,y+i,z+dz,m);}
        b(w,x,y+h,z,Material.END_ROD);
    }
    private void stairs(World w,int x,int y,int z,int count,Material m,boolean alongX){
        for(int i=0;i<count;i++)b(w,x+(alongX?i:0),y+i,z+(alongX?0:i),m);
    }
    private void windows(World w,int x,int y,int z,int r,int h,Material glass){
        for(int yy=2;yy<h-1;yy+=3){b(w,x-r, y+yy,z,glass);b(w,x+r,y+yy,z,glass);b(w,x,y+yy,z-r,glass);b(w,x,y+yy,z+r,glass);}
    }
    private void chest(World w,int x,int y,int z,Random r,String tier){
        Block block=w.getBlockAt(x,y,z);block.setType(Material.CHEST,false);
        if(!(block.getState() instanceof Chest c))return;
        Inventory inv=c.getBlockInventory();
        int min=plugin.getConfig().getInt("loot."+tier+".min-items",7),max=plugin.getConfig().getInt("loot."+tier+".max-items",12);
        int count=min+r.nextInt(Math.max(1,max-min+1));
        List<ItemStack> pool=loot(tier,r);
        Set<Integer> used=new HashSet<>();
        for(int i=0;i<count;i++){
            int slot;do{slot=r.nextInt(inv.getSize());}while(!used.add(slot));
            inv.setItem(slot,pool.get(r.nextInt(pool.size())).clone());
        }
        c.update(true,false);
    }
    private List<ItemStack> loot(String tier,Random r){
        List<ItemStack> p=new ArrayList<>();
        add(p,Material.BREAD,3,8);add(p,Material.COOKED_BEEF,3,8);add(p,Material.IRON_INGOT,2,8);
        add(p,Material.GOLD_INGOT,2,6);add(p,Material.EMERALD,1,5);add(p,Material.ARROW,8,24);
        add(p,Material.ENDER_PEARL,1,3);add(p,Material.GOLDEN_APPLE,1,2);add(p,Material.BOW,1,1);
        add(p,Material.CROSSBOW,1,1);add(p,Material.SHIELD,1,1);add(p,Material.IRON_PICKAXE,1,1);
        add(p,Material.DIAMOND,1,4);add(p,Material.EXPERIENCE_BOTTLE,3,8);
        p.add(potion(PotionType.STRENGTH));
        p.add(potion(PotionType.REGENERATION));
        p.add(potion(PotionType.FIRE_RESISTANCE));
        p.add(enchantBook(Enchantment.PROTECTION,3+r.nextInt(2)));
        p.add(enchantBook(Enchantment.UNBREAKING,2+r.nextInt(2)));
        p.add(enchantBook(Enchantment.MENDING,1));
        if(!tier.equals("common")){
            add(p,Material.DIAMOND_SWORD,1,1);add(p,Material.DIAMOND_PICKAXE,1,1);add(p,Material.DIAMOND_AXE,1,1);
            p.add(enchantedGear(Material.DIAMOND_CHESTPLATE,Enchantment.PROTECTION,4));
            p.add(enchantedGear(Material.DIAMOND_HELMET,Enchantment.RESPIRATION,3));
            p.add(enchantedGear(Material.DIAMOND_LEGGINGS,Enchantment.UNBREAKING,3));
            p.add(enchantedGear(Material.DIAMOND_BOOTS,Enchantment.FEATHER_FALLING,4));
            add(p,Material.ENCHANTED_GOLDEN_APPLE,1,1);add(p,Material.TOTEM_OF_UNDYING,1,1);
            add(p,Material.TRIDENT,1,1);add(p,Material.SHULKER_SHELL,1,2);
        }
        if(tier.equals("legendary")){
            add(p,Material.ANCIENT_DEBRIS,1,3);add(p,Material.NETHERITE_INGOT,1,2);
            add(p,Material.NETHERITE_SCRAP,1,3);add(p,Material.NETHERITE_SWORD,1,1);
            add(p,Material.NETHERITE_PICKAXE,1,1);add(p,Material.NETHERITE_AXE,1,1);
            p.add(enchantedGear(Material.NETHERITE_CHESTPLATE,Enchantment.PROTECTION,4));
            p.add(enchantedGear(Material.NETHERITE_HELMET,Enchantment.AQUA_AFFINITY,1));
            p.add(enchantedGear(Material.NETHERITE_LEGGINGS,Enchantment.UNBREAKING,3));
            p.add(enchantedGear(Material.NETHERITE_BOOTS,Enchantment.DEPTH_STRIDER,3));
        }
        return p;
    }
    private void add(List<ItemStack> p,Material m,int min,int max){ItemStack it=new ItemStack(m);if(it.getMaxStackSize()>1)it.setAmount(min+new Random().nextInt(max-min+1));p.add(it);}
    private ItemStack potion(PotionType type) {
        ItemStack item=new ItemStack(Material.POTION);
        PotionMeta meta=(PotionMeta)item.getItemMeta();
        meta.setBasePotionType(type);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack enchantedGear(Material material,Enchantment enchantment,int level) {
        ItemStack item=new ItemStack(material);
        ItemMeta meta=item.getItemMeta();
        meta.addEnchant(enchantment,level,true);
        meta.addEnchant(Enchantment.UNBREAKING,3,true);
        item.setItemMeta(meta);
        return item;
    }
    private ItemStack enchantBook(Enchantment e,int level){
        ItemStack it=new ItemStack(Material.ENCHANTED_BOOK);EnchantmentStorageMeta meta=(EnchantmentStorageMeta)it.getItemMeta();
        meta.addStoredEnchant(e,level,true);it.setItemMeta(meta);return it;
    }
    private void chestSet(World w,int x,int y,int z,Random r){chest(w,x,y,z,r,"common");}
    private void chestVault(World w,int x,int y,int z,Random r){chest(w,x,y,z,r,"legendary");}
    private void chestHidden(World w,int x,int y,int z,Random r){chest(w,x,y,z,r,"rare");}

    private void castle(World w,int x,int y,int z,Random r){
        Material s=Material.STONE_BRICKS;box(w,x-25,y-1,z-22,x+25,y-1,z+22,Material.POLISHED_ANDESITE);
        room(w,x,y,z,9,8,17,s,Material.DEEPSLATE_BRICKS);box(w,x-7,y+1,z-6,x+7,y+1,z+6,Material.SPRUCE_PLANKS);
        box(w,x-6,y+8,z-5,x+6,y+8,z+5,Material.DARK_OAK_PLANKS);
        for(int dx:new int[]{-21,21})for(int dz:new int[]{-18,18}){room(w,x+dx,y,z+dz,4,4,13,Material.MOSSY_STONE_BRICKS,Material.CRACKED_STONE_BRICKS);roofSpire(w,x+dx,y+13,z+dz,4,8,Material.DEEPSLATE_TILES);}
        shell(w,x,y,z,24,21,7,Material.CRACKED_STONE_BRICKS);box(w,x-2,y,z-22,x+2,y+5,z-22,Material.AIR);
        for(int i=-20;i<=20;i+=4){b(w,x+i,y+7,z-21,Material.STONE_BRICK_WALL);b(w,x+i,y+7,z+21,Material.STONE_BRICK_WALL);}
        box(w,x-4,y+1,z-2,x+4,y+1,z+2,Material.RED_CARPET);b(w,x,y+2,z+5,Material.CHEST);
        stairs(w,x-3,y+1,z+8,7,Material.STONE_BRICK_STAIRS,true);
        chestSet(w,x-5,y+1,z+4,r);chestSet(w,x+5,y+1,z+4,r);chestHidden(w,x+6,y+2,z-6,r);chestVault(w,x,y+2,z+1,r);
        windows(w,x,y,z,8,17,Material.YELLOW_STAINED_GLASS);b(w,x,y+18,z,Material.LIGHTNING_ROD);
        for(int i=0;i<8;i++){b(w,x-12+i*3,y+1,z+13,Material.FERN);b(w,x+12-i*3,y+1,z-13,Material.MOSS_CARPET);}
    }
    private void mushroom(World w,int x,int y,int z,Random r){
        box(w,x-18,y-1,z-18,x+18,y-1,z+18,Material.MOSS_BLOCK);
        for(int dy=0;dy<19;dy++)for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)b(w,x+dx,y+dy,z+dz,Material.MUSHROOM_STEM);
        for(int ring=0;ring<4;ring++){int rad=5+ring*3;for(int dx=-rad;dx<=rad;dx++)for(int dz=-rad;dz<=rad;dz++)if(dx*dx+dz*dz<=rad*rad&&dx*dx+dz*dz>=(rad-2)*(rad-2))b(w,x+dx,y+15+ring,z+dz,Material.RED_MUSHROOM_BLOCK);}
        room(w,x,y+3,z,8,7,6,Material.MANGROVE_PLANKS,Material.MOSS_BLOCK);
        room(w,x,y+10,z,6,5,5,Material.SPRUCE_PLANKS,Material.OAK_PLANKS);
        for(int a=0;a<8;a++){double q=a*Math.PI/4;int bx=x+(int)(Math.cos(q)*12),bz=z+(int)(Math.sin(q)*12);
            b(w,bx,y+5,bz,Material.OAK_FENCE);b(w,bx,y+6,bz,Material.LANTERN);b(w,bx,y+4,bz,Material.SPRUCE_SLAB);}
        stairs(w,x-8,y+1,z+10,9,Material.MANGROVE_STAIRS,true);
        chestSet(w,x-5,y+4,z-4,r);chestSet(w,x+5,y+4,z+4,r);chestHidden(w,x+2,y+11,z-2,r);chestVault(w,x,y+11,z,r);
        for(int i=0;i<8;i++)b(w,x-15+i*4,y+1,z+15,Material.FLOWERING_AZALEA_LEAVES);
    }
    private void tower(World w,int x,int y,int z,Random r){
        box(w,x-13,y-1,z-13,x+13,y-1,z+13,Material.DEEPSLATE);
        for(int level=0;level<5;level++){int yy=y+level*8,rad=6-level/2;
            room(w,x,yy,z,rad,rad,8,Material.POLISHED_DEEPSLATE,Material.SPRUCE_PLANKS);
            for(int dx:new int[]{-rad,rad})for(int dz:new int[]{-rad,rad})b(w,x+dx,yy+8,z+dz,Material.PURPUR_PILLAR);
            windows(w,x,yy,z,rad,8,Material.MAGENTA_STAINED_GLASS);
        }
        roofSpire(w,x,y+40,z,7,13,Material.PURPUR_BLOCK);
        box(w,x-10,y+1,z-10,x+10,y+1,z+10,Material.CRYING_OBSIDIAN);
        chestSet(w,x-3,y+2,z+2,r);chestHidden(w,x+3,y+18,z-2,r);chestVault(w,x,y+41,z,r);
        for(int i=0;i<12;i++){int a=i*30;double q=Math.toRadians(a);b(w,x+(int)(Math.cos(q)*10),y+2,z+(int)(Math.sin(q)*10),Material.AMETHYST_CLUSTER);}
    }
    private void keep(World w,int x,int y,int z,Random r){
        box(w,x-25,y-1,z-22,x+25,y-1,z+22,Material.POLISHED_BLACKSTONE);
        for(int i=0;i<5;i++){int ox=(i-2)*9,oz=(i%2==0?-10:10);room(w,x+ox,y,z+oz,4,4,13,Material.DEEPSLATE_BRICKS,Material.DARK_OAK_PLANKS);roofSpire(w,x+ox,y+13,z+oz,4,9,i%2==0?Material.PURPLE_CONCRETE:Material.PINK_CONCRETE);}
        room(w,x,y,z,10,8,10,Material.POLISHED_BLACKSTONE_BRICKS,Material.PURPUR_BLOCK);
        for(int i=-18;i<=18;i+=6){b(w,x+i,y+1,z,Material.AMETHYST_BLOCK);b(w,x+i,y+2,z,Material.END_ROD);}
        box(w,x-3,y+1,z-5,x+3,y+1,z+5,Material.PURPLE_CARPET);
        chestSet(w,x-7,y+1,z+5,r);chestHidden(w,x+14,y+3,z-10,r);chestVault(w,x,y+1,z,r);
    }
    private void dragon(World w,int x,int y,int z,Random r){
        box(w,x-26,y-1,z-20,x+26,y-1,z+20,Material.BLACKSTONE);
        // Long ribbed dragon silhouette framing a compact fortress core.
        for(int i=-20;i<=20;i+=5){int h=9+r.nextInt(5);for(int yy=0;yy<h;yy++){b(w,x+i,y+yy,z-12,Material.BONE_BLOCK);b(w,x+i,y+yy,z+12,Material.BONE_BLOCK);}b(w,x+i,y+h,z,Material.BLACKSTONE_WALL);}
        room(w,x,y,z,10,8,12,Material.POLISHED_BLACKSTONE_BRICKS,Material.NETHER_BRICKS);
        roofSpire(w,x-7,y+12,z-5,4,8,Material.RED_NETHER_BRICKS);roofSpire(w,x+7,y+12,z+5,4,8,Material.RED_NETHER_BRICKS);
        for(int i=-8;i<=8;i+=4){b(w,x+i,y+1,z-9,Material.MAGMA_BLOCK);b(w,x+i,y+1,z+9,Material.MAGMA_BLOCK);}
        chestSet(w,x-5,y+1,z+4,r);chestHidden(w,x+7,y+2,z-5,r);chestVault(w,x,y+2,z,r);
        b(w,x,y+13,z,Material.DRAGON_HEAD);b(w,x,y+14,z,Material.SOUL_LANTERN);
    }
    private void sky(World w,int x,int y,int z,Random r){
        // Ground anchor plus three elevated terraces connected by stair bridges.
        for(int i=0;i<3;i++){int ox=(i-1)*17, yy=y+12+(i%2)*8;
            box(w,x+ox-7,yy,z-7,x+ox+7,yy,z+7,Material.MOSS_BLOCK);
            room(w,x+ox,yy+1,z,5,5,8,Material.QUARTZ_BRICKS,Material.POLISHED_DIORITE);
            roofSpire(w,x+ox,yy+9,z,5,8,Material.AMETHYST_BLOCK);
            if(i>0)stairs(w,x+ox-10,yy-1,z,10,Material.QUARTZ_STAIRS,true);
        }
        box(w,x-8,y-1,z-8,x+8,y-1,z+8,Material.CALCITE);
        room(w,x,y,z,6,6,7,Material.QUARTZ_PILLAR,Material.SMOOTH_QUARTZ);
        chestSet(w,x,y+2,z,r);chestHidden(w,x-17,y+14,z,r);chestVault(w,x+17,y+22,z,r);
        for(int dx=-6;dx<=6;dx+=3)for(int dz=-6;dz<=6;dz+=3)b(w,x+dx,y+10,z+dz,Material.CHAIN);
    }
    private void elven(World w,int x,int y,int z,Random r){
        box(w,x-24,y-1,z-24,x+24,y-1,z+24,Material.GRASS_BLOCK);
        for(int i=0;i<7;i++){double a=i*Math.PI*2/7;int tx=x+(int)(Math.cos(a)*15),tz=z+(int)(Math.sin(a)*15),h=20+r.nextInt(9);
            for(int yy=0;yy<h;yy++)b(w,tx,y+yy,tz,Material.DARK_OAK_LOG);
            for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)for(int dy=h-3;dy<=h+3;dy++)
                if(dx*dx+dz*dz+(dy-h)*(dy-h)<=36)b(w,tx+dx,y+dy,tz+dz,Material.AZALEA_LEAVES);
            room(w,tx,y+7,tz,4,3,5,Material.OAK_PLANKS,Material.BIRCH_PLANKS);
            chestSet(w,tx,y+8,tz,r);
        }
        room(w,x,y,z,9,7,9,Material.MANGROVE_ROOTS,Material.MOSS_BLOCK);
        for(int i=-12;i<=12;i+=4){b(w,x+i,y+5,z-10,Material.OAK_FENCE);b(w,x+i,y+5,z+10,Material.OAK_FENCE);}
        chestHidden(w,x+5,y+2,z-4,r);chestVault(w,x,y+2,z,r);
    }
    private void manor(World w,int x,int y,int z,Random r){
        box(w,x-20,y-1,z-16,x+20,y-1,z+16,Material.DEEPSLATE_TILES);
        room(w,x,y,z,11,8,14,Material.POLISHED_DEEPSLATE,Material.DARK_OAK_PLANKS);
        room(w,x,y+14,z,8,6,7,Material.DARK_OAK_PLANKS,Material.SPRUCE_PLANKS);
        for(int dx:new int[]{-15,15})for(int dz:new int[]{-11,11}){room(w,x+dx,y,z+dz,4,4,11,Material.DEEPSLATE_BRICKS,Material.DARK_OAK_PLANKS);roofSpire(w,x+dx,y+11,z+dz,4,8,Material.BLACKSTONE);}
        for(int i=-8;i<=8;i+=4){b(w,x+i,y+2,z-9,Material.SOUL_LANTERN);b(w,x+i,y+2,z+9,Material.SOUL_LANTERN);}
        chestSet(w,x-7,y+1,z+5,r);chestHidden(w,x+8,y+15,z-3,r);chestVault(w,x,y+1,z,r);
        box(w,x-3,y-7,z-3,x+3,y-7,z+3,Material.CRYING_OBSIDIAN);
        box(w,x-3,y-6,z-3,x+3,y-1,z+3,Material.AIR);chestVault(w,x,y-6,z,r);
    }
    private void dwarven(World w,int x,int y,int z,Random r){
        box(w,x-26,y-1,z-20,x+26,y-1,z+20,Material.TUFF_BRICKS);
        room(w,x,y,z,17,11,17,Material.DEEPSLATE_BRICKS,Material.POLISHED_ANDESITE);
        for(int dx:new int[]{-13,13})for(int dz:new int[]{-8,8})for(int yy=0;yy<15;yy+=3)b(w,x+dx,y+yy,z+dz,Material.IRON_BARS);
        for(int i=-14;i<=14;i+=4){b(w,x+i,y+1,z-5,Material.RAIL);b(w,x+i,y+1,z+5,Material.RAIL);}
        room(w,x-21,y,z,4,5,8,Material.COPPER_BLOCK,Material.CUT_COPPER);
        room(w,x+21,y,z,4,5,8,Material.COPPER_BLOCK,Material.CUT_COPPER);
        chestSet(w,x-18,y+1,z+2,r);chestHidden(w,x+18,y+1,z-2,r);chestVault(w,x,y+1,z,r);
        b(w,x,y+2,z,Material.BLAST_FURNACE);b(w,x-2,y+2,z,Material.ANVIL);b(w,x+2,y+2,z,Material.SMITHING_TABLE);
    }
    private void temple(World w,int x,int y,int z,Random r){
        box(w,x-21,y-1,z-21,x+21,y-1,z+21,Material.SANDSTONE);
        room(w,x,y,z,13,13,13,Material.CUT_SANDSTONE,Material.SMOOTH_SANDSTONE);
        for(int dx:new int[]{-17,17})for(int dz:new int[]{-17,17}){
            for(int yy=0;yy<18;yy++)b(w,x+dx,y+yy,z+dz,Material.CHISELED_SANDSTONE);
            roofSpire(w,x+dx,y+18,z+dz,3,7,Material.ORANGE_TERRACOTTA);
        }
        box(w,x-5,y+1,z-5,x+5,y+1,z+5,Material.ORANGE_TERRACOTTA);
        box(w,x-2,y-8,z-2,x+2,y-1,z+2,Material.AIR);
        chestSet(w,x-8,y+1,z+7,r);chestHidden(w,x+8,y+1,z-7,r);chestVault(w,x,y-7,z,r);
        for(int i=-12;i<=12;i+=4){b(w,x+i,y+1,z-14,Material.DEAD_BUSH);b(w,x+i,y+1,z+14,Material.DEAD_BUSH);}
    }
}
