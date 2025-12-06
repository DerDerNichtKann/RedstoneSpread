package Dexter.redstoneSpread;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.RedstoneWire;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class RedstoneSpread extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("spread").setExecutor(new SpreadCommand());
        getCommand("spread").setTabCompleter(new SpreadTabCompleter());
    }

    @Override
    public void onDisable() {
    }

    private class SpreadTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
            List<String> suggestions = new ArrayList<>();

            if (args.length == 1) {
                suggestions.add("help");
                String input = args[0].toUpperCase();
                for (Material mat : Material.values()) {
                    if (mat.isBlock() && !mat.name().startsWith("LEGACY") && mat.name().startsWith(input)) {
                        suggestions.add(mat.name().toLowerCase());
                    }
                }
                return suggestions;
            }
            if (args.length == 2) {
                suggestions.add("100");
                suggestions.add("500");
                suggestions.add("1000");
                return suggestions;
            }
            if (args.length == 3) {
                suggestions.add("true");
                suggestions.add("false");
                return suggestions;
            }
            if (args.length == 4) {
                suggestions.add("true");
                suggestions.add("false");
                return suggestions;
            }
            if (args.length == 5) {
                suggestions.add("0.0");
                suggestions.add("0.1");
                suggestions.add("0.5");
                suggestions.add("0.8");
                return suggestions;
            }
            if (args.length == 6) {
                suggestions.add("true");
                suggestions.add("false");
                return suggestions;
            }
            return Collections.emptyList();
        }
    }

    private class SpreadCommand implements CommandExecutor {

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            if (args.length == 0 || (args.length > 0 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("?")))) {
                sendHelp(player);
                return true;
            }

            if (args.length < 2) {
                player.sendMessage("§cUsage: /spread <Mat> <Amt> [Phys] [Clean] [SplitChance] [Multi]");
                return false;
            }

            String matName = args[0].toUpperCase();
            Material material;
            if (matName.equals("REDSTONE")) {
                material = Material.REDSTONE_WIRE;
            } else {
                material = Material.matchMaterial(matName);
            }
            if (material == null || !material.isBlock()) {
                player.sendMessage("§cInvalid material: " + matName);
                return true;
            }

            int requestedAmount;
            try {
                requestedAmount = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                player.sendMessage("§cInvalid amount.");
                return true;
            }

            boolean applyPhysics = args.length < 3 || Boolean.parseBoolean(args[2]);

            boolean doCleanup = args.length > 3 && Boolean.parseBoolean(args[3]);

            double splitChance = 0.10;
            if (args.length > 4) {
                try {
                    splitChance = Double.parseDouble(args[4]);
                    if (splitChance < 0 || splitChance > 1) throw new NumberFormatException();
                } catch (NumberFormatException e) {
                    player.sendMessage("§cChance must be 0.0 - 1.0");
                    return true;
                }
            }

            boolean multiStart = args.length > 5 && Boolean.parseBoolean(args[5]);

            Block targetBlock = player.getTargetBlockExact(10);
            if (targetBlock == null) {
                player.sendMessage("§cYou are looking at nothing.");
                return true;
            }

            Block startBlock = targetBlock.getRelative(BlockFace.UP);

            int realPlaced = spreadBranchingLines(startBlock, material, requestedAmount, applyPhysics, doCleanup, splitChance, multiStart);

            if (realPlaced > 0) {
                player.sendMessage("§aSpread done: " + realPlaced + " blocks.");
                if (doCleanup) player.sendMessage("§7(Dots removed)");
            } else {
                player.sendMessage("§cCould not start. (Solid Block?)");
            }

            return true;
        }

        private void sendHelp(Player player) {
            player.sendMessage("§8§m--------------------------------");
            player.sendMessage("§6§lRedstoneSpread Help");
            player.sendMessage("§e/spread <Mat> <Amt> [Phys] [Clean] [Split%] [Multi]");
            player.sendMessage("");
            player.sendMessage("§71. §fMaterial§7: The block to place (e.g. redstone).");
            player.sendMessage("§72. §fAmount§7: How many blocks max.");
            player.sendMessage("§73. §fPhysics§7 (t/f): Connect neighbors/apply gravity?");
            player.sendMessage("§74. §fCleanup§7 (t/f): Remove single dots (for redstone)?");
            player.sendMessage("§75. §fSplitChance §7(0.0-1.0):");
            player.sendMessage("§7   §f0.0 §7= Snake (Single long line)");
            player.sendMessage("§7   §f0.5 §7= Branching (Tree-like)");
            player.sendMessage("§7   §f1.0 §7= Explosion (Dense cluster)");
            player.sendMessage("§76. §fMultiStart §7(t/f): Start in all 4 directions?");
            player.sendMessage("§8§m--------------------------------");
        }

        private int spreadBranchingLines(Block startBlock, Material material, int limit, boolean physics, boolean doCleanup, double splitChance, boolean multiStart) {
            List<Block> activeTips = new ArrayList<>();
            Set<Location> structureLocations = new HashSet<>();
            List<Block> allPlacedBlocks = new ArrayList<>();
            Random random = new Random();

            if (!isValidSpot(startBlock, structureLocations)) return 0;

            setBlock(startBlock, material, physics);
            structureLocations.add(startBlock.getLocation());
            allPlacedBlocks.add(startBlock);
            int placedCount = 1;

            if (multiStart) {
                List<Block> neighbors = getValidNeighbors(startBlock, structureLocations);
                for (Block n : neighbors) {
                    if (placedCount >= limit) break;
                    setBlock(n, material, physics);
                    structureLocations.add(n.getLocation());
                    allPlacedBlocks.add(n);
                    activeTips.add(n);
                    placedCount++;
                }
                if (activeTips.isEmpty()) activeTips.add(startBlock);
            } else {
                activeTips.add(startBlock);
            }

            while (placedCount < limit && !activeTips.isEmpty()) {
                int index = random.nextInt(activeTips.size());
                Block currentTip = activeTips.get(index);

                List<Block> validNeighbors = getValidNeighbors(currentTip, structureLocations);

                if (validNeighbors.isEmpty()) {
                    activeTips.remove(index);
                } else {
                    Block target = validNeighbors.get(random.nextInt(validNeighbors.size()));

                    setBlock(target, material, physics);
                    structureLocations.add(target.getLocation());
                    activeTips.add(target);
                    allPlacedBlocks.add(target);
                    placedCount++;

                    if (random.nextDouble() > splitChance) {
                        activeTips.remove(index);
                    }
                }
            }

            // Cleanup Logic
            if (doCleanup && material == Material.REDSTONE_WIRE) {
                for (Block b : allPlacedBlocks) {
                    if (b.getType() == Material.REDSTONE_WIRE) {
                        if (b.getBlockData() instanceof RedstoneWire) {
                            RedstoneWire wire = (RedstoneWire) b.getBlockData();
                            boolean north = wire.getFace(BlockFace.NORTH) == RedstoneWire.Connection.NONE;
                            boolean east = wire.getFace(BlockFace.EAST) == RedstoneWire.Connection.NONE;
                            boolean south = wire.getFace(BlockFace.SOUTH) == RedstoneWire.Connection.NONE;
                            boolean west = wire.getFace(BlockFace.WEST) == RedstoneWire.Connection.NONE;

                            if (north && east && south && west) {
                                b.setType(Material.AIR);
                            }
                        }
                    }
                }
            }

            return placedCount;
        }

        private void setBlock(Block block, Material mat, boolean physics) {
            block.setType(mat, physics);
        }

        private List<Block> getValidNeighbors(Block center, Set<Location> currentStructure) {
            List<Block> valid = new ArrayList<>();
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};

            for (BlockFace face : faces) {
                Block neighbor = center.getRelative(face);

                if (isValidSpot(neighbor, currentStructure) && !currentStructure.contains(neighbor.getLocation())) {
                    if (countTouchingNeighbors(neighbor, currentStructure) <= 1) {
                        valid.add(neighbor);
                    }
                }

                Block upTarget = neighbor.getRelative(BlockFace.UP);
                if (isValidSpot(upTarget, currentStructure) && !currentStructure.contains(upTarget.getLocation())) {
                    if (center.getRelative(BlockFace.UP).getType().isAir()) {
                        if (countTouchingNeighbors(upTarget, currentStructure) <= 1) {
                            valid.add(upTarget);
                        }
                    }
                }

                Block downTarget = neighbor.getRelative(BlockFace.DOWN);
                if (isValidSpot(downTarget, currentStructure) && !currentStructure.contains(downTarget.getLocation())) {
                    if (neighbor.getType().isAir()) {
                        if (countTouchingNeighbors(downTarget, currentStructure) <= 1) {
                            valid.add(downTarget);
                        }
                    }
                }
            }
            return valid;
        }

        private boolean isValidSpot(Block block, Set<Location> currentStructure) {
            if (!block.getType().isAir()) return false;
            Block ground = block.getRelative(BlockFace.DOWN);
            if (currentStructure.contains(ground.getLocation())) return false;
            Material groundType = ground.getType();
            if (!groundType.isSolid()) return false;
            if (!groundType.isOccluding()) return false;
            if (groundType == Material.DIRT_PATH || groundType == Material.FARMLAND ||
                    groundType == Material.SOUL_SAND || groundType == Material.SOUL_SOIL) return false;
            return true;
        }

        private int countTouchingNeighbors(Block target, Set<Location> structure) {
            int count = 0;
            BlockFace[] checkFaces = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
            for (BlockFace face : checkFaces) {
                Block n = target.getRelative(face);
                if (structure.contains(n.getLocation())) count++;
                else if (structure.contains(n.getRelative(BlockFace.UP).getLocation())) count++;
                else if (structure.contains(n.getRelative(BlockFace.DOWN).getLocation())) count++;
            }
            return count;
        }
    }
}