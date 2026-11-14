package pl.elaeste.structures;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

final class StructureBuilder {
    private final ElaesteStructures plugin;

    StructureBuilder(ElaesteStructures plugin) {
        this.plugin = plugin;
    }

    boolean tryBuild(World world, int x, int z, String type, Random random) {
        int radius = switch (type) {
            case "castle_large" -> 19;
            case "castle_medium" -> 14;
            case "settlement" -> 13;
            case "ruin" -> 10;
            case "camp" -> 8;
            default -> 7;
        };

        int baseY = world.getHighestBlockYAt(x, z);
        if (baseY < world.getMinHeight() + 5 || baseY > world.getMaxHeight() - 30) return false;
        if (!terrainSuitable(world, x, z, radius)) return false;

        switch (type) {
            case "castle_medium" -> buildCastle(world, x, baseY, z, 14, random);
            case "castle_large" -> buildCastle(world, x, baseY, z, 19, random);
            case "tower" -> buildTower(world, x, baseY, z, random);
            case "ruin" -> buildRuin(world, x, baseY, z, random);
            case "settlement" -> buildSettlement(world, x, baseY, z, random);
            case "camp" -> buildCamp(world, x, baseY, z, random);
            default -> { return false; }
        }
        plugin.recordPlacement(world, x, baseY, z, type);
        return true;
    }

    private boolean terrainSuitable(World world, int cx, int cz, int radius) {
        int centerY = world.getHighestBlockYAt(cx, cz);
        int maxDiff = plugin.getConfig().getInt("generation.max-height-difference", 5);
        boolean avoidWater = plugin.getConfig().getBoolean("generation.avoid-water", true);
        for (int dx = -radius; dx <= radius; dx += Math.max(2, radius / 4)) {
            for (int dz = -radius; dz <= radius; dz += Math.max(2, radius / 4)) {
                int y = world.getHighestBlockYAt(cx + dx, cz + dz);
                if (Math.abs(y - centerY) > maxDiff) return false;
                Material surface = world.getBlockAt(cx + dx, y - 1, cz + dz).getType();
                if (avoidWater && (surface == Material.WATER || surface == Material.LAVA)) return false;
            }
        }
        return true;
    }

    private void set(World w, int x, int y, int z, Material material) {
        w.getBlockAt(x, y, z).setType(material, false);
    }

    private void fill(World w, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                    set(w, x, y, z, material);
    }

