# OldRaids

**This fork targets Minecraft/Spigot 1.21.4.**
**In 1.21.10 Leaves can be loaded normally, but not tested.**

A lightweight spigot plugin that reverts raid behavior to before 1.21, allowing old raid farms to work the same as before.

### Configuration

```
# allow bStats metrics
metrics: true

# should raid captains still drop ominous bottles (on top of giving bad omen)
raidersDropOminousBottles: false

# duration of the Bad Omen effect given by raid captains, in ticks
badOmenDurationTicks: 120000

# move raid waves back to the pre-1.21.3 spawn-location behavior
restoreOldRaidSpawnLocations: true
```

### Commands

```
/oldraids reload
  - Reload the plugin configuration
```
