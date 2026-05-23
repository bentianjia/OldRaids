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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;
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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;


public final class OldRaids extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private static final int DEFAULT_BAD_OMEN_DURATION_TICKS = 120000;
    private static final int MAX_BAD_OMEN_AMPLIFIER = 4;
    private static final int RAID_SPAWN_TRIES = 20;

    private boolean warnedNmsBridge;

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
        getServer().getScheduler().runTaskTimer(this, this::triggerBadOmenRaids, 1L, 1L);
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
                triggerBadOmenRaid(damager);
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
            if(target == null || !isOriginalRaidSpawnLocation(target, spawn, 2)) {
                target = spawn;
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

            if(isOriginalRaidSpawnLocation(target, center, proximity)) {
                return target;
            }
        }

        return null;
    }

    private Location findSurfaceSpawnLocation(World world, int x, int z) {
        if(world == null || !world.isChunkLoaded(x >> 4, z >> 4)) return null;

        Integer vanillaY = NmsBridge.getWorldSurfaceY(world, x, z);
        if(vanillaY != null) {
            return new Location(world, x + 0.5D, vanillaY, z + 0.5D);
        }

        Block surface = world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
        Block feet = surface.isPassable() && surface.getType() != Material.SNOW ? surface : surface.getRelative(BlockFace.UP);
        return feet.getLocation().add(0.5D, 0.0D, 0.5D);
    }

    private boolean isOriginalRaidSpawnLocation(Location location, Location raidCenter, int proximity) {
        try {
            return NmsBridge.isOriginalRaidSpawnLocation(location, proximity);
        } catch(ReflectiveOperationException ex) {
            warnNmsBridge(ex);
            return isFallbackRaidSpawnLocation(location, raidCenter, proximity);
        }
    }

    private boolean isFallbackRaidSpawnLocation(Location location, Location raidCenter, int proximity) {
        boolean outsideApproximateVillage = location.distanceSquared(raidCenter) > 32.0D * 32.0D;
        if(!outsideApproximateVillage && proximity < 2) return false;

        Block feet = location.getBlock();
        Block head = feet.getRelative(BlockFace.UP);
        Block ground = feet.getRelative(BlockFace.DOWN);

        boolean hasRoom = feet.isPassable() && head.isPassable();
        boolean hasFullGround = ground.getType().isOccluding() && !ground.isPassable();
        return hasRoom && (hasFullGround || isSnowSpawnException(location));
    }

    private boolean isSnowSpawnException(Location location) {
        Block feet = location.getBlock();
        Block ground = feet.getRelative(BlockFace.DOWN);
        return ground.getType() == Material.SNOW && feet.isEmpty();
    }

    private void triggerBadOmenRaids() {
        if(!getConfig().getBoolean("restoreBadOmenRaidTrigger", true)) return;

        for(Player player : getServer().getOnlinePlayers()) {
            triggerBadOmenRaid(player);
        }
    }

    private boolean triggerBadOmenRaid(Player player) {
        PotionEffect badOmen = player.getPotionEffect(PotionEffectType.BAD_OMEN);
        if(badOmen == null || Boolean.TRUE.equals(player.getWorld().getGameRuleValue(GameRule.DISABLE_RAIDS))) {
            return false;
        }

        try {
            if(!NmsBridge.isPlayerInVillage(player)) return false;

            player.removePotionEffect(PotionEffectType.RAID_OMEN);
            player.addPotionEffect(new PotionEffect(PotionEffectType.RAID_OMEN, 1, badOmen.getAmplifier(), false, false, true));
            Object raid = NmsBridge.createOrExtendRaid(player);
            player.removePotionEffect(PotionEffectType.RAID_OMEN);

            if(raid != null) {
                player.removePotionEffect(PotionEffectType.BAD_OMEN);
                return true;
            }
        } catch(ReflectiveOperationException ex) {
            warnNmsBridge(ex);
        }

        return false;
    }

    private void warnNmsBridge(Exception ex) {
        if(warnedNmsBridge) return;

        warnedNmsBridge = true;
        getLogger().log(Level.WARNING, "Could not access vanilla raid internals; falling back to API-only behavior.", ex);
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

    private static final class NmsBridge {
        private static final Class<?> BLOCK_POS_CLASS = findClass("net.minecraft.core.BlockPos");
        private static final Class<?> ENTITY_TYPE_CLASS = findClass("net.minecraft.world.entity.EntityType");
        private static final Class<?> LEVEL_READER_CLASS = findClass("net.minecraft.world.level.LevelReader");
        private static final Class<?> SERVER_PLAYER_CLASS = findClass("net.minecraft.server.level.ServerPlayer");
        private static final Class<?> HEIGHTMAP_TYPES_CLASS = findClass("net.minecraft.world.level.levelgen.Heightmap$Types");
        private static final Constructor<?> BLOCK_POS_CONSTRUCTOR = findConstructor(BLOCK_POS_CLASS);
        private static final Object RAVAGER = findStaticField(ENTITY_TYPE_CLASS, "RAVAGER");
        private static final Object WORLD_SURFACE = findStaticField(HEIGHTMAP_TYPES_CLASS, "WORLD_SURFACE");
        private static final Object RAVAGER_SPAWN_PLACEMENT = findRavagerSpawnPlacement();

        private static boolean isPlayerInVillage(Player player) throws ReflectiveOperationException {
            Object serverPlayer = getHandle(player);
            Object serverLevel = invoke(serverPlayer, "level");
            Object blockPos = invoke(serverPlayer, "blockPosition");
            return (boolean) invoke(serverLevel, "isVillage", new Class<?>[]{BLOCK_POS_CLASS}, blockPos);
        }

        private static Object createOrExtendRaid(Player player) throws ReflectiveOperationException {
            Object serverPlayer = getHandle(player);
            Object serverLevel = invoke(serverPlayer, "level");
            Object raids = invoke(serverLevel, "getRaids");
            Object blockPos = invoke(serverPlayer, "blockPosition");
            return invoke(raids, "createOrExtendRaid", new Class<?>[]{SERVER_PLAYER_CLASS, BLOCK_POS_CLASS}, serverPlayer, blockPos);
        }

        private static boolean isOriginalRaidSpawnLocation(Location location, int proximity) throws ReflectiveOperationException {
            if(isVillage(location) && proximity < 2) return false;
            if(!hasChunksAt(location, 10)) return false;
            if(!isPositionEntityTicking(location)) return false;
            return isRavagerSpawnPositionOk(location) || isSnowSpawnExceptionVanilla(location);
        }

        private static Integer getWorldSurfaceY(World world, int x, int z) {
            try {
                Object serverLevel = getHandle(world);
                return (Integer) invoke(serverLevel, "getHeight", new Class<?>[]{HEIGHTMAP_TYPES_CLASS, int.class, int.class}, WORLD_SURFACE, x, z);
            } catch(ReflectiveOperationException ex) {
                return null;
            }
        }

        private static boolean isVillage(Location location) throws ReflectiveOperationException {
            Object serverLevel = getHandle(location.getWorld());
            Object blockPos = blockPos(location);
            return (boolean) invoke(serverLevel, "isVillage", new Class<?>[]{BLOCK_POS_CLASS}, blockPos);
        }

        private static boolean hasChunksAt(Location location, int offset) throws ReflectiveOperationException {
            Object serverLevel = getHandle(location.getWorld());
            int x = location.getBlockX();
            int z = location.getBlockZ();
            return (boolean) invoke(serverLevel, "hasChunksAt", new Class<?>[]{int.class, int.class, int.class, int.class}, x - offset, z - offset, x + offset, z + offset);
        }

        private static boolean isPositionEntityTicking(Location location) throws ReflectiveOperationException {
            Object serverLevel = getHandle(location.getWorld());
            Object blockPos = blockPos(location);
            return (boolean) invoke(serverLevel, "isPositionEntityTicking", new Class<?>[]{BLOCK_POS_CLASS}, blockPos);
        }

        private static boolean isRavagerSpawnPositionOk(Location location) throws ReflectiveOperationException {
            if(RAVAGER_SPAWN_PLACEMENT == null || RAVAGER == null) {
                throw new ReflectiveOperationException("Ravager spawn placement is unavailable");
            }

            Object serverLevel = getHandle(location.getWorld());
            Object blockPos = blockPos(location);
            return (boolean) invoke(RAVAGER_SPAWN_PLACEMENT, "isSpawnPositionOk", new Class<?>[]{LEVEL_READER_CLASS, BLOCK_POS_CLASS, ENTITY_TYPE_CLASS}, serverLevel, blockPos, RAVAGER);
        }

        private static boolean isSnowSpawnExceptionVanilla(Location location) {
            Block feet = location.getBlock();
            Block ground = feet.getRelative(BlockFace.DOWN);
            return ground.getType() == Material.SNOW && feet.isEmpty();
        }

        private static Object blockPos(Location location) throws ReflectiveOperationException {
            return blockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }

        private static Object blockPos(int x, int y, int z) throws ReflectiveOperationException {
            if(BLOCK_POS_CONSTRUCTOR == null) {
                throw new ReflectiveOperationException("BlockPos constructor is unavailable");
            }
            return BLOCK_POS_CONSTRUCTOR.newInstance(x, y, z);
        }

        private static Object getHandle(Object object) throws ReflectiveOperationException {
            if(object == null) {
                throw new ReflectiveOperationException("Cannot get handle for null object");
            }
            return invoke(object, "getHandle");
        }

        private static Object invoke(Object target, String name, Object... args) throws ReflectiveOperationException {
            Class<?>[] parameterTypes = new Class<?>[args.length];
            for(int i = 0; i < args.length; i++) {
                parameterTypes[i] = args[i].getClass();
            }
            return invoke(target, name, parameterTypes, args);
        }

        private static Object invoke(Object target, String name, Class<?>[] parameterTypes, Object... args) throws ReflectiveOperationException {
            if(target == null) {
                throw new ReflectiveOperationException("Cannot invoke " + name + " on null target");
            }
            for(Class<?> parameterType : parameterTypes) {
                if(parameterType == null) {
                    throw new ReflectiveOperationException("Missing parameter type for " + name);
                }
            }
            Method method = target.getClass().getMethod(name, parameterTypes);
            return method.invoke(target, args);
        }

        private static Class<?> findClass(String name) {
            try {
                return Class.forName(name);
            } catch(ClassNotFoundException ex) {
                return null;
            }
        }

        private static Constructor<?> findConstructor(Class<?> type) {
            if(type == null) return null;
            try {
                return type.getConstructor(int.class, int.class, int.class);
            } catch(NoSuchMethodException ex) {
                return null;
            }
        }

        private static Object findStaticField(Class<?> type, String name) {
            if(type == null) return null;
            try {
                Field field = type.getField(name);
                return field.get(null);
            } catch(ReflectiveOperationException ex) {
                return null;
            }
        }

        private static Object findRavagerSpawnPlacement() {
            Class<?> spawnPlacementsClass = findClass("net.minecraft.world.entity.SpawnPlacements");
            if(spawnPlacementsClass == null || ENTITY_TYPE_CLASS == null || RAVAGER == null) return null;
            try {
                Method method = spawnPlacementsClass.getMethod("getPlacementType", ENTITY_TYPE_CLASS);
                return method.invoke(null, RAVAGER);
            } catch(ReflectiveOperationException ex) {
                return null;
            }
        }
    }
}