    private void shell(World w, int cx, int y, int cz, int radius, int height, Material wall) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) == radius || Math.abs(z) == radius) {
                    for (int dy = 0; dy < height; dy++) set(w, cx + x, y + dy, cz + z, wall);
                }
            }
        }
    }

    private void buildCastle(World w, int x, int y, int z, int r, Random random) {
        Material wall = random.nextBoolean() ? Material.STONE_BRICKS : Material.ANDESITE;
        Material accent = random.nextBoolean() ? Material.MOSSY_STONE_BRICKS : Material.CRACKED_STONE_BRICKS;
        fill(w, x-r, y-1, z-r, x+r, y-1, z+r, Material.COBBLESTONE);
        shell(w, x, y, z, r, 8, wall);
        // Battlements and four corner towers
        for (int dx : new int[]{-r, r}) for (int dz : new int[]{-r, r}) {
            fill(w, x+dx-2, y, z+dz-2, x+dx+2, y+13, z+dz+2, wall);
            fill(w, x+dx-1, y+13, z+dz-1, x+dx+1, y+13, z+dz+1, Material.STONE_BRICK_SLAB);
            fill(w, x+dx-1, y+1, z+dz-1, x+dx+1, y+1, z+dz+1, Material.AIR);
        }
        // Gate and central keep
        fill(w, x-2, y, z-r, x+2, y+5, z-r, Material.AIR);
        fill(w, x-5, y, z-4, x+5, y+11, z+4, wall);
        fill(w, x-4, y+1, z-3, x+4, y+9, z+3, Material.AIR);
        fill(w, x-6, y+11, z-5, x+6, y+11, z+5, accent);
        // Floors, stairs, courtyard details
        fill(w, x-4, y+1, z-3, x+4, y+1, z+3, Material.SPRUCE_PLANKS);
        for (int i = -r+3; i <= r-3; i += 4) set(w, x+i, y+8, z, Material.STONE_BRICK_SLAB);
        set(w, x, y+1, z+7, Material.CAMPFIRE);
        chest(w, x+2, y+1, z+2, random, "castle");
        chest(w, x-2, y+1, z-2, random, "castle");
    }

    private void buildTower(World w, int x, int y, int z, Random random) {
        int r = 5 + random.nextInt(2);
        Material wall = random.nextBoolean() ? Material.STONE_BRICKS : Material.COBBLESTONE;
        fill(w, x-r, y-1, z-r, x+r, y-1, z+r, Material.COBBLESTONE);
        for (int dy = 0; dy < 20; dy++) {
            for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                if (Math.abs(dx) == r || Math.abs(dz) == r) set(w, x+dx, y+dy, z+dz, wall);
            }
        }
        fill(w, x-r+1, y+1, z-r+1, x+r-1, y+18, z+r-1, Material.AIR);
        for (int floor : new int[]{5, 10, 15}) {
            fill(w, x-r+1, y+floor, z-r+1, x+r-1, y+floor, z+r-1, Material.SPRUCE_PLANKS);
            set(w, x, y+floor+1, z, Material.LADDER);
        }
        fill(w, x-r-1, y+20, z-r-1, x+r+1, y+20, z+r+1, Material.STONE_BRICK_SLAB);
        chest(w, x, y+1, z, random, "tower");
        chest(w, x+2, y+11, z+2, random, "tower");
    }

    private void buildRuin(World w, int x, int y, int z, Random random) {
        Material[] blocks = {Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.COBBLESTONE};
        fill(w, x-9, y-1, z-7, x+9, y-1, z+7, Material.MOSSY_COBBLESTONE);
        for (int i = 0; i < 5; i++) {
            int dx = random.nextInt(17)-8;
            int dz = random.nextInt(13)-6;
            int h = 2 + random.nextInt(7);
            for (int dy = 0; dy < h; dy++) {
                set(w, x+dx, y+dy, z+dz, blocks[random.nextInt(blocks.length)]);
                if (random.nextBoolean()) set(w, x+dx+1, y+dy, z+dz, blocks[random.nextInt(blocks.length)]);
            }
        }
        for (int i = -7; i <= 7; i++) {
            if (random.nextBoolean()) set(w, x+i, y+4+random.nextInt(3), z-5, Material.STONE_BRICK_SLAB);
        }
        set(w, x, y, z, Material.MOSSY_COBBLESTONE);
        chest(w, x+2, y, z+2, random, "ruin");
    }

    private void buildSettlement(World w, int x, int y, int z, Random random) {
        fill(w, x-12, y-1, z-12, x+12, y-1, z+12, Material.DIRT_PATH);
        int[][] offsets = {{-7,-7},{7,-7},{-7,7},{7,7}};
        for (int[] o : offsets) buildHouse(w, x+o[0], y, z+o[1], random);
        fill(w, x-1, y, z-1, x+1, y+2, z+1, Material.COBBLESTONE);
        set(w, x, y+3, z, Material.WATER);
        set(w, x+3, y, z, Material.CAMPFIRE);
        chest(w, x-2, y, z, random, "settlement");
    }

    private void buildHouse(World w, int x, int y, int z, Random random) {
        Material plank = random.nextBoolean() ? Material.SPRUCE_PLANKS : Material.OAK_PLANKS;
        fill(w, x-3, y-1, z-3, x+3, y-1, z+3, Material.COBBLESTONE);
        fill(w, x-3, y, z-3, x+3, y+4, z+3, plank);
        fill(w, x-2, y+1, z-2, x+2, y+3, z+2, Material.AIR);
        fill(w, x-4, y+4, z-4, x+4, y+4, z+4, Material.SPRUCE_STAIRS);
        fill(w, x-2, y+1, z-3, x+2, y+2, z-3, Material.AIR);
        set(w, x, y, z+3, Material.SPRUCE_DOOR);
        chest(w, x+1, y+1, z+1, random, "settlement");
    }

    private void buildCamp(World w, int x, int y, int z, Random random) {
        fill(w, x-7, y-1, z-7, x+7, y-1, z+7, Material.COARSE_DIRT);
        for (int i = 0; i < 3; i++) {
            int tx = x + (i-1)*5;
            set(w, tx-2, y, z-2, Material.SPRUCE_FENCE);
            set(w, tx+2, y, z-2, Material.SPRUCE_FENCE);
            fill(w, tx-2, y+1, z-2, tx+2, y+3, z+2, Material.RED_WOOL);
            fill(w, tx-1, y+1, z-1, tx+1, y+2, z+1, Material.AIR);
            set(w, tx, y, z, Material.CAMPFIRE);
        }
        set(w, x, y, z+5, Material.CAMPFIRE);
        chest(w, x+4, y, z+4, random, "camp");
        set(w, x-4, y, z+4, Material.BARREL);
    }

    private void chest(World w, int x, int y, int z, Random random, String category) {
        Block block = w.getBlockAt(x, y, z);
        block.setType(Material.CHEST, false);
        if (!(block.getState() instanceof Chest chest)) return;
        Inventory inv = chest.getBlockInventory();
        List<ItemStack> pool = lootPool(category);
        int slots = 2 + random.nextInt(4);
        for (int i = 0; i < slots; i++) {
            ItemStack item = pool.get(random.nextInt(pool.size())).clone();
            if (item.getMaxStackSize() > 1) item.setAmount(1 + random.nextInt(Math.min(8, item.getMaxStackSize())));
            inv.setItem(random.nextInt(inv.getSize()), item);
        }
        chest.update(true, false);
    }

    private List<ItemStack> lootPool(String category) {
        List<ItemStack> items = new ArrayList<>();
        items.add(new ItemStack(Material.BREAD, 3));
        items.add(new ItemStack(Material.IRON_INGOT, 4));
        items.add(new ItemStack(Material.GOLD_INGOT, 2));
        items.add(new ItemStack(Material.ARROW, 8));
        items.add(new ItemStack(Material.BOW));
        items.add(new ItemStack(Material.COOKED_BEEF, 4));
        items.add(new ItemStack(Material.ENCHANTED_BOOK));
        items.add(new ItemStack(Material.DIAMOND));
        items.add(new ItemStack(Material.GOLDEN_APPLE));
        items.add(new ItemStack(Material.POTION));
        items.add(new ItemStack(Material.IRON_PICKAXE));
        items.add(new ItemStack(Material.GOLD_NUGGET, 6));
        if (category.equals("castle")) {
            items.add(new ItemStack(Material.DIAMOND_CHESTPLATE));
            items.add(new ItemStack(Material.DIAMOND));
            items.add(new ItemStack(Material.EMERALD, 3));
        } else if (category.equals("tower")) {
            items.add(new ItemStack(Material.CROSSBOW));
            items.add(new ItemStack(Material.ARROW, 16));
        } else if (category.equals("ruin")) {
            items.add(new ItemStack(Material.GOLD_INGOT, 5));
            items.add(new ItemStack(Material.ENCHANTED_BOOK));
        } else if (category.equals("camp")) {
            items.add(new ItemStack(Material.IRON_SWORD));
            items.add(new ItemStack(Material.COOKED_BEEF, 6));
        }
        return items;
    }
}
