package me.twostinkysocks.oldraids;

import org.bstats.bukkit.Metrics;
import org.bukkit.ChatColor;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Raid;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.raid.RaidSpawnWaveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;


public final class OldRaids extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final int DEFAULT_BAD_OMEN_DURATION_TICKS = 120000;
    private static final int MAX_BAD_OMEN_AMPLIFIER = 4;
    private static final int RAID_SPAWN_TRIES = 20;

    @Override
    public void onEnable() {
        load();
        if(getConfig().getBoolean("metrics")) {
            int pluginId = 22491;
            new Metrics(this, pluginId);
        }
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("oldraids").setExecutor(this);
        getCommand("oldraids").setTabCompleter(this);
    }
    
    public void load() {
        if(!this.getDataFolder().exists()) {
            this.getDataFolder().mkdir();
        }
        File config = new File(this.getDataFolder(), "config.yml");
        if(!config.exists()) {
            saveDefaultConfig();
        }
        this.reloadConfig();
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        Player damager = e.getEntity().getKiller();
        LivingEntity damaged = e.getEntity();

        if(damager == null) return;
        if(!(damaged instanceof Raider)) return;

        Raider raider = (Raider) damaged;

        if(raider.isPatrolLeader() && raider.getRaid() == null && damaged.getWorld().locateNearestRaid(damaged.getLocation(), 0) == null) {
            int i = 0;
            PotionEffect effect = damager.getPotionEffect(PotionEffectType.BAD_OMEN);
            if(effect != null) {
                i = effect.getAmplifier() + 1;
                damager.removePotionEffect(PotionEffectType.BAD_OMEN);
            }
            i = clamp(i, 0, MAX_BAD_OMEN_AMPLIFIER);
            int duration = getConfig().getInt("badOmenDurationTicks", DEFAULT_BAD_OMEN_DURATION_TICKS);
            PotionEffect newEffect = new PotionEffect(PotionEffectType.BAD_OMEN, duration, i, false, false, true);
            if(Boolean.FALSE.equals(damaged.getWorld().getGameRuleValue(GameRule.DISABLE_RAIDS))) {
                damager.addPotionEffect(newEffect);
            }

            if(!getConfig().getBoolean("raidersDropOminousBottles")) e.getDrops().removeIf(item -> item.getType() == Material.OMINOUS_BOTTLE);
        }
    }

    @EventHandler
    public void onPotion(EntityPotionEffectEvent e) {
        if(e.getEntity() instanceof Player && e.getNewEffect() != null && e.getNewEffect().getType() == PotionEffectType.RAID_OMEN && e.getNewEffect().getDuration() > 1) {
            e.setCancelled(true);
            ((Player) e.getEntity()).addPotionEffect(new PotionEffect(PotionEffectType.RAID_OMEN, 1, e.getNewEffect().getAmplifier(), false, true, true));
        }
    }

    @EventHandler
    public void onRaidSpawnWave(RaidSpawnWaveEvent e) {
        if(!getConfig().getBoolean("restoreOldRaidSpawnLocations", true)) return;

        Location spawn = findOldRaidSpawnLocation(e.getRaid(), e.getWorld());
        if(spawn == null) return;

        List<Raider> raiders = new ArrayList<>(e.getRaiders());
        if(e.getPatrolLeader() != null && !raiders.contains(e.getPatrolLeader())) {
            raiders.add(e.getPatrolLeader());
        }

        for(int index = 0; index < raiders.size(); index++) {
            Raider raider = raiders.get(index);
            if(raider == null || !raider.isValid() || raider.isDead()) continue;

            int xOffset = (index % 3) - 1;
            int zOffset = ((index / 3) % 3) - 1;
            Location target = findSurfaceSpawnLocation(spawn.getWorld(), spawn.getBlockX() + xOffset, spawn.getBlockZ() + zOffset);
            if(target == null) {
                target = spawn.clone().add(xOffset, 0, zOffset);
            }
            target.setYaw(raider.getLocation().getYaw());
            target.setPitch(raider.getLocation().getPitch());
            raider.teleport(target);
        }
    }

    private Location findOldRaidSpawnLocation(Raid raid, World world) {
        Location center = raid.getLocation();
        if(center == null) return null;

        for(int proximity = 0; proximity <= 3; proximity++) {
            Location spawn = findRandomRaidSpawnLocation(world, center, proximity, RAID_SPAWN_TRIES);
            if(spawn != null) return spawn;
        }
        return null;
    }

    private Location findRandomRaidSpawnLocation(World world, Location center, int proximity, int tries) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int invertedProximity = proximity == 0 ? 2 : 2 - proximity;

        for(int i = 0; i < tries; i++) {
            double angle = random.nextDouble(Math.PI * 2.0D);
            int x = center.getBlockX() + (int) Math.floor(Math.cos(angle) * 32.0D * invertedProximity) + random.nextInt(5);
            int z = center.getBlockZ() + (int) Math.floor(Math.sin(angle) * 32.0D * invertedProximity) + random.nextInt(5);

            Location target = findSurfaceSpawnLocation(world, x, z);
            if(target == null) continue;

            boolean outsideApproximateVillage = target.distanceSquared(center) > 32.0D * 32.0D;
            if((outsideApproximateVillage || proximity >= 2) && isRaidSpawnLocation(target)) {
                return target;
            }
        }

        return null;
    }

    private Location findSurfaceSpawnLocation(World world, int x, int z) {
        if(world == null || !world.isChunkLoaded(x >> 4, z >> 4)) return null;

        Block surface = world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
        Block feet = surface.isPassable() ? surface : surface.getRelative(BlockFace.UP);
        return feet.getLocation().add(0.5D, 0.0D, 0.5D);
    }

    private boolean isRaidSpawnLocation(Location location) {
        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);

        boolean hasRoom = feet.isPassable() && head.isPassable();
        boolean hasGround = ground.getType().isSolid() || ground.getType() == Material.SNOW;
        return hasRoom && hasGround;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(command.getName().equalsIgnoreCase("oldraids")) {
            if(!sender.hasPermission("oldraids.reload") && !(sender instanceof ConsoleCommandSender) && !sender.isOp()) {
                sender.sendMessage(ChatColor.RED + "You don't have permission to do that!");
                return true;
            }
            if(args.length == 0 || !args[0].equals("reload")) {
                sender.sendMessage(ChatColor.RED + "Usage: /oldraids reload");
                return true;
            }
            load();
            sender.sendMessage(ChatColor.GREEN + "Config reloaded!");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if(command.getName().equalsIgnoreCase("oldraids") && args.length == 1) {
            return List.of("reload");
        }
        return List.of();
    }
}
