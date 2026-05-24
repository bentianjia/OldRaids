package com.tendoarisu.oldraids;

import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Raid;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Raider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.raid.RaidSpawnWaveEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;


public final class OldRaids extends JavaPlugin implements Listener {
   private static final int DEFAULT_BAD_OMEN_DURATION_TICKS = 120000;
   private static final int MAX_BAD_OMEN_AMPLIFIER = 4;
   private static final int RAID_SPAWN_TRIES = 20;
   private static final String KEEP_OMINOUS_BOTTLE_CONFIG = "keep-ominous-bottle";
   private static final String ADMIN_PERMISSION = "oldraids.admin";

   private boolean warnedNmsBridge;
   private boolean keepOminousBottle;

   public void onEnable() {
      this.saveDefaultConfig();
      this.getConfig().options().copyDefaults(true);
      this.saveConfig();
      this.reloadSettings();
      this.getServer().getPluginManager().registerEvents(this, this);
      this.getServer().getScheduler().runTaskTimer(this, this::precalculateRaidSpawnPositions, 1L, 1L);
   }

   @Override
   public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
      if (!command.getName().equalsIgnoreCase("oldraids")) {
         return false;
      }
      if (!sender.hasPermission(ADMIN_PERMISSION)) {
         sender.sendMessage("[OldRaids] You do not have permission to use this command.");
         return true;
      }

      if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
         this.sendConfigStatus(sender);
         return true;
      }
      if (args[0].equalsIgnoreCase("reload")) {
         this.reloadConfig();
         this.reloadSettings();
         sender.sendMessage("[OldRaids] Config reloaded.");
         this.sendConfigStatus(sender);
         return true;
      }
      if (args[0].equalsIgnoreCase("set") && args.length == 3 && args[1].equalsIgnoreCase(KEEP_OMINOUS_BOTTLE_CONFIG)) {
         Boolean value = this.parseBoolean(args[2]);
         if (value == null) {
            sender.sendMessage("[OldRaids] Value must be true or false.");
            return true;
         }

         this.getConfig().set(KEEP_OMINOUS_BOTTLE_CONFIG, value);
         this.saveConfig();
         this.reloadSettings();
         sender.sendMessage("[OldRaids] Saved " + KEEP_OMINOUS_BOTTLE_CONFIG + "=" + value + ".");
         return true;
      }

      sender.sendMessage("[OldRaids] Usage: /oldraids <status|reload|set keep-ominous-bottle <true|false>>");
      return true;
   }

   @Override
   public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
      if (!command.getName().equalsIgnoreCase("oldraids") || !sender.hasPermission(ADMIN_PERMISSION)) {
         return List.of();
      }
      if (args.length == 1) {
         return this.matching(args[0], "status", "reload", "set");
      }
      if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
         return this.matching(args[1], KEEP_OMINOUS_BOTTLE_CONFIG);
      }
      if (args.length == 3 && args[0].equalsIgnoreCase("set") && args[1].equalsIgnoreCase(KEEP_OMINOUS_BOTTLE_CONFIG)) {
         return this.matching(args[2], "true", "false");
      }
      return List.of();
   }

   @EventHandler
   public void onDeath(EntityDeathEvent e) {
      Player damager = e.getEntity().getKiller();
      LivingEntity damaged = e.getEntity();
      if (damager != null) {
         if (damaged instanceof Raider raider) {
            if (raider.isPatrolLeader() && raider.getRaid() == null && damaged.getWorld().locateNearestRaid(damaged.getLocation(), 0) == null) {
               int i = 0;
               PotionEffect effect = damager.getPotionEffect(PotionEffectType.BAD_OMEN);
               if (effect != null) {
                  i = effect.getAmplifier() + 1;
                  damager.removePotionEffect(PotionEffectType.BAD_OMEN);
               }

               i = this.clamp(i, 0, MAX_BAD_OMEN_AMPLIFIER);
               PotionEffect newEffect = new PotionEffect(PotionEffectType.BAD_OMEN, DEFAULT_BAD_OMEN_DURATION_TICKS, i, false, false, true);
               if (Boolean.FALSE.equals(damaged.getWorld().getGameRuleValue(GameRule.DISABLE_RAIDS))) {
                  damager.addPotionEffect(newEffect);
                  this.getServer().getScheduler().runTask(this, () -> this.triggerRaidFromBadOmen(damager));
               }

               if (!this.keepOminousBottle) {
                  e.getDrops().removeIf(item -> item.getType() == Material.OMINOUS_BOTTLE);
               }
            }
         }
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onMove(PlayerMoveEvent e) {
      if (this.hasChangedBlock(e.getFrom(), e.getTo())) {
         this.triggerRaidFromBadOmen(e.getPlayer());
      }
   }

   @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
   public void onPotion(EntityPotionEffectEvent e) {
      if (e.getEntity() instanceof Player player
         && e.getNewEffect() != null
         && e.getNewEffect().getType() == PotionEffectType.RAID_OMEN
         && e.getNewEffect().getDuration() > 1) {
         this.getServer().getScheduler().runTask(this, () -> this.triggerRaidFromRaidOmen(player));
      }
   }

   @EventHandler
   public void onRaidSpawnWave(RaidSpawnWaveEvent e) {
      Location spawn = this.findOldRaidSpawnLocation(e.getRaid(), e.getWorld(), false);
      if (spawn == null) {
         return;
      }

      List<Raider> raiders = new ArrayList<>(e.getRaiders());
      if (e.getPatrolLeader() != null && !raiders.contains(e.getPatrolLeader())) {
         raiders.add(e.getPatrolLeader());
      }

      for (int index = 0; index < raiders.size(); index++) {
         Raider raider = raiders.get(index);
         if (raider != null && raider.isValid() && !raider.isDead()) {
            int xOffset = index % 3 - 1;
            int zOffset = index / 3 % 3 - 1;
            Location target = this.findSurfaceSpawnLocation(spawn.getWorld(), spawn.getBlockX() + xOffset, spawn.getBlockZ() + zOffset);
            if (target == null || !this.isOriginalRaidSpawnLocation(target, spawn, 2)) {
               target = spawn;
            }

            target.setYaw(raider.getLocation().getYaw());
            target.setPitch(raider.getLocation().getPitch());
            raider.teleport(target);
         }
      }
   }

   private void precalculateRaidSpawnPositions() {
      for (World world : this.getServer().getWorlds()) {
         for (Raid raid : world.getRaids()) {
            if (raid.getStatus() != Raid.RaidStatus.ONGOING || !raid.getRaiders().isEmpty() || raid.getSpawnedGroups() >= raid.getTotalGroups()) {
               continue;
            }

            Location spawn = this.findOldRaidSpawnLocation(raid, world, true);
            if (spawn == null) {
               continue;
            }

            try {
               NmsBridge.setWaveSpawnPosition(raid, spawn);
            } catch (ReflectiveOperationException ex) {
               this.warnNmsBridge(ex);
            }
         }
      }
   }

   private void triggerRaidFromBadOmen(Player player) {
      if (!player.isOnline() || player.isDead()) {
         return;
      }

      PotionEffect badOmen = player.getPotionEffect(PotionEffectType.BAD_OMEN);
      if (badOmen == null || !this.canTriggerRaid(player)) {
         return;
      }

      try {
         if (!NmsBridge.isPlayerInVillage(player)) {
            return;
         }

         Object raid = NmsBridge.getRaidAtPlayer(player);
         if (raid != null && NmsBridge.isRaidAtMaxOmen(raid)) {
            return;
         }

         player.removePotionEffect(PotionEffectType.BAD_OMEN);
         player.addPotionEffect(new PotionEffect(PotionEffectType.RAID_OMEN, 2, badOmen.getAmplifier(), false, true, true));
         NmsBridge.createOrExtendRaid(player);
         player.removePotionEffect(PotionEffectType.RAID_OMEN);
      } catch (ReflectiveOperationException ex) {
         this.warnNmsBridge(ex);
      }
   }

   private void triggerRaidFromRaidOmen(Player player) {
      if (!player.isOnline() || player.isDead() || player.getPotionEffect(PotionEffectType.RAID_OMEN) == null || !this.canTriggerRaid(player)) {
         return;
      }

      try {
         NmsBridge.createOrExtendRaid(player);
         NmsBridge.clearOmenTriggerCooldown(player);
         player.removePotionEffect(PotionEffectType.RAID_OMEN);
      } catch (ReflectiveOperationException ex) {
         this.warnNmsBridge(ex);
      }
   }

   private Location findOldRaidSpawnLocation(Raid raid, World world, boolean preCalculate) {
      Location center = raid.getLocation();
      if (center == null) {
         return null;
      }

      if (preCalculate) {
         int cooldownTicks = NmsBridge.getRaidCooldownTicks(raid);
         int proximity = cooldownTicks < 100 ? 1 : 0;
         for (int i = 0; i < 3; i++) {
            Location spawn = this.findRandomRaidSpawnLocation(raid, world, center, proximity, 1);
            if (spawn != null) {
               return spawn;
            }
         }
         return null;
      }

      for (int proximity = 0; proximity <= 3; proximity++) {
         Location spawn = this.findRandomRaidSpawnLocation(raid, world, center, proximity, RAID_SPAWN_TRIES);
         if (spawn != null) {
            return spawn;
         }
      }

      return null;
   }

   private Location findRandomRaidSpawnLocation(Raid raid, World world, Location center, int proximity, int tries) {
      int invertedProximity = proximity == 0 ? 2 : 2 - proximity;

      for (int i = 0; i < tries; i++) {
         double angle = this.nextRaidRandomDouble(raid, Math.PI * 2);
         int x = center.getBlockX() + (int)Math.floor(Math.cos(angle) * 32.0 * invertedProximity) + this.nextRaidRandomInt(raid, 5);
         int z = center.getBlockZ() + (int)Math.floor(Math.sin(angle) * 32.0 * invertedProximity) + this.nextRaidRandomInt(raid, 5);
         Location target = this.findSurfaceSpawnLocation(world, x, z);
         if (target != null && this.isOriginalRaidSpawnLocation(target, center, proximity)) {
            return target;
         }
      }

      return null;
   }

   private double nextRaidRandomDouble(Raid raid, double bound) {
      try {
         Float value = NmsBridge.nextRaidRandomFloat(raid);
         if (value != null) {
            return value * bound;
         }
      } catch (ReflectiveOperationException ex) {
         this.warnNmsBridge(ex);
      }
      return ThreadLocalRandom.current().nextDouble(bound);
   }

   private int nextRaidRandomInt(Raid raid, int bound) {
      try {
         Integer value = NmsBridge.nextRaidRandomInt(raid, bound);
         if (value != null) {
            return value;
         }
      } catch (ReflectiveOperationException ex) {
         this.warnNmsBridge(ex);
      }
      return ThreadLocalRandom.current().nextInt(bound);
   }

   private Location findSurfaceSpawnLocation(World world, int x, int z) {
      if (world == null || !world.isChunkLoaded(x >> 4, z >> 4)) {
         return null;
      }

      Integer vanillaY = NmsBridge.getWorldSurfaceY(world, x, z);
      if (vanillaY != null) {
         return new Location(world, x + 0.5, vanillaY, z + 0.5);
      }

      Block surface = world.getHighestBlockAt(x, z, HeightMap.WORLD_SURFACE);
      Block feet = surface.isPassable() && surface.getType() != Material.SNOW ? surface : surface.getRelative(BlockFace.UP);
      return feet.getLocation().add(0.5, 0.0, 0.5);
   }

   private boolean isOriginalRaidSpawnLocation(Location location, Location raidCenter, int proximity) {
      try {
         return NmsBridge.isOriginalRaidSpawnLocation(location, proximity);
      } catch (ReflectiveOperationException ex) {
         this.warnNmsBridge(ex);
         return this.isFallbackRaidSpawnLocation(location, raidCenter, proximity);
      }
   }

   private boolean isFallbackRaidSpawnLocation(Location location, Location raidCenter, int proximity) {
      boolean outsideApproximateVillage = location.distanceSquared(raidCenter) > 1024.0;
      if (!outsideApproximateVillage && proximity < 2) {
         return false;
      }

      Block feet = location.getBlock();
      Block head = feet.getRelative(BlockFace.UP);
      Block ground = feet.getRelative(BlockFace.DOWN);
      boolean hasRoom = feet.isPassable() && head.isPassable();
      boolean hasFullGround = ground.getType().isOccluding() && !ground.isPassable();
      return hasRoom && (hasFullGround || this.isSnowSpawnException(location));
   }

   private boolean isSnowSpawnException(Location location) {
      Block feet = location.getBlock();
      Block ground = feet.getRelative(BlockFace.DOWN);
      return ground.getType() == Material.SNOW && feet.isEmpty();
   }

   private boolean canTriggerRaid(Player player) {
      return player.getWorld().hasRaids()
         && player.getWorld().getDifficulty() != Difficulty.PEACEFUL
         && !Boolean.TRUE.equals(player.getWorld().getGameRuleValue(GameRule.DISABLE_RAIDS));
   }

   private boolean hasChangedBlock(Location from, Location to) {
      return to != null
         && (from.getBlockX() != to.getBlockX()
            || from.getBlockY() != to.getBlockY()
            || from.getBlockZ() != to.getBlockZ());
   }

   private void warnNmsBridge(Exception ex) {
      if (this.warnedNmsBridge) {
         return;
      }

      this.warnedNmsBridge = true;
      this.getLogger().log(Level.WARNING, "Could not access vanilla raid internals; falling back where possible.", ex);
   }

   private void reloadSettings() {
      this.keepOminousBottle = this.getConfig().getBoolean(KEEP_OMINOUS_BOTTLE_CONFIG, false);
   }

   private void sendConfigStatus(CommandSender sender) {
      sender.sendMessage("[OldRaids] " + KEEP_OMINOUS_BOTTLE_CONFIG + "=" + this.keepOminousBottle);
   }

   private Boolean parseBoolean(String value) {
      if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("on") || value.equals("1")) {
         return true;
      }
      if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no") || value.equalsIgnoreCase("off") || value.equals("0")) {
         return false;
      }
      return null;
   }

   private List<String> matching(String input, String... options) {
      String lower = input.toLowerCase(Locale.ROOT);
      List<String> matches = new ArrayList<>();
      for (String option : options) {
         if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
            matches.add(option);
         }
      }
      return matches;
   }

   private int clamp(int value, int min, int max) {
      return Math.max(min, Math.min(max, value));
   }

   private static final class NmsBridge {
      private static final Class<?> BLOCK_POS_CLASS = findClass("net.minecraft.core.BlockPos", "net.minecraft.core.BlockPosition");
      private static final Class<?> ENTITY_TYPE_CLASS = findClass("net.minecraft.world.entity.EntityType", "net.minecraft.world.entity.EntityTypes");
      private static final Class<?> HEIGHTMAP_TYPES_CLASS = findClass("net.minecraft.world.level.levelgen.Heightmap$Types", "net.minecraft.world.level.levelgen.HeightMap$Type");
      private static final Class<?> NMS_RAID_CLASS = findClass("net.minecraft.world.entity.raid.Raid");
      private static final Class<?> SPAWN_PLACEMENTS_CLASS = findClass("net.minecraft.world.entity.SpawnPlacements");
      private static final Class<?> SPAWN_PLACEMENT_TYPE_CLASS = findClass("net.minecraft.world.entity.SpawnPlacementType");
      private static final Constructor<?> BLOCK_POS_CONSTRUCTOR = findConstructor(BLOCK_POS_CLASS);
      private static final Field RAID_COOLDOWN_TICKS_FIELD = findDeclaredField(NMS_RAID_CLASS, "raidCooldownTicks");
      private static final Field RAID_RANDOM_FIELD = findDeclaredField(NMS_RAID_CLASS, "random");
      private static final Field WAVE_SPAWN_POS_FIELD = findDeclaredField(NMS_RAID_CLASS, "waveSpawnPos");
      private static final Object RAVAGER = findRavagerEntityType();
      private static final Object WORLD_SURFACE = findStaticFieldByNameOrText(HEIGHTMAP_TYPES_CLASS, "WORLD_SURFACE", "WORLD_SURFACE");
      private static final Object RAVAGER_SPAWN_PLACEMENT = findRavagerSpawnPlacement();

      private static boolean isPlayerInVillage(Player player) throws ReflectiveOperationException {
         Object serverPlayer = getHandle(player);
         Object serverLevel = playerLevel(serverPlayer);
         Object blockPos = playerBlockPosition(serverPlayer);
         return invokeBoolean(serverLevel, new String[]{"isVillage", "c"}, new Object[]{blockPos});
      }

      private static Object getRaidAtPlayer(Player player) throws ReflectiveOperationException {
         Object serverPlayer = getHandle(player);
         Object serverLevel = playerLevel(serverPlayer);
         Object blockPos = playerBlockPosition(serverPlayer);
         return invokeOptional(serverLevel, new String[]{"getRaidAt", "d"}, new Object[]{blockPos});
      }

      private static boolean isRaidAtMaxOmen(Object raid) throws ReflectiveOperationException {
         Integer level = invokeIntOptional(raid, new String[]{"getRaidOmenLevel", "m"});
         Integer max = invokeIntOptional(raid, new String[]{"getMaxRaidOmenLevel", "l"});
         return level != null && max != null && level >= max;
      }

      private static int getRaidCooldownTicks(Raid raid) {
         try {
            Object handle = getHandle(raid);
            if (RAID_COOLDOWN_TICKS_FIELD != null) {
               return RAID_COOLDOWN_TICKS_FIELD.getInt(handle);
            }
         } catch (ReflectiveOperationException ignored) {
         }
         return 300;
      }

      private static void setWaveSpawnPosition(Raid raid, Location location) throws ReflectiveOperationException {
         Object handle = getHandle(raid);
         if (WAVE_SPAWN_POS_FIELD == null) {
            throw new ReflectiveOperationException("Raid waveSpawnPos field is unavailable");
         }
         WAVE_SPAWN_POS_FIELD.set(handle, Optional.of(blockPos(location)));
      }

      private static Float nextRaidRandomFloat(Raid raid) throws ReflectiveOperationException {
         Object random = raidRandom(raid);
         Object result = invokeOptional(random, new String[]{"nextFloat", "i"});
         return result instanceof Float ? (Float)result : null;
      }

      private static Integer nextRaidRandomInt(Raid raid, int bound) throws ReflectiveOperationException {
         Object random = raidRandom(raid);
         return invokeIntOptional(random, new String[]{"nextInt", "a"}, new Object[]{bound});
      }

      private static Object raidRandom(Raid raid) throws ReflectiveOperationException {
         Object handle = getHandle(raid);
         if (RAID_RANDOM_FIELD == null) {
            throw new ReflectiveOperationException("Raid random field is unavailable");
         }
         return RAID_RANDOM_FIELD.get(handle);
      }

      private static Object createOrExtendRaid(Player player) throws ReflectiveOperationException {
         Object serverPlayer = getHandle(player);
         Object serverLevel = playerLevel(serverPlayer);
         Object raids = invokeOptional(serverLevel, new String[]{"getRaids", "B"});
         Object blockPos = playerBlockPosition(serverPlayer);
         if (raids == null) {
            throw new ReflectiveOperationException("Could not find raids manager");
         }
         Object raid = invokeOptional(raids, new String[]{"createOrExtendRaid", "a"}, new Object[]{serverPlayer, blockPos});
         if (raid == null) {
            throw new ReflectiveOperationException("Could not create or extend raid");
         }
         return raid;
      }

      private static void clearOmenTriggerCooldown(Player player) throws ReflectiveOperationException {
         Object serverPlayer = getHandle(player);
         invokeOptional(serverPlayer, new String[]{"clearRaidOmenPosition", "af"});
      }

      private static boolean isOriginalRaidSpawnLocation(Location location, int proximity) throws ReflectiveOperationException {
         if (isVillage(location) && proximity < 2) {
            return false;
         }
         if (!hasChunksAt(location, 10)) {
            return false;
         }
         if (!isPositionEntityTicking(location)) {
            return false;
         }
         return isRavagerSpawnPositionOk(location) || isSnowSpawnExceptionVanilla(location);
      }

      private static Integer getWorldSurfaceY(World world, int x, int z) {
         try {
            Object serverLevel = getHandle(world);
            return invokeIntOptional(serverLevel, new String[]{"getHeight", "a"}, new Object[]{WORLD_SURFACE, x, z});
         } catch (ReflectiveOperationException ex) {
            return null;
         }
      }

      private static boolean isVillage(Location location) throws ReflectiveOperationException {
         Object serverLevel = getHandle(location.getWorld());
         Object blockPos = blockPos(location);
         return invokeBoolean(serverLevel, new String[]{"isVillage", "c"}, new Object[]{blockPos});
      }

      private static boolean hasChunksAt(Location location, int offset) throws ReflectiveOperationException {
         Object serverLevel = getHandle(location.getWorld());
         int x = location.getBlockX();
         int z = location.getBlockZ();
         Boolean result = invokeBooleanOptional(serverLevel, new String[]{"hasChunksAt"}, new Object[]{x - offset, z - offset, x + offset, z + offset});
         return result == null || result;
      }

      private static boolean isPositionEntityTicking(Location location) throws ReflectiveOperationException {
         Object serverLevel = getHandle(location.getWorld());
         Object blockPos = blockPos(location);
         Boolean result = invokeBooleanOptional(serverLevel, new String[]{"isPositionEntityTicking"}, new Object[]{blockPos});
         return result == null || result;
      }

      private static boolean isRavagerSpawnPositionOk(Location location) throws ReflectiveOperationException {
         if (RAVAGER == null) {
            throw new ReflectiveOperationException("Ravager entity type is unavailable");
         }

         Object serverLevel = getHandle(location.getWorld());
         Object blockPos = blockPos(location);

         Boolean staticResult = invokeStaticBooleanOptional(
            SPAWN_PLACEMENTS_CLASS,
            new String[]{"isSpawnPositionOk"},
            new Object[]{RAVAGER, serverLevel, blockPos}
         );
         if (staticResult != null) {
            return staticResult;
         }

         if (RAVAGER_SPAWN_PLACEMENT != null) {
            Boolean placementResult = invokeBooleanOnTypeOptional(
               SPAWN_PLACEMENT_TYPE_CLASS,
               RAVAGER_SPAWN_PLACEMENT,
               new String[]{"isSpawnPositionOk"},
               new Object[]{serverLevel, blockPos, RAVAGER}
            );
            if (placementResult != null) {
               return placementResult;
            }
         }

         throw new ReflectiveOperationException("Ravager spawn placement method is unavailable");
      }

      private static boolean isSnowSpawnExceptionVanilla(Location location) {
         Block feet = location.getBlock();
         Block ground = feet.getRelative(BlockFace.DOWN);
         return ground.getType() == Material.SNOW && feet.isEmpty();
      }

      private static Object playerLevel(Object serverPlayer) throws ReflectiveOperationException {
         Object level = invokeOptional(serverPlayer, new String[]{"level", "y"});
         if (level == null) {
            throw new ReflectiveOperationException("Could not find player level");
         }
         return level;
      }

      private static Object playerBlockPosition(Object serverPlayer) throws ReflectiveOperationException {
         Object blockPos = invokeOptional(serverPlayer, new String[]{"blockPosition", "dv"});
         if (blockPos == null) {
            throw new ReflectiveOperationException("Could not find player block position");
         }
         return blockPos;
      }

      private static Object blockPos(Location location) throws ReflectiveOperationException {
         return blockPos(location.getBlockX(), location.getBlockY(), location.getBlockZ());
      }

      private static Object blockPos(int x, int y, int z) throws ReflectiveOperationException {
         if (BLOCK_POS_CONSTRUCTOR == null) {
            throw new ReflectiveOperationException("BlockPos constructor is unavailable");
         }
         return BLOCK_POS_CONSTRUCTOR.newInstance(x, y, z);
      }

      private static Object getHandle(Object object) throws ReflectiveOperationException {
         if (object == null) {
            throw new ReflectiveOperationException("Cannot get handle for null object");
         }
         Object handle = invokeOptional(object, new String[]{"getHandle"});
         if (handle == null) {
            throw new ReflectiveOperationException("Cannot get handle for " + object.getClass().getName());
         }
         return handle;
      }

      private static boolean invokeBoolean(Object target, String[] names, Object[] args) throws ReflectiveOperationException {
         Boolean result = invokeBooleanOptional(target, names, args);
         if (result == null) {
            throw new ReflectiveOperationException("Could not invoke boolean method on " + target.getClass().getName());
         }
         return result;
      }

      private static Boolean invokeBooleanOptional(Object target, String[] names, Object[] args) throws ReflectiveOperationException {
         Object result = invokeOptional(target, names, args);
         return result instanceof Boolean ? (Boolean)result : null;
      }

      private static Boolean invokeStaticBooleanOptional(Class<?> type, String[] names, Object[] args) throws ReflectiveOperationException {
         Object result = invokeStaticOptional(type, names, args);
         return result instanceof Boolean ? (Boolean)result : null;
      }

      private static Boolean invokeBooleanOnTypeOptional(Class<?> type, Object target, String[] names, Object[] args) throws ReflectiveOperationException {
         Object result = invokeOnTypeOptional(type, target, names, args);
         return result instanceof Boolean ? (Boolean)result : null;
      }

      private static Integer invokeIntOptional(Object target, String[] names) throws ReflectiveOperationException {
         return invokeIntOptional(target, names, new Object[0]);
      }

      private static Integer invokeIntOptional(Object target, String[] names, Object[] args) throws ReflectiveOperationException {
         Object result = invokeOptional(target, names, args);
         return result instanceof Integer ? (Integer)result : null;
      }

      private static Object invokeOptional(Object target, String[] names, Object... args) throws ReflectiveOperationException {
         Object[] actualArgs = args;
         if (args.length == 1 && args[0] instanceof Object[] nested) {
            actualArgs = nested;
         }
         for (String name : names) {
            Method method = findCompatibleMethod(target.getClass(), name, actualArgs);
            if (method != null) {
               return method.invoke(target, actualArgs);
            }
         }
         return null;
      }

      private static Object invokeStaticOptional(Class<?> type, String[] names, Object... args) throws ReflectiveOperationException {
         Object[] actualArgs = args;
         if (args.length == 1 && args[0] instanceof Object[] nested) {
            actualArgs = nested;
         }
         if (type == null) {
            return null;
         }
         for (String name : names) {
            Method method = findCompatibleMethod(type, name, actualArgs);
            if (method != null && Modifier.isStatic(method.getModifiers())) {
               return method.invoke(null, actualArgs);
            }
         }
         return null;
      }

      private static Object invokeOnTypeOptional(Class<?> type, Object target, String[] names, Object... args) throws ReflectiveOperationException {
         Object[] actualArgs = args;
         if (args.length == 1 && args[0] instanceof Object[] nested) {
            actualArgs = nested;
         }
         if (type == null || target == null) {
            return null;
         }
         for (String name : names) {
            Method method = findCompatibleMethod(type, name, actualArgs);
            if (method != null) {
               return method.invoke(target, actualArgs);
            }
         }
         return null;
      }

      private static Method findCompatibleMethod(Class<?> type, String name, Object[] args) {
         for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) {
               continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < parameterTypes.length; i++) {
               if (!isCompatible(parameterTypes[i], args[i])) {
                  compatible = false;
                  break;
               }
            }
            if (compatible) {
               return method;
            }
         }
         return null;
      }

      private static boolean isCompatible(Class<?> parameterType, Object value) {
         if (value == null) {
            return !parameterType.isPrimitive();
         }
         if (parameterType.isPrimitive()) {
            return (parameterType == int.class && value instanceof Integer)
               || (parameterType == boolean.class && value instanceof Boolean)
               || (parameterType == long.class && value instanceof Long)
               || (parameterType == double.class && value instanceof Double)
               || (parameterType == float.class && value instanceof Float);
         }
         return parameterType.isInstance(value);
      }

      private static Class<?> findClass(String... names) {
         for (String name : names) {
            try {
               return Class.forName(name);
            } catch (ClassNotFoundException ignored) {
            }
         }
         return null;
      }

      private static Constructor<?> findConstructor(Class<?> type) {
         if (type == null) {
            return null;
         }
         try {
            return type.getConstructor(int.class, int.class, int.class);
         } catch (NoSuchMethodException ex) {
            return null;
         }
      }

      private static Field findDeclaredField(Class<?> type, String name) {
         if (type == null) {
            return null;
         }
         try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
         } catch (NoSuchFieldException ex) {
            return null;
         }
      }

      private static Object findStaticFieldByNameOrText(Class<?> type, String name, String text) {
         if (type == null) {
            return null;
         }
         Object named = findStaticField(type, name);
         if (named != null) {
            return named;
         }
         for (Field field : type.getFields()) {
            if (Modifier.isStatic(field.getModifiers())) {
               try {
                  Object value = field.get(null);
                  if (value != null && String.valueOf(value).contains(text)) {
                     return value;
                  }
               } catch (ReflectiveOperationException ignored) {
               }
            }
         }
         return null;
      }

      private static Object findStaticField(Class<?> type, String name) {
         if (type == null) {
            return null;
         }
         try {
            Field field = type.getField(name);
            return field.get(null);
         } catch (ReflectiveOperationException ex) {
            return null;
         }
      }

      private static Object findRavagerEntityType() {
         Object named = findStaticField(ENTITY_TYPE_CLASS, "RAVAGER");
         if (named != null) {
            return named;
         }
         if (ENTITY_TYPE_CLASS == null) {
            return null;
         }
         for (Field field : ENTITY_TYPE_CLASS.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) {
               continue;
            }
            try {
               Object value = field.get(null);
               if (value != null && String.valueOf(value).toLowerCase().contains("ravager")) {
                  return value;
               }
            } catch (ReflectiveOperationException ignored) {
            }
         }
         return null;
      }

      private static Object findRavagerSpawnPlacement() {
         if (SPAWN_PLACEMENTS_CLASS == null || RAVAGER == null) {
            return null;
         }
         try {
            Object named = invokeStaticOptional(SPAWN_PLACEMENTS_CLASS, new String[]{"getPlacementType"}, new Object[]{RAVAGER});
            if (named != null) {
               return named;
            }
         } catch (ReflectiveOperationException ignored) {
         }
         for (Method method : SPAWN_PLACEMENTS_CLASS.getMethods()) {
            if (Modifier.isStatic(method.getModifiers())
               && method.getParameterCount() == 1
               && method.getParameterTypes()[0].isInstance(RAVAGER)
               && method.getReturnType() != boolean.class) {
               try {
                  Object value = method.invoke(null, RAVAGER);
                  if (value != null) {
                     return value;
                  }
               } catch (ReflectiveOperationException ignored) {
               }
            }
         }
         return null;
      }
   }
}
